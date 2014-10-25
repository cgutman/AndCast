#include <stdlib.h>

#include "muxer.h"

#include <jni.h>


JNIEXPORT jint JNICALL
Java_org_andcast_casting_FfmpegMuxer_initializeMuxer(JNIEnv *env, jobject this, jstring formatName,
    jstring fileName, jint width, jint height, jint frameRate, jint iFrameInterval)
{
    CAST_CONFIGURATION config;
    
    config.width = width;
    config.height = height;
    config.frameRate = frameRate;
    config.iFrameInterval = iFrameInterval;

    return initializeMuxer((*env)->GetStringUTFChars(env, formatName, NULL),
                           (*env)->GetStringUTFChars(env, fileName, NULL),
                           &config);
}

JNIEXPORT jint JNICALL
Java_org_andcast_casting_FfmpegMuxer_submitVideoFrame(JNIEnv *env, jobject this, jbyteArray buffer, jlong frameTimestamp)
{
    char *data;
    int ret;

    data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data == NULL) {
        return -1;
    }
    
    ret = submitVideoFrame(data, (*env)->GetArrayLength(env, buffer), frameTimestamp);
    
    (*env)->ReleaseByteArrayElements(env, buffer, data, 0);
    
    return ret;
}

JNIEXPORT void JNICALL
Java_org_andcast_casting_FfmpegMuxer_cleanupMuxer(JNIEnv *env, jobject this)
{
    cleanupMuxer();
}
