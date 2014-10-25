#pragma once

// This is a subset of the fields in the CastConfiguration object
typedef struct _CAST_CONFIGURATION {
    int width, height;
    int frameRate;
    int iFrameInterval;
} CAST_CONFIGURATION, *PCAST_CONFIGURATION;

int initializeMuxer(const char *formatName, const char *fileName, PCAST_CONFIGURATION config);

int submitVideoFrame(char *data, int length, long frameTimestamp);

void cleanupMuxer(void);
