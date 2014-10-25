#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <arpa/inet.h>

#include <libavformat/avformat.h>

#include "muxer.h"

static AVOutputFormat *format;
static AVFormatContext *formatContext;

static AVStream *videoStream;
static AVCodecContext *videoCodec;

static char sps[64];
static int spsLength = 0;

static char pps[64];
static int ppsLength = 0;

int initializeMuxer(const char *formatName, const char *fileName, PCAST_CONFIGURATION config) {
    AVOutputFormat *format;

    av_register_all();
    avformat_network_init();
    
    format = av_guess_format(formatName, fileName, NULL);
    if (format == NULL) {
        fprintf(stderr, "av_guess_format() failed\n");
        return -1;
    }
    
    formatContext = avformat_alloc_context();
    if (formatContext == NULL) {
        fprintf(stderr, "avformat_alloc_context() failed\n");
        return -2;
    }
    
    // Initialize the AVFormatContext
    formatContext->oformat = format;
    strcpy(formatContext->filename, fileName);
    
    // Add the video stream
    videoStream = avformat_new_stream(formatContext, NULL);
    if (videoStream == NULL) {
        fprintf(stderr, "avformat_new_stream() failed\n");
        return -3;
    }
    
    // Configure the video codec
    videoCodec = videoStream->codec;
    videoCodec->codec_type = AVMEDIA_TYPE_VIDEO;
    videoCodec->codec_id = AV_CODEC_ID_H264;
    videoCodec->bit_rate = 0;
    videoCodec->width = config->width;
    videoCodec->height = config->height;
    videoCodec->time_base.den = config->frameRate;
    videoCodec->time_base.num = 1;
    videoCodec->gop_size = config->frameRate * config->iFrameInterval;
    videoCodec->pix_fmt = AV_PIX_FMT_YUV420P;
    
    if (format->flags & AVFMT_GLOBALHEADER) {
        videoCodec->flags |= CODEC_FLAG_GLOBAL_HEADER;
    }
    
    if ((format->flags & AVFMT_NOFILE) == 0) {
        int ret;
        if ((ret = avio_open(&formatContext->pb, fileName, AVIO_FLAG_WRITE)) < 0) {
            fprintf(stderr, "avio_open() failed: %d\n", ret);
            return -4;
        }
    }
    
    return 0;
}

void handlePossibleParameterSet(char *data, int length) {
    switch (data[4] & 0x1F)
    {
    case 0x7:
        // The 00 00 00 01 prefix is chopped off
        memcpy(sps, &data[4], length - 4);
        spsLength = length - 4;
        printf("Got SPS\n");
        break;
    case 0x8:
        // Same here; we're chopping off the Annex B prefix
        memcpy(pps, &data[4], length - 4);
        ppsLength = length - 4;
        printf("Got PPS\n");
        break;
    }
}

void saveParameterSets(char *data, int length) {
    int lastOffset = -1;
    int i;

    for (i = 0; i < length; i++) {
        if (data[i] == 0 && data[i+1] == 0 &&
            data[i+2] == 0 && data[i+3] == 1)
        {
            if (lastOffset == -1) {
                lastOffset = i;
            }
            else {
                handlePossibleParameterSet(&data[lastOffset], i - lastOffset);

                lastOffset = i;
            }
        }
    }

    if (lastOffset != -1) {
        handlePossibleParameterSet(&data[lastOffset], length - lastOffset);
    }
}

static int frameNum;

int submitVideoFrame(char *data, int length, long frameTimestamp) {
    AVPacket pkt;
    int ret;
    short tmp;
    int netLen;

    if (ppsLength == 0 || spsLength == 0) {
        saveParameterSets(data, length);
    }
    
    // Initialize extradata if we haven't yet and have the required data
    if (videoCodec->extradata == NULL && ppsLength > 0 && spsLength > 0) {
        videoCodec->extradata_size = 8 + spsLength + 1 + 2 + ppsLength;
        videoCodec->extradata = av_mallocz(videoCodec->extradata_size);
        if (videoCodec->extradata == NULL) {
            fprintf(stderr, "No memory for extradata\n");
            return -1;
        }

        // Initialize AVCC header
        videoCodec->extradata[0] = 0x01;
        videoCodec->extradata[1] = sps[1];
        videoCodec->extradata[2] = sps[2];
        videoCodec->extradata[3] = sps[3];
        videoCodec->extradata[4] = 0xFC | 3;
        videoCodec->extradata[5] = 0xE0 | 1;
        
        // Copy SPS length in big-endian
        tmp = htons(spsLength);
        memcpy(&videoCodec->extradata[6], &tmp, sizeof(tmp));
        
        // Copy SPS data
        memcpy(&videoCodec->extradata[8], sps, spsLength);
        
        videoCodec->extradata[8+spsLength] = 0x01;
        
        // Copy PPS length in big-endian
        tmp = htons(ppsLength);
        memcpy(&videoCodec->extradata[8+spsLength+1], &tmp, sizeof(tmp));
        
        // Copy PPS data
        memcpy(&videoCodec->extradata[8+spsLength+3], pps, ppsLength);
    
        // Write the format header
        avformat_write_header(formatContext, NULL);
        return 0;
    }
    else if (videoCodec->extradata == NULL) {
        return -2;
    }
    
    av_init_packet(&pkt);
    
    if ((data[4] & 0x1F) == 0x5) {
        pkt.flags |= AV_PKT_FLAG_KEY;
    }
    
    // NALUs in FLV must be preceded by this length
    netLen = htonl(length - 4);
    memcpy(data, &netLen, sizeof(netLen));
    
    pkt.pts = frameTimestamp;
    pkt.stream_index = videoStream->index;
    pkt.data = (unsigned char*)(data);
    pkt.size = length;
    
    ret = av_interleaved_write_frame(formatContext, &pkt);
    
    return ret;
}

void cleanupMuxer(void) {
    int i;
    
    for (i = 0; i < formatContext->nb_streams; i++) {
        av_freep(&formatContext->streams[i]->codec);
        av_freep(&formatContext->streams[i]);
    }

    if ((format->flags & AVFMT_NOFILE) == 0) {
        //avio_close(formatContext->pb);
    }

    if (formatContext) {
        av_free(formatContext);
        formatContext = NULL;
    }
}
