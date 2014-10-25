# Android.mk for FfmpegMuxer
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := ffmpeg_muxer
LOCAL_SRC_FILES := muxer.c jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
LOCAL_SHARED_LIBRARIES := libavcodec libavformat libswscale libavutil libwsresample openssl

include $(BUILD_SHARED_LIBRARY)
