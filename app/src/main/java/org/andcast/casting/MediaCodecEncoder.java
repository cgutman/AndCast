package org.andcast.casting;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.view.Surface;

public class MediaCodecEncoder {
	
	private MediaCodec encoder;
	private Surface inputSurface;
    private Thread outputThread;
	
	public static MediaCodecEncoder createEncoder(CastConfiguration config) throws IOException {
		MediaCodecEncoder encoder = new MediaCodecEncoder();
		encoder.encoder = MediaCodec.createEncoderByType("video/avc");
		System.out.println("Encoding using "+encoder.encoder.getName());
		
		MediaFormat format = MediaFormat.createVideoFormat("video/avc", config.width, config.height);
		format.setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSecs);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, (1 / config.frameRate) * 1000 * 1000);
		
		encoder.encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		
		encoder.inputSurface = encoder.encoder.createInputSurface();
		
		return encoder;
	}
	
	public void stop() {
        if (outputThread != null) {
            outputThread.interrupt();

            try {
                outputThread.join();
            } catch (InterruptedException e) {}

            outputThread = null;
        }

		encoder.stop();
	}
	
	public void release() {
		inputSurface.release();
		encoder.release();
	}
	
	public void start() {
		encoder.start();
		
		outputThread = new Thread() {
			@Override
			public void run() {
				BufferInfo info = new BufferInfo();
				
				System.out.println("Output thread has started");

                long firstTime = System.currentTimeMillis();

				for (;;) {
                    int index;
                    do {
                        index = encoder.dequeueOutputBuffer(info, 100 * 1000);

                        // Check if we've been interrupted when we time out
                        if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            if (Thread.interrupted()) {
                                return;
                            }
                        }
                    } while (index == MediaCodec.INFO_TRY_AGAIN_LATER);

					if (index >= 0) {

						ByteBuffer buf = encoder.getOutputBuffer(index);

						byte[] buff = new byte[buf.limit()-buf.position()];
						buf.get(buff);

                        int ret = FfmpegMuxer.submitVideoFrame(buff, buff.length, System.currentTimeMillis() - firstTime);
                        System.out.println("Video frame Ret: "+ret);

                        encoder.releaseOutputBuffer(index, false);
					}
					else {
						switch (index) {
						case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
							System.out.println("Output format changed");
							System.out.println("New output Format: " + encoder.getOutputFormat());
							break;
						default:
							System.out.println("Strange status: "+index);
							break;
						}
					}
				}
			}
		};

        outputThread.start();
	}
	
	public Surface getInputSurface() {
		return inputSurface;
	}
}
