package org.andcast.casting;

import java.io.IOException;

import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.IBinder;

public class CastingService extends Service {

	private Binder binder = new CastingBinder();
	
	public class CastingBinder extends Binder {
		private MediaCodecEncoder encoder;
		private VirtualDisplay display;
		private MediaProjection projection;
		private CastConfiguration config;
		
		public void initialize(MediaProjection projection, CastConfiguration config) {
			this.projection = projection;
			this.config = config;
		}

		public void start() throws IOException {
			// Create the H264 encoder and input surface for the virtual display
			encoder = MediaCodecEncoder.createEncoder(config);
			
			// Create the new virtual display
			display = projection.createVirtualDisplay("AndCast Virtual Display",
					config.width, config.height, config.dpi,
					DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
					encoder.getInputSurface(), new VirtualDisplay.Callback() {
				@Override
				public void onPaused() {
					System.out.println("onPaused");
					encoder.stop();
				}

				@Override
				public void onResumed() {
					System.out.println("onResumed");
					encoder.start();
				}

				@Override
				public void onStopped() {
					System.out.println("onStop");
					encoder.release();
				}
			}, null);
		}

		public void stop() {
			// This will stop the encoder too
			display.release();
		}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}
	
}
