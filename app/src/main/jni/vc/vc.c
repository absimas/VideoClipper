// FFmpeg includes
#include <ffmpeg.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libavutil/pixfmt.h>

// Android includes
#include <android/log.h>
#include <android/bitmap.h>
// Standard includes
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <stdbool.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <time.h>
#include <wchar.h>
#include <jni.h>
#include <dlfcn.h>
#include <fcntl.h>

/* Notes
 * - Every process forked inside JNI seems to be a part of parent's process group. Therefore when
 * the parent dies, children do too.
 */

/* Config */
// Log tag
#define TAG "VC.c"
// Convenience log functions definitions
#ifdef NDEBUG
    #define LOGV(...) __android_log_print(2, TAG, __VA_ARGS__);
    #define LOGI(...) __android_log_print(4, TAG, __VA_ARGS__);
#else
    // Do nothing on release builds
    #define LOGV(...) do { } while(0)
    #define LOGI(...) do { } while(0)
#endif
#define LOGE(...) __android_log_print(6, TAG, __VA_ARGS__);

// Time between checks if the child process has been closed
static const int FFMPEG_WAIT_INTERVAL = 1000;
static const int FFPROBE_WAIT_INTERVAL = 300;
static const int MAX_LOG_UNMODIFYING_ITERATIONS = 10;
static const char* mFfmpegActivityPath = "com/simas/vc/background_tasks/Ffmpeg";
static const char* mFfprobeActivityPath = "com/simas/vc/background_tasks/Ffprobe";

////////////////////////////////////////////////////////////////////////////////////////////////////

// Custom types
typedef struct CArray {
   const char **arr;
   jstring *jstrings;
   int size;
} CArray;

// Method prototypes
jint cFfmpeg(JNIEnv *env, jobject obj, jobjectArray args);
jint cFfprobe(JNIEnv *env, jobject obj, jobjectArray args, jstring output);
jobject createPreview(JNIEnv *pEnv, jobject pObj, jstring videoPath);
// Helper method prototypes
void sleep_ms(int milliseconds);
time_t get_mtime(const char *path);
CArray convertToCArray(JNIEnv *env, jobjectArray args);
jobject createBitmap(JNIEnv *pEnv, int pWidth, int pHeight);

// Method implementations
jint cFfmpeg(JNIEnv *env, jobject obj, jobjectArray args) {
#define TAG "cFfmpeg"
	// Fork process
    pid_t childID = fork();

    switch (childID) {
        case -1:
            LOGE("Fork error");
            return EXIT_FAILURE;
        case 0:
            LOGI("Child process started...");
            CArray cArray = convertToCArray(env, args);

        	// Start FFmpeg
            int result = ffmpeg(cArray.size, cArray.arr);

		    freeCArray(cArray);

            exit(result);
        default:
            LOGI("Parent listening to child: %d", childID);
            // Check if child has finished every FFMPEG_WAIT_INTERVAL ms
            int status;
            for(;;) {
                pid_t endID = waitpid(childID, &status, WNOHANG|WUNTRACED);

                switch (endID) {
                    case -1:
                        LOGE("waitpid error!");
                        return EXIT_FAILURE;
                    case 0:
                        LOGI("Parent waiting for child...");
                        sleep_ms(FFMPEG_WAIT_INTERVAL);
                        break;
                    default:
                        if (endID == childID) {
                            if (WIFEXITED(status)) {
                                LOGI("Child ended normally.");
                                // If return code != 0, child has failed.
                                if (status) {
                                    LOGE("However the code returned was: %d", status);
                                }
                                return status;
                            } else if (WIFSIGNALED(status)) {
                                LOGE("Child ended because of an uncaught signal.");
                            } else if (WIFSTOPPED(status)) {
                                LOGI("Child process has stopped.");
                            } else {
                              return status;
                            }
                        }
                }
            }
    }
}

jint cFfprobe(JNIEnv *env, jobject obj, jobjectArray args, jstring jLogPath) {
#define TAG "cFfprobe"
	const char *logPath = (*env)->GetStringUTFChars(env, jLogPath, 0);
	int status;

    // Fork process
    pid_t childID = fork();

    switch (childID) {
        case -1:
            LOGE("fork error");
            status = EXIT_FAILURE;
            break;
        case 0:
            LOGI("Child process started...");
		    CArray cArray = convertToCArray(env, args);
		    // Open output file
		    FILE *logFile;
		    if (access(logPath, F_OK) != -1) {
		        if ((logFile = fopen(logPath, "w")) == NULL) {
		            LOGE("Couldn't open the file! %s", logPath);
		            exit(EXIT_FAILURE);
		        }
		    } else {
		        LOGE("File not found!");
		        exit(EXIT_FAILURE);
		    }

		    // Redirect stdout to a file
		    if (dup2(fileno(logFile), fileno(stdout)) == -1) {
		        LOGE("Error redirecting stdout");
		        exit(EXIT_FAILURE);
		    }
		    // File descriptor duplicated, can now close the file
		    fclose(logFile);

		    // Start FFprobe
		    int result = ffprobe(cArray.size, cArray.arr);

		    // Flush FFprobe output
			fflush(stdout);

		    // Free log path string
		    (*env)->ReleaseStringUTFChars(env, jLogPath, logPath);
		    freeCArray(cArray);

			// Exit the child process and return the FFprobe result code
			exit(result);
        default:
            LOGI("Parent listening to child: %d", childID);
            int unmodifiedIterations = 0;
            long logModificationTime = 0;
            int loop = true;
            while (loop) {
                // Check if child has finished every FFPROBE_WAIT_INTERVAL ms
                pid_t endID = waitpid(childID, &status, WNOHANG|WUNTRACED);

                switch (endID) {
                    case -1:
                        LOGE("waitpid error!");
                        status = EXIT_FAILURE;
                        loop = false;
                        break;
	                case 0:
                        // Check the log modification time
                        if (logModificationTime != 0 && logModificationTime == get_mtime(logPath)) {
                            ++unmodifiedIterations;
                            LOGI("Log file unmodified for %d iteration(s)!", unmodifiedIterations);
                        } else {
                            unmodifiedIterations = 0;
                        }

                        if (unmodifiedIterations >= MAX_LOG_UNMODIFYING_ITERATIONS) {
                            LOGE("Log file hasn't been modified for %d millis! Quitting.",
                                    unmodifiedIterations * FFPROBE_WAIT_INTERVAL);
                            status = EXIT_FAILURE;
                            loop = false;
                        } else {
                            // Update time variable
	                        logModificationTime = get_mtime(logPath);

	                        LOGV("Parent waiting for child...");
	                        sleep_ms(FFPROBE_WAIT_INTERVAL);
                        }
                        break;
                    default:
                        if (endID == childID) {
                            if (WIFEXITED(status)) {
                                LOGI("Child ended normally.");
                                if (status) {
                                    LOGE("However the return code is: %d", status);
                                    status = EXIT_FAILURE;
                                }
                            } else {
	                            if (WIFSIGNALED(status)) {
	                                LOGE("Child ended because of an uncaught signal.n");
	                            } else if (WIFSTOPPED(status)) {
	                                LOGI("Child process has stopped.");
	                            }
	                            status = EXIT_FAILURE;
                            }
                            loop = false;
                        }
                }
            }
    }

    // Free log path string
    (*env)->ReleaseStringUTFChars(env, jLogPath, logPath);
}

jobject createBitmap(JNIEnv *pEnv, int pWidth, int pHeight) {
	// Fetch the Bitmap class and createBitmap method
	jclass bitmapClass = (jclass)(*pEnv)->FindClass(pEnv, "android/graphics/Bitmap");
	jmethodID methodId = (*pEnv)->GetStaticMethodID(pEnv, bitmapClass, "createBitmap",
			"(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

	// Create Bitmap.Config // Reference: https://forums.oracle.com/thread/1548728
	const wchar_t* configName = L"ARGB_8888";
	int len = wcslen(configName);
	jstring jConfigName;

	if (sizeof(wchar_t) != sizeof(jchar)) {
		// wchar_t is defined as different length than jchar (2 bytes)
		jchar* str = (jchar*) malloc((len+1) * sizeof(jchar));
		for (int i = 0; i<len; ++i) {
			str[i] = (jchar) configName[i];
		}
		str[len] = 0;
		jConfigName = (*pEnv)->NewString(pEnv, (const jchar*) str, len);
	} else {
		// wchar_t is defined same length as jchar (2 bytes)
		jConfigName = (*pEnv)->NewString(pEnv, (const jchar*) configName, len);
	}

	// Create a Bitmap
	jclass bitmapConfigClass = (*pEnv)->FindClass(pEnv, "android/graphics/Bitmap$Config");
	jobject javaBitmapConfig = (*pEnv)->CallStaticObjectMethod(pEnv, bitmapConfigClass,
			(*pEnv)->GetStaticMethodID(pEnv, bitmapConfigClass, "valueOf",
			"(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;"), jConfigName);
	return (*pEnv)->CallStaticObjectMethod(pEnv, bitmapClass, methodId, pWidth, pHeight, javaBitmapConfig);
}

jobject createPreview(JNIEnv *pEnv, jobject pObj, jstring videoPath) {
	AVFormatContext     *formatCtx = NULL;
	AVCodecContext      *codecCtx = NULL;
	AVCodec             *codec = NULL;
	AVFrame             *frame = NULL;
	AVFrame             *frameRGBA = NULL;
	AVDictionary        *options = NULL;
    struct SwsContext   *swsCtx = NULL;
	AVPacket            packet;
	int i, videoStream, frameFinished;
	char *videoFilePath;
	void *buffer;
	jobject bitmap;

	// Register all formats and codecs
	av_register_all();

	// Convert String to char*
	videoFilePath = (char *)(*pEnv)->GetStringUTFChars(pEnv, videoPath, NULL);

	// Open video file
	if (avformat_open_input(&formatCtx, videoFilePath, NULL, NULL) != 0) {
		LOGE("Couldn't open %s!", videoPath);
		return NULL;
	}


	// Retrieve stream information
	if (avformat_find_stream_info(formatCtx, NULL) < 0) {
		LOGE("Couldn't find stream info!");
		return NULL;
	}

	// Dump information about file onto standard error
	av_dump_format(formatCtx, 0, videoFilePath, 0);

	// Find the first video stream
	videoStream = -1;
	for (i=0; i<formatCtx->nb_streams; i++) {
		if (formatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
			videoStream = i;
			break;
		}
	}
	if (videoStream == -1) {
		LOGE("Couldn't find video stream!");
		return NULL;
	}

	// Get a pointer to the codec context for the video stream
	codecCtx = formatCtx->streams[videoStream]->codec;

	// Find the decoder for the video stream
	codec = avcodec_find_decoder(codecCtx->codec_id);
	if (codec == NULL) {
		LOGE("Unsupported codec!");
		return NULL;
	}
	// Open codec
	if (avcodec_open2(codecCtx, codec, &options) < 0) {
		LOGE("Couldn't open codec!");
		return NULL;
	}

	// Allocate video frame
	frame = avcodec_alloc_frame();

	// Allocate an AVFrame structure
	frameRGBA = avcodec_alloc_frame();
	if (frameRGBA == NULL)
		return NULL;

	// Create a bitmap as the buffer for frameRGBA
	bitmap = createBitmap(pEnv, codecCtx->width, codecCtx->height);
	if (AndroidBitmap_lockPixels(pEnv, bitmap, &buffer) < 0) {
		LOGE("Bitmap buffer creation failed!");
		return NULL;
	}

	// Get the scaling context
	swsCtx = sws_getContext
    (
        codecCtx->width,
        codecCtx->height,
        codecCtx->pix_fmt,
        codecCtx->width,
        codecCtx->height,
        AV_PIX_FMT_RGBA,
        SWS_BILINEAR,
        NULL,
        NULL,
        NULL
    );

	// Assign appropriate parts of bitmap to image planes in frameRGBA
	// Note that frameRGBA is an AVFrame, but AVFrame is a superset
	// of AVPicture
	avpicture_fill((AVPicture *)frameRGBA, buffer, AV_PIX_FMT_RGBA,
			codecCtx->width, codecCtx->height);

	// ToDo seek parameter ?
		// int av_seek_frame(AVFormatContext *s, int stream_index, int64_t timestamp, int flags);

	// Read frames and save first five frames to disk
	i = 0;
	while (av_read_frame(formatCtx, &packet) >= 0) {
		// Is this a packet from the video stream?
		if (packet.stream_index == videoStream) {
			// Decode video frame
			avcodec_decode_video2(codecCtx, frame, &frameFinished, &packet);

			// Did we get a video frame?
			if (frameFinished) {
				// Convert the image from its native format to RGBA
				sws_scale
				(
					swsCtx,
					(uint8_t const * const *)frame->data,
					frame->linesize,
					0,
					codecCtx->height,
					frameRGBA->data,
					frameRGBA->linesize
				);
			}

			if (++i == 5) {
				break;
			}
		}
		// Free the packet that was allocated by av_read_frame
		av_free_packet(&packet);
	}

	// Free the RGB image
	av_free(frameRGBA);

	// Free the YUV frame
	av_free(frame);

	// Close the codec
	avcodec_close(codecCtx);

	// Close the video file
	avformat_close_input(&formatCtx);

	return bitmap;
}

// Converts Java's String[] to C's char**
CArray convertToCArray(JNIEnv *env, jobjectArray args) {
    CArray cArray;
	cArray.size = (*env)->GetArrayLength(env, args);
	cArray.jstrings = (jstring *) malloc(sizeof(jstring) * cArray.size);
	cArray.arr = (const char **) malloc(sizeof(const char *) * cArray.size);

	for(int i=0; i<cArray.size; ++i) {
		cArray.jstrings[i] = (jstring)(*env)->GetObjectArrayElement(env, args, i);
		cArray.arr[i] = (const char *)(*env)->GetStringUTFChars(env, cArray.jstrings[i], false);
	}

	return cArray;
}

void freeCArray(JNIEnv *env, CArray cArray) {
	for(int i=0; i<cArray.size; ++i) {
		// Free char*
		(*env)->ReleaseStringUTFChars(env, cArray.jstrings[i], cArray.arr[i]);

		// Free jstring
		free(cArray.jstrings[i]);
	}
	// Free char**
    free(cArray.arr);

	// Free jstring[]
	free(cArray.jstrings);
}

void sleep_ms(int milliseconds) {
#if _POSIX_C_SOURCE >= 199309L
    struct timespec ts = {0, milliseconds * 1000000};
    nanosleep(&ts, NULL);
#else
    usleep(milliseconds * 1000);
#endif
}

time_t get_mtime(const char *path) {
    struct stat statbuf;
    if (stat(path, &statbuf) == -1) {
        return 0;
    }
    return statbuf.st_mtime;
}

// JNI Initialization
static JNINativeMethod ffprobeMethodTable[] = {
	{"cFfprobe", "([Ljava/lang/String;Ljava/lang/String;)I", (void *) cFfprobe}
};
static JNINativeMethod ffmpegMethodTable[] = {
	{"cFfmpeg", "([Ljava/lang/String;)I", (void *) cFfmpeg},
	{"createPreview", "(Ljava/lang/String;)Landroid/graphics/Bitmap;", (void *)createPreview}
};

jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
#ifdef NDEBUG
    LOGE("NDEBUG");
#else
    LOGE("!NDEBUG");
#endif
	LOGV("JNI_OnLoad");
	JNIEnv *env;
	if ((*jvm)->GetEnv(jvm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		LOGE("Failed to get the environment");
		return -1;
	}

    // Register ffmpeg method(s)
    jclass activityClass = (*env)->FindClass(env, mFfmpegActivityPath);
    if (!activityClass) {
        LOGE("Failed to get %s class reference", mFfmpegActivityPath);
        return -1;
    }
	(*env)->RegisterNatives(env, activityClass, ffmpegMethodTable,
			sizeof(ffmpegMethodTable) / sizeof (ffmpegMethodTable[0]));

	// Register ffprobe method(s)
    activityClass = (*env)->FindClass(env, mFfprobeActivityPath);
    if (!activityClass) {
        LOGE("Failed to get %s class reference", mFfprobeActivityPath);
        return -1;
    }
    (*env)->RegisterNatives(env, activityClass, ffprobeMethodTable,
            sizeof(ffprobeMethodTable) / sizeof (ffprobeMethodTable[0]));

	return JNI_VERSION_1_6;
}
