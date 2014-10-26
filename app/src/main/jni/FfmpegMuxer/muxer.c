#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <arpa/inet.h>

#include <libavformat/avformat.h>
#include <libswresample/swresample.h>

#include "muxer.h"

static AVOutputFormat *format;
static AVFormatContext *formatContext;

static AVStream *videoStream;
static AVCodecContext *videoCodecCtx;

static char sps[64];
static int spsLength = 0;

static char pps[64];
static int ppsLength = 0;

static AVStream *audioStream;
static AVCodecContext *audioCodecCtx;

static unsigned char ** srcSamplesData;
static int srcSamplesLinesize;
static int srcSamplesCount;

static unsigned char ** dstSamplesData;
static int dstSamplesLinesize;
static int dstSamplesSize;
static int maxDstSamplesCount;

static SwrContext *swrContext;

int initializeMuxer(const char *formatName, const char *fileName, PCAST_CONFIGURATION config) {
    AVOutputFormat *format;
    AVCodec *audCodec;
    int ret;

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

    if (config->muxEnableFlags & ENABLE_VIDEO) {
        // Add the video stream
        videoStream = avformat_new_stream(formatContext, NULL);
        if (videoStream == NULL) {
            fprintf(stderr, "avformat_new_stream() #1 failed\n");
            return -3;
        }

        // Configure the video codec
        videoCodecCtx = videoStream->codec;
        videoCodecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
        videoCodecCtx->codec_id = AV_CODEC_ID_H264;
        videoCodecCtx->bit_rate = 0;
        videoCodecCtx->width = config->width;
        videoCodecCtx->height = config->height;
        videoCodecCtx->time_base.den = config->frameRate;
        videoCodecCtx->time_base.num = 1;
        videoCodecCtx->gop_size = config->frameRate * config->iFrameInterval;
        videoCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;
    }

    if (config->muxEnableFlags & ENABLE_AUDIO) {
        audCodec = avcodec_find_encoder(AV_CODEC_ID_AAC);
        if (audCodec == NULL) {
            fprintf(stderr, "avcodec_find_encoder failed\n");
            return -4;
        }

        // Add the audio stream
        audioStream = avformat_new_stream(formatContext, audCodec);
        if (audioStream == NULL) {
            fprintf(stderr, "avformat_new_stream() #2 failed\n");
            return -5;
        }

        // Configure the audio codec
        audioCodecCtx = audioStream->codec;
        audioCodecCtx->codec_type = AVMEDIA_TYPE_AUDIO;
        audioCodecCtx->codec_id = AV_CODEC_ID_AAC;
        audioCodecCtx->bit_rate = config->audioBitrate;
        audioCodecCtx->sample_rate = 44100;
        audioCodecCtx->channels = config->audioChannels;
        audioCodecCtx->sample_fmt = AV_SAMPLE_FMT_FLTP;
        audioCodecCtx->profile = FF_PROFILE_AAC_LOW;
        audioCodecCtx->strict_std_compliance = FF_COMPLIANCE_EXPERIMENTAL;

        ret = avcodec_open2(audioCodecCtx, audCodec, NULL);
        if (ret < 0) {
            fprintf(stderr, "avcodec_open2() failed: %d\n", ret);
            return ret;
        }

        srcSamplesCount = audioCodecCtx->codec->capabilities & CODEC_CAP_VARIABLE_FRAME_SIZE ?
            10000 : audioCodecCtx->frame_size;

        ret = av_samples_alloc_array_and_samples(&srcSamplesData, &srcSamplesLinesize,
            audioCodecCtx->channels, srcSamplesCount, audioCodecCtx->sample_fmt, 0);
        if (ret < 0) {
            fprintf(stderr, "av_samples_alloc_array_and_samples() failed: %d\n", ret);
            return ret;
        }

        // Our input is 16-bit signed samples so we'll need a resampler to convert to FP
        swrContext = swr_alloc();
        if (swrContext == NULL) {
            fprintf(stderr, "swr_alloc() failed\n");
            return -1;
        }

        av_opt_set_int(swrContext, "in_channel_count", audioCodecCtx->channels, 0);
        av_opt_set_int(swrContext, "in_sample_rate", audioCodecCtx->sample_rate, 0);
        av_opt_set_int(swrContext, "in_sample_fmt", AV_SAMPLE_FMT_S16, 0);
        av_opt_set_int(swrContext, "out_channel_count", audioCodecCtx->channels, 0);
        av_opt_set_int(swrContext, "out_sample_rate", audioCodecCtx->sample_rate, 0);
        av_opt_set_int(swrContext, "out_sample_fmt", audioCodecCtx->sample_fmt, 0);

        ret = swr_init(swrContext);
        if (ret < 0) {
            fprintf(stderr, "swr_init() failed: %d\n", ret);
            return ret;
        }

        maxDstSamplesCount = srcSamplesCount;
        ret = av_samples_alloc_array_and_samples(&dstSamplesData, &dstSamplesLinesize, audioCodecCtx->channels,
            maxDstSamplesCount, audioCodecCtx->sample_fmt, 0);
        if (ret < 0) {
            fprintf(stderr, "av_samples_alloc_array_and_samples() failed: %d\n", ret);
            return ret;
        }

        dstSamplesSize = av_samples_get_buffer_size(NULL, audioCodecCtx->channels, maxDstSamplesCount,
            audioCodecCtx->sample_fmt, 0);
    }
    
    if (format->flags & AVFMT_GLOBALHEADER) {
        if (videoCodecCtx != NULL) {
            videoCodecCtx->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
        if (audioCodecCtx != NULL) {
            audioCodecCtx->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
    }
    
    if ((format->flags & AVFMT_NOFILE) == 0) {
        if ((ret = avio_open(&formatContext->pb, fileName, AVIO_FLAG_WRITE)) < 0) {
            fprintf(stderr, "avio_open() failed: %d\n", ret);
            return -4;
        }
    }

    // FIXME: Needs to be done earlier for H264

    printf("Writing header\n");
    avformat_write_header(formatContext, NULL);
    
    return 0;
}

int getRequiredAudioBufferSize(void) {
    return
        // AAC requires submissions in multiples of 1024 samples
        1024 *

        // Before they're resampled, our samples take 2 bytes each
        2 *

        // and we have n channels per sample
        audioCodecCtx->channels;
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

int submitAudioFrame(char *data, int length, long frameTimestamp) {
    AVPacket pkt;
    int ret;
    AVFrame *frame;
    int got_packet;
    int sampleCount;

    frame = avcodec_alloc_frame();
    if (frame == NULL) {
        fprintf(stderr, "Failed to allocate frame\n");
        return -1;
    }

    av_init_packet(&pkt);

    // Copy our data in
    memcpy(srcSamplesData[0], data, length);
    srcSamplesCount = length / 4;

    // Resample to floating point
    sampleCount = av_rescale_rnd(swr_get_delay(swrContext, audioCodecCtx->sample_rate) + srcSamplesCount,
        audioCodecCtx->sample_rate, audioCodecCtx->sample_rate, AV_ROUND_UP);
    if (sampleCount > maxDstSamplesCount) {
        // Need to resize the buffer
        av_free(dstSamplesData[0]);

        ret = av_samples_alloc(dstSamplesData, &dstSamplesLinesize, audioCodecCtx->channels,
            sampleCount, audioCodecCtx->sample_fmt, 0);
        if (ret < 0) {
            fprintf(stderr, "av_samples_alloc() failed: %d\n", ret);
            return ret;
        }

        maxDstSamplesCount = sampleCount;
        dstSamplesSize = av_samples_get_buffer_size(NULL, audioCodecCtx->channels, sampleCount,
            audioCodecCtx->sample_fmt, 0);
    }

    ret = swr_convert(swrContext, dstSamplesData, sampleCount,
        (const unsigned char **)srcSamplesData, srcSamplesCount);
    if (ret < 0) {
        fprintf(stderr, "swr_convert() failed: %d\n", ret);
        return ret;
    }

    frame->nb_samples = sampleCount;
    ret = avcodec_fill_audio_frame(frame, audioCodecCtx->channels,
        audioCodecCtx->sample_fmt, dstSamplesData[0], dstSamplesSize, 0);
    if (ret < 0) {
        fprintf(stderr, "avcodec_fill_audio_frame() failed: %d\n", ret);
        avcodec_free_frame(&frame);
        return ret;
    }

    // pkt is freed on failure or !got_packet
    pkt.data = NULL;
    pkt.size = 0;
    ret = avcodec_encode_audio2(audioCodecCtx, &pkt, frame, &got_packet);
    if (ret < 0) {
        fprintf(stderr, "avcodec_encode_audio2() failed: %d\n", ret);
        avcodec_free_frame(&frame);
        return ret;
    }

    if (!got_packet) {
        // Nothing to do here
        return 0;
    }

    pkt.stream_index = audioStream->index;

    ret = av_interleaved_write_frame(formatContext, &pkt);
    avcodec_free_frame(&frame);
    av_free_packet(&pkt);

    if (ret != 0) {
        fprintf(stderr, "av_interleaved_write_frame() failed: %d\n", ret);
        return ret;
    }

    return 0;
}

int submitVideoFrame(char *data, int length, long frameTimestamp) {
    AVPacket pkt;
    int ret;
    short tmp;
    int netLen;

    if (ppsLength == 0 || spsLength == 0) {
        saveParameterSets(data, length);
    }
    
    // Initialize extradata if we haven't yet and have the required data
    if (videoCodecCtx->extradata == NULL && ppsLength > 0 && spsLength > 0) {
        videoCodecCtx->extradata_size = 8 + spsLength + 1 + 2 + ppsLength;
        videoCodecCtx->extradata = av_mallocz(videoCodecCtx->extradata_size);
        if (videoCodecCtx->extradata == NULL) {
            fprintf(stderr, "No memory for extradata\n");
            return -1;
        }

        // Initialize AVCC header
        videoCodecCtx->extradata[0] = 0x01;
        videoCodecCtx->extradata[1] = sps[1];
        videoCodecCtx->extradata[2] = sps[2];
        videoCodecCtx->extradata[3] = sps[3];
        videoCodecCtx->extradata[4] = 0xFC | 3;
        videoCodecCtx->extradata[5] = 0xE0 | 1;
        
        // Copy SPS length in big-endian
        tmp = htons(spsLength);
        memcpy(&videoCodecCtx->extradata[6], &tmp, sizeof(tmp));
        
        // Copy SPS data
        memcpy(&videoCodecCtx->extradata[8], sps, spsLength);
        
        videoCodecCtx->extradata[8+spsLength] = 0x01;
        
        // Copy PPS length in big-endian
        tmp = htons(ppsLength);
        memcpy(&videoCodecCtx->extradata[8+spsLength+1], &tmp, sizeof(tmp));
        
        // Copy PPS data
        memcpy(&videoCodecCtx->extradata[8+spsLength+3], pps, ppsLength);
    
        // Write the format header
        return 0;
    }
    else if (videoCodecCtx->extradata == NULL) {
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

    if (audioStream != NULL) {
        avcodec_close(audioStream->codec);
    }

    if ((format->flags & AVFMT_NOFILE) == 0) {
        //avio_close(formatContext->pb);
    }

    if (formatContext) {
        av_free(formatContext);
        formatContext = NULL;
    }
}
