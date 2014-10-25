package org.andcast.casting;

import java.io.IOException;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.IBinder;

import org.andcast.MainActivity;
import org.andcast.R;

public class CastingService extends Service {

	private Binder binder = new CastingBinder();

    private static final int NOTIFICATION_ID = 30;
	
	public class CastingBinder extends Binder {
		private MediaCodecEncoder encoder;
		private VirtualDisplay display;
		private MediaProjection projection;
		private CastConfiguration config;
		
		public void initialize(MediaProjection projection, CastConfiguration config) {
			this.projection = projection;
			this.config = config;
		}

        public void enterForeground() {
            Intent intent = new Intent(CastingService.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendIntent = PendingIntent.getActivity(CastingService.this, 0, intent, 0);

            Notification.Builder builder = new Notification.Builder(CastingService.this);
            builder .setTicker("AndCast Running")
                    .setContentTitle("AndCast Running")
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentIntent(pendIntent)
                    .setContentText("AndCast is currently casting your screen");

            startForeground(NOTIFICATION_ID, builder.build());
        }

        public void exitForeground() {
            stopForeground(true);
        }

		public void start() throws IOException {
			// Create the H264 encoder and input surface for the virtual display
			encoder = MediaCodecEncoder.createEncoder(config);

            int res = FfmpegMuxer.initializeMuxer(config.streamMuxType, config.streamUrl,
                    config.width, config.height, config.frameRate, config.iFrameIntervalSecs);
            if (res != 0) {
                encoder.release();
                encoder = null;

                throw new IOException("Failed to start casting");
            }

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
                    FfmpegMuxer.cleanupMuxer();

					encoder.release();
                    encoder = null;

                    exitForeground();
				}
			}, null);
            if (display == null) {
                FfmpegMuxer.cleanupMuxer();

                encoder.release();
                encoder = null;

                throw new IOException("Failed to create virtual display");
            }

            // Become a foreground service so we're unlikely to be killed
            enterForeground();
		}

		public void stop() {
			// This will trigger the rest of the cleanup process
			display.release();
		}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}
	
}
