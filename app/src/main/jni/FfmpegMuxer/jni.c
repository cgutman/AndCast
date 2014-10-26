#include <stdlib.h>

#include "muxer.h"

#include <jni.h>


JNIEXPORT jint JNICALL
Java_org_andcast_casting_FfmpegMuxer_initializeMuxer(JNIEnv *env, jobject this, jstring formatName,
    jstring fileName, jint width, jint height, jint frameRate, jint iFrameInterval,
    jint audioBitrate, jint audioChannels)
{
    CAST_CONFIGURATION config;
    const char *formatNameStr = NULL;
    const char *fileNameStr = NULL;
    
    config.width = width;
    config.height = height;
    config.frameRate = frameRate;
    config.iFrameInterval = iFrameInterval;

    config.audioBitrate = audioBitrate;
    config.audioChannels = audioChannels;

    config.muxEnableFlags = 0;
    if (config.width != 0 && config.height != 0) {
        config.muxEnableFlags |= ENABLE_VIDEO;
    }
    if (config.audioBitrate != 0 && config.audioChannels != 0) {
        config.muxEnableFlags |= ENABLE_AUDIO;
    }

    if (formatName != NULL) {
        formatNameStr = (*env)->GetStringUTFChars(env, formatName, NULL);
    }
    if (fileName != NULL) {
        fileNameStr = (*env)->GetStringUTFChars(env, fileName, NULL);
    }

    return initializeMuxer(formatNameStr,
                           fileNameStr,
                           &config);
}

// Returns the size of audio buffers that must be passed to submitAudioFrame().
JNIEXPORT jint JNICALL
Java_org_andcast_casting_FfmpegMuxer_getRequiredAudioBufferSize(JNIEnv *env, jobject this)
{
    return getRequiredAudioBufferSize();
}

JNIEXPORT jint JNICALL
Java_org_andcast_casting_FfmpegMuxer_submitVideoFrame(JNIEnv *env, jobject this, jbyteArray buffer, jint length, jlong frameTimestamp)
{
    char *data;
    int ret;

    data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data == NULL) {
        return -1;
    }
    
    ret = submitVideoFrame(data, length, frameTimestamp);
    
    (*env)->ReleaseByteArrayElements(env, buffer, data, 0);
    
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_andcast_casting_FfmpegMuxer_submitAudioFrame(JNIEnv *env, jobject this, jbyteArray buffer, jint length, jlong frameTimestamp)
{
    char *data;
    int ret;

    data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data == NULL) {
        return -1;
    }

    ret = submitAudioFrame(data, length, frameTimestamp);

    (*env)->ReleaseByteArrayElements(env, buffer, data, 0);

    return ret;
}

JNIEXPORT void JNICALL
Java_org_andcast_casting_FfmpegMuxer_cleanupMuxer(JNIEnv *env, jobject this)
{
    cleanupMuxer();
}
