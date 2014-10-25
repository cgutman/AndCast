LOCAL_PATH:= $(call my-dir)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= libcrypto
LOCAL_SRC_FILES:= $(TARGET_ARCH_ABI)/libcrypto.so
include $(PREBUILT_SHARED_LIBRARY)
 
include $(CLEAR_VARS)
LOCAL_MODULE:= libssl
LOCAL_SRC_FILES:= $(TARGET_ARCH_ABI)/libssl.so
include $(PREBUILT_SHARED_LIBRARY)

