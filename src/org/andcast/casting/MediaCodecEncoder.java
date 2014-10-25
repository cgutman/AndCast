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
	
	public static MediaCodecEncoder createEncoder(CastConfiguration config) throws IOException {
		MediaCodecEncoder encoder = new MediaCodecEncoder();
		encoder.encoder = MediaCodec.createEncoderByType("video/avc");
		System.out.println("Encoding using "+encoder.encoder.getName());
		
		MediaFormat format = MediaFormat.createVideoFormat("video/avc", config.width, config.height);
		format.setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSecs);
		
		encoder.encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		
		encoder.inputSurface = encoder.encoder.createInputSurface();
		
		return encoder;
	}
	
	public void stop() {
		encoder.stop();
	}
	
	public void release() {
		inputSurface.release();
		encoder.release();
	}
	
	public void start() {
		encoder.start();
		
		new Thread() {
			@Override
			public void run() {
				BufferInfo info = new BufferInfo();
				
				System.out.println("Output thread has started");
				
				FileOutputStream fout;
								
				try {
					fout = new FileOutputStream("/sdcard/andcast.h264");
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
				
				for (;;) {
					int index = encoder.dequeueOutputBuffer(info, -1);
					if (index >= 0) {
						System.out.println("Got output buffer");
						
						ByteBuffer buf = encoder.getOutputBuffer(index);
						
						byte[] buff = new byte[buf.limit()-buf.position()];
						buf.get(buff);
						try {
							fout.write(buff);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							return;
						}
						
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
		}.start();
	}
	
	public Surface getInputSurface() {
		return inputSurface;
	}
}
