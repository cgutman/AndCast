#pragma once

#define ENABLE_VIDEO 0x01
#define ENABLE_AUDIO 0x02

// This is a subset of the fields in the CastConfiguration object
typedef struct _CAST_CONFIGURATION {
    int muxEnableFlags;

    int width, height;
    int frameRate;
    int iFrameInterval;

    int audioBitrate;
    int audioChannels;
} CAST_CONFIGURATION, *PCAST_CONFIGURATION;

int initializeMuxer(const char *formatName, const char *fileName, PCAST_CONFIGURATION config);

int submitVideoFrame(char *data, int length, long frameTimestamp);

int submitAudioFrame(char *data, int length, long frameTimestamp);

int getRequiredAudioBufferSize(void);

void cleanupMuxer(void);
