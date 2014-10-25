LOCAL_PATH:= $(call my-dir)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= librtmp
LOCAL_SRC_FILES:= $(TARGET_ARCH_ABI)/librtmp.so
include $(PREBUILT_SHARED_LIBRARY)

