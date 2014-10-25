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
		public void start(MediaProjection projection, CastConfiguration config) {
			/*GLHelper helper = new GLHelper();
			
			EGLSurface glSurface = helper.createOffscrenSurface(config.width, config.height);
			SurfaceTexture texture = new SurfaceTexture((int) glSurface.getNativeHandle());
			Surface surface = new Surface(texture);*/
			
			MediaCodecEncoder encoder;
			try {
				encoder = MediaCodecEncoder.createEncoder(config);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			
			projection.createVirtualDisplay("AndCast Virtual Display",
					config.width, config.height, config.dpi,
					DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
					encoder.getInputSurface(), new VirtualDisplay.Callback() {
						@Override
						public void onPaused() {
							System.out.println("onPaused");
						}
						
						@Override
						public void onResumed() {
							System.out.println("onResumed");
						}
						
						@Override
						public void onStopped() {
							System.out.println("onStopped");
						}
					}, null);
			System.out.println("Started");
			
			encoder.start();
		}
		
		public void stop() {
		}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}
	
}
