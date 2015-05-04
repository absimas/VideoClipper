LOCAL_PATH := $(call my-dir)

# Build FFmpeg to a static local lib
include $(CLEAR_VARS)
LOCAL_MODULE := ffmpeg
LOCAL_CFLAGS += -I$(LOCAL_PATH)/ffmpeg -Dmain=ffmpeg -g -Wno-deprecated-declarations
LOCAL_LDLIBS += -llog
LOCAL_SRC_FILES := $(addprefix ffmpeg/, \
	cmdutils.c \
	ffmpeg.c \
	ffmpeg_opt.c \
	ffmpeg_filter.c)
include $(BUILD_STATIC_LIBRARY)

# Build FFprobe to a static local lib
include $(CLEAR_VARS)
LOCAL_MODULE := ffprobe
LOCAL_CFLAGS += -I$(LOCAL_PATH)/ffmpeg -Dmain=ffprobe -g -Wno-deprecated-declarations
LOCAL_LDLIBS += -llog
LOCAL_SRC_FILES := $(addprefix ffmpeg/, \
	cmdutils.c \
	ffprobe.c)
include $(BUILD_STATIC_LIBRARY)

# List of prebuilt static libraries
STATIC_LIBS := $(addprefix $(LOCAL_PATH)/ffmpeg/, \
	libavdevice/libavdevice.a \
	libavformat/libavformat.a \
	libavfilter/libavfilter.a \
	libavcodec/libavcodec.a \
	libswscale/libswscale.a \
	libavutil/libavutil.a \
	libswresample/libswresample.a \
	libpostproc/libpostproc.a)

# Add everything together to VC lib
include $(CLEAR_VARS)
LOCAL_MODULE  := vc
LOCAL_CFLAGS += -I$(LOCAL_PATH)/ffmpeg -I$(LOCAL_PATH)/vc
LOCAL_CFLAGS += -g -Wno-deprecated-declarations -std=c99
LOCAL_LDLIBS += -llog -ljnigraphics -lz $(STATIC_LIBS)
LOCAL_STATIC_LIBRARIES := ffmpeg ffprobe
LOCAL_SRC_FILES :=  vc/vc.c
include $(BUILD_SHARED_LIBRARY)