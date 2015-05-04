#include <libswresample/swresample.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libavutil/pixfmt.h>

#include <android/log.h>
#include <stdio.h>
#include <wchar.h>
#include <jni.h>

#define TAG "ffmpeg_framer.c"
#define LOGI(...) __android_log_print(4, TAG, __VA_ARGS__);
#define LOGE(...) __android_log_print(6, TAG, __VA_ARGS__);

const char* mMainActivityClassPath = "com/simas/vc/MainActivity";

////////////////////////////////////////////////////////////////////////////////////////////////////

// Methods
void cMain(JNIEnv* env, jobject obj) {
	LOGE("IT FUCKING WORKS!!!~~~~~");
}

void saveFrame(JNIEnv *env, jobject activityInstance, jobject pBitmap, int width, int height,
		int frameNo, char *destination) {
	char szFilename[200];
	sprintf(szFilename, "%s/frame%d.jpg", destination, frameNo);

	jclass mainActCls = (*env)->GetObjectClass(env, activityInstance);
	jmethodID saveFrameMethod = (*env)->GetMethodID(env, mainActCls, "saveFrameToPath",
			"(Landroid/graphics/Bitmap;Ljava/lang/String;)V");

	LOGI("Save frame %d via Java", frameNo);
	jstring filePath = (*env)->NewStringUTF(env, szFilename);
	(*env)->CallVoidMethod(env, activityInstance, saveFrameMethod, pBitmap, filePath);
	LOGI("Save frame %d via Java done", frameNo);
}

jobject createBitmap(JNIEnv *env, int pWidth, int pHeight) {
	int i;

	// Get Bitmap class and createBitmap method ID
	jclass javaBitmapClass = (jclass)(*env)->FindClass(env, "android/graphics/Bitmap");
	jmethodID mid = (*env)->GetStaticMethodID(env, javaBitmapClass, "createBitmap",
			"(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

	// Create Bitmap.Config // Reference: https://forums.oracle.com/thread/1548728
	const wchar_t* configName = L"ARGB_8888";
	int len = wcslen(configName);
	jstring jConfigName;

	if (sizeof(wchar_t) != sizeof(jchar)) {
		// wchar_t is defined as different length than jchar(2 bytes)
		jchar *str = (jchar*)malloc((len+1)*sizeof(jchar));
		for (i = 0; i < len; ++i) {
			str[i] = (jchar)configName[i];
		}
		str[len] = 0;
		jConfigName = (*env)->NewString(env, (const jchar*)str, len);
	} else {
		//wchar_t is defined same length as jchar(2 bytes)
		jConfigName = (*env)->NewString(env, (const jchar*)configName, len);
	}

	jclass bitmapConfigClass = (*env)->FindClass(env, "android/graphics/Bitmap$Config");
	jobject javaBitmapConfig = (*env)->CallStaticObjectMethod(env, bitmapConfigClass,
			(*env)->GetStaticMethodID(env, bitmapConfigClass, "valueOf",
					"(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;"),
			jConfigName);

	// Create the bitmap
	jobject bitmap = (*env)->CallStaticObjectMethod(env, javaBitmapClass, mid,
			pWidth, pHeight, javaBitmapConfig);
	return bitmap;
}

jboolean mergeVideos(JNIEnv *env, jobject obj, jobject activityInstance, jstring filename1,
		jstring filename2) {
	AVFormatContext *formatCtx = NULL;
	int             i, videoStream;
	AVCodecContext  *codecCtx = NULL;
	AVCodec         *codex = NULL;
	AVFrame         *frame = NULL;
	AVFrame         *frameRGBA = NULL;
	AVPacket        packet;
	int             frameFinished;
	jobject			bitmap;
	void* 			buffer;

	AVDictionary    *optionsDict = NULL;
	struct SwsContext      *sws_ctx = NULL;
	char *cFilename1;
	char *cFilename2;

	// Register all formats and codecs
	av_register_all();

	//get C string from jstring
	cFilename1 = (char *)(*env)->GetStringUTFChars(env, filename1, NULL);
	cFilename2 = (char *)(*env)->GetStringUTFChars(env, filename2, NULL);

	// Open video file
	if (avformat_open_input(&formatCtx, cFilename1, NULL, NULL) != 0) {
		LOGE("Couldn't open %s!", cFilename1);
		return JNI_FALSE; // Couldn't open file
	}

	// Retrieve stream information
	if (avformat_find_stream_info(formatCtx, NULL) < 0) {
		LOGE("Couldn't find stream info!");
		return JNI_FALSE; // Couldn't find stream information
	}

	// Dump information about file onto standard error
	av_dump_format(formatCtx, 0, cFilename1, 0);

	// Find the first video stream
	videoStream = -1;
	for(i=0; i<formatCtx->nb_streams; i++) {
		if(formatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
			videoStream = i;
			break;
		}
	}
	if(videoStream == -1) {
		LOGE("Couldn't find video stream!");
		return JNI_FALSE; // Didn't find a video stream
	}

	// Get a pointer to the codec context for the video stream
	codecCtx = formatCtx->streams[videoStream]->codec;

	// Find the decoder for the video stream
	codex = avcodec_find_decoder(codecCtx->codec_id);
	if (codex == NULL) {
		LOGE("Unsupported codec!");
		return JNI_FALSE;
	}
	// Open codec
	if (avcodec_open2(codecCtx, codex, &optionsDict) < 0) {
		LOGE("Couldn't open codec!");
		return JNI_FALSE;
	}

	// Allocate video frame
	frame = avcodec_alloc_frame();

	// Allocate an AVFrame structure
	if ((frameRGBA = avcodec_alloc_frame()) == NULL) {
		LOGE("AVFrame structure allocation failed!");
		return JNI_FALSE;
	}

	// Create a bitmap as the buffer for frameRGBA
	bitmap = createBitmap(env, codecCtx->width, codecCtx->height);
	if (AndroidBitmap_lockPixels(env, bitmap, &buffer) < 0) {
		LOGE("Bitmap buffer creation failed!");
		return JNI_FALSE;
	}

	// Get the scaling context
	sws_ctx = sws_getContext(codecCtx->width, codecCtx->height,
		codecCtx->pix_fmt, codecCtx->width, codecCtx->height,
        AV_PIX_FMT_RGBA, SWS_BILINEAR, NULL, NULL, NULL);

	// Assign appropriate parts of bitmap to image planes in frameRGBA
	// Note that frameRGBA is an AVFrame, but AVFrame is a superset
	// of AVPicture
	avpicture_fill((AVPicture *)frameRGBA, buffer, AV_PIX_FMT_RGBA,
		 codecCtx->width, codecCtx->height);

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
				sws_scale(sws_ctx,
					(uint8_t const * const *)frame->data, frame->linesize,
					0, codecCtx->height,
					frameRGBA->data, frameRGBA->linesize);

				// Save the frame to disk
				if (++i <= 5) {
					saveFrame(env, activityInstance, bitmap,
							codecCtx->width, codecCtx->height, i, cFilename2);
				}
			}
		}
		// Free the packet that was allocated by av_read_frame
		av_free_packet(&packet);
	}

	// Unlock the bitmap
	AndroidBitmap_unlockPixels(env, bitmap);

	// Free the RGB image
	av_free(frameRGBA);

	// Free the YUV frame
	av_free(frame);

	// Close the codec
	avcodec_close(codecCtx);

	// Close the video file
	avformat_close_input(&formatCtx);

	return JNI_TRUE;
}

////////////////////////////////////////////////////////////////////////////////////////////////////

// NDK Initialization
static JNINativeMethod methodTable[] = {
	{"cMain", "()V", (void *) cMain},
	{"mergeVideos", "(Lcom/simas/vc/MainActivity;"
			"Ljava/lang/String;Ljava/lang/String;)Z", (void*) mergeVideos}
//	{"engine_start", "(Landroid/content/res/AssetManager;)V", (void *) engine_start},
};

jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
	LOGE("ONLOAD");
	JNIEnv *env;
	if ((*jvm)->GetEnv(jvm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		LOGE("Failed to get the environment");
		return -1;
	}

	jclass activityClass = (*env)->FindClass(env, mMainActivityClassPath);
	if (!activityClass) {
		LOGE("Failed to get %s class reference", mMainActivityClassPath);
		return -1;
	}

	// Register methods
	(*env)->RegisterNatives(env, activityClass, methodTable,
			sizeof(methodTable) / sizeof (methodTable[0]));

	return JNI_VERSION_1_6;
}
