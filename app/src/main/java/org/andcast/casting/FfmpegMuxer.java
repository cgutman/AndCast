package org.andcast.casting;

public class FfmpegMuxer {

    static {
        // FFMPEG dependencies
        System.loadLibrary("avutil-52");
        System.loadLibrary("swresample-0");
        System.loadLibrary("swscale-2");
        System.loadLibrary("avcodec-55");
        System.loadLibrary("avformat-55");

        // Our muxer
        System.loadLibrary("ffmpeg_muxer");
    }

    public static native int initializeMuxer(String formatName, String fileName, int width, int height, int frameRate, int iFrameInterval);

    public static native void cleanupMuxer();

    public static native int submitVideoFrame(byte[] buffer, long frameTimestamp);
}
