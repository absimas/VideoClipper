/*
 * Copyright (c) 2015. Simas Abramovas
 *
 * This file is part of VideoClipper.
 *
 * VideoClipper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VideoClipper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VideoClipper. If not, see <http://www.gnu.org/licenses/>.
 */

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
// Convenience log functions
#ifdef NDEBUG
    #define LOGV(...) __android_log_print(2, TAG, __VA_ARGS__);
    #define LOGI(...) __android_log_print(4, TAG, __VA_ARGS__);
    #define LOGW(...) __android_log_print(5, TAG, __VA_ARGS__);
#else
    // Do nothing on release builds
    #define LOGV(...) do { } while(0)
    #define LOGI(...) do { } while(0)
    #define LOGW(...) do { } while(0)
#endif
#define LOGE(...) __android_log_print(6, TAG, __VA_ARGS__);

/* Time between checks if the child process has been closed */
static const int CFFMPEG_WAIT_INTERVAL = 1000;
static const int CFFPROBE_WAIT_INTERVAL = 300;
// Fruitless (unmodified log file) iterations cFfprobe will endure before quitting
static const int MAX_LOG_UNMODIFYING_ITERATIONS = 10;
// Times cFfprobe will retry when log isn't modified for MAX_LOG_UNMODIFYING_ITERATIONS
static const int CFFPROBE_RETRY_CALLS = 2;
/* Min and Max luminance values the average preview pixel can have (0-255) */
static const int MIN_PREVIEW_LUMINANCE = 20;
static const int MAX_PREVIEW_LUMINANCE = 235;
// cFfprobe retry counter
int cFfprobeRetries = 0;
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
int isFrameUnderExposed(AVFrame *frame);
void sleep_ms(int milliseconds);
time_t get_mtime(const char *path);
CArray convertToCArray(JNIEnv *env, jobjectArray args);
void freeCArray(JNIEnv *env, CArray *cArray);
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

		    freeCArray(env, &cArray);

            exit(result);
        default:
            LOGI("Parent listening to child: %d", childID);
            // Check if child has finished every CFFMPEG_WAIT_INTERVAL ms
            int status;
            for(;;) {
                pid_t endID = waitpid(childID, &status, WNOHANG|WUNTRACED);

                switch (endID) {
                    case -1:
                        LOGE("waitpid error!");
                        return EXIT_FAILURE;
                    case 0:
                        LOGI("Parent waiting for child...");
                        sleep_ms(CFFMPEG_WAIT_INTERVAL);
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

    // Fork process
    pid_t childID = fork();

    switch (childID) {
        case -1:
            LOGE("Fork error");
            return EXIT_FAILURE;
        case 0:
            LOGI("Child process started...");
		    CArray cArray = convertToCArray(env, args);
		    // Open output file
		    FILE *logFile;
		    if (access(logPath, F_OK) != -1) {
		        if ((logFile = fopen(logPath, "w")) == NULL) {
		            LOGE("Error: Couldn't open the log file! %s", logPath);
		            exit(EXIT_FAILURE);
		        }
		    } else {
		        LOGE("Error: Log file not found! %s", logPath);
		        exit(EXIT_FAILURE);
		    }

		    // Redirect stdout to a file
		    if (dup2(fileno(logFile), fileno(stdout)) == -1) {
		        LOGE("Error: Couldn't redirect stdout!");
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
		    freeCArray(env, &cArray);

			// Exit the child process and return the FFprobe result code
			exit(result);
        default:
            LOGI("Parent listening to child: %d", childID);
            int unmodifiedIterations = 0, status;
            long logModificationTime = 0;
            for (;;) {
                // Check if child has finished every CFFPROBE_WAIT_INTERVAL ms
                pid_t endID = waitpid(childID, &status, WNOHANG|WUNTRACED);

                switch (endID) {
                    case -1:
                        LOGE("waitpid error!");
                        // Free log path string
                        (*env)->ReleaseStringUTFChars(env, jLogPath, logPath);
                        return EXIT_FAILURE;
	                case 0:
                        // Check the log modification time
                        if (logModificationTime != 0 && logModificationTime == get_mtime(logPath)) {
                            ++unmodifiedIterations;
                            LOGI("Log file unmodified for %d iteration(s)!", unmodifiedIterations);
                        } else {
                            unmodifiedIterations = 0;
                        }

                        if (unmodifiedIterations >= MAX_LOG_UNMODIFYING_ITERATIONS) {
                            LOGW("Log file hasn't been modified for %d millis!",
                                    unmodifiedIterations * CFFPROBE_WAIT_INTERVAL);
                            // Free log path string
                            (*env)->ReleaseStringUTFChars(env, jLogPath, logPath);

                            int result;
                            // Retry CFFPROBE_RETRY_CALLS times
                            if (++cFfprobeRetries < CFFPROBE_RETRY_CALLS) {
                                LOGW("Retrying... %d retries out of %d",
                                        cFfprobeRetries, CFFPROBE_RETRY_CALLS);
                                // Invoke cFfprobe again if haven't yet reached the limit
                                result = cFfprobe(env, obj, args, jLogPath);
                            } else {
                                LOGE("Already retried %d times... exiting!", CFFPROBE_RETRY_CALLS);
                                result = EXIT_FAILURE;
                            }

                            // Reset retry counter
                            cFfprobeRetries = 0;
                            return result;
                        } else {
                            // Update time variable
	                        logModificationTime = get_mtime(logPath);

	                        LOGV("Parent waiting for child...");
	                        sleep_ms(CFFPROBE_WAIT_INTERVAL);
                        }
                        break;
                    default:
                        if (endID == childID) {
                            if (WIFEXITED(status)) {
                                LOGI("Child ended normally.");
                                if (status) {
                                    LOGE("However the return code is: %d", status);
                                    // Free log path string
                                    (*env)->ReleaseStringUTFChars(env, jLogPath, logPath);
                                    return EXIT_FAILURE;
                                } else {
                                    return status;
                                }
                            } else {
	                            if (WIFSIGNALED(status)) {
	                                LOGE("Child ended because of an uncaught signal.");
	                            } else if (WIFSTOPPED(status)) {
	                                LOGI("Child process has stopped.");
	                            }
                                // Free log path string
                                (*env)->ReleaseStringUTFChars(env, jLogPath, logPath);
                                return EXIT_FAILURE;
                            }
                        }
                }
            }
    }
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

	for (int i =0; i<n; i++) {
		String output = "";
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

	// Seek functions
		//int av_seek_frame(AVFormatContext *s, int stream_index, int64_t timestamp, int flags);
		//int avformat_seek_file (AVFormatContext *s, int stream_index, int64_t min_ts, int64_t ts, int64_t max_ts, int flags)

	int underExposedRetries = 0, findKeyPacket = 0;
	while (av_read_frame(formatCtx, &packet) >= 0) {
		// Is this a packet from the video stream?
		if (packet.stream_index == videoStream) {
			// Check whether this packet contains a key frame
			if (findKeyPacket && !(packet.flags & AV_PKT_FLAG_KEY)) {
	        	av_free_packet(&packet);
	            continue;
			}

			// Decode video frame
			avcodec_decode_video2(codecCtx, frame, &frameFinished, &packet);

			// Check whether frame successfully decoded
			if (frameFinished) {
				if (isFrameUnderExposed(frame)) {
					switch (++underExposedRetries) {
						case 1:
							seekTo(formatCtx, 0.25);
							break;
						case 2:
							seekTo(formatCtx, 0.50);
							break;
						default:
							LOGI("Looking for key frame packets. Retry %d.", underExposedRetries);
							findKeyPacket = 1;
							break;
					}
					avcodec_flush_buffers(codecCtx);
					av_free_packet(&packet);
					continue;
				}

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
                av_free_packet(&packet);
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

// Check if frame is too dark or too bright
int isFrameUnderExposed(AVFrame *frame) {
	// Count average pixel luma (brightness)
	unsigned long long totalLuma = 0;
	for (int x=1; x<=frame->width; ++x) {
		for (int y=1; y<=frame->height; ++y) {
			totalLuma += frame->data[0][frame->linesize[0]*y + x];
        }
	}
	unsigned char averageLuma = totalLuma / (frame->width * frame->height);

	LOGI("Average frame luma is: %d", averageLuma);

	if (averageLuma <= MIN_PREVIEW_LUMINANCE || averageLuma >= MAX_PREVIEW_LUMINANCE) {
		return 1;
	} else {
		return 0;
	}
}

// Seek to position on movie. durationPart is specified with part of the total movie duration, e.g.
// 0.15 is 15% or 15s in a 100s movie
int seekTo(AVFormatContext *format, double durationPart) {
	int64_t timestamp = format->duration * durationPart / AV_TIME_BASE;
	LOGI("Seeking to %" PRId64 "s.", timestamp);

	// AVSEEK_FLAG_ANY enables seeking to every frame and not just keyframes.
	avformat_seek_file(format, -1, INT64_MIN, timestamp, INT64_MAX, AVSEEK_FLAG_ANY);
}

// Converts Java's String[] to C's char**
CArray convertToCArray(JNIEnv *env, jobjectArray args) {
    CArray cArray;
	jint size = (*env)->GetArrayLength(env, args);
	cArray.size = size;
	cArray.jstrings = (jstring *) malloc(sizeof(jstring) * cArray.size);
	cArray.arr = (const char **) malloc(sizeof(const char *) * cArray.size);

	for(int i=0; i<cArray.size; ++i) {
		cArray.jstrings[i] = (jstring)(*env)->GetObjectArrayElement(env, args, i);
		cArray.arr[i] = (const char *)(*env)->GetStringUTFChars(env, cArray.jstrings[i], false);
	}

	return cArray;
}

void freeCArray(JNIEnv *env, CArray *cArray) {
	CArray myArr = *cArray;
	for(int i=0; i<cArray->size; ++i) {
		// Free char*
		(*env)->ReleaseStringUTFChars(env, cArray->jstrings[i], cArray->arr[i]);

		// Delete jstring reference (unnecessary as these are freed after the native method returns)
		(*env)->DeleteLocalRef(env, cArray->jstrings[i]);
	}
	// Free char**
    free(cArray->arr);

	// Free jstring[]
	free(cArray->jstrings);
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
