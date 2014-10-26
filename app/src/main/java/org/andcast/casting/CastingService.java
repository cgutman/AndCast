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
        private AudioCapturer audioCap;
		private VirtualDisplay display;
		private MediaProjection projection;
		private CastConfiguration config;
        private boolean running;
        private boolean reaped;

        public void initialize(MediaProjection projection, CastConfiguration config) {
			this.projection = projection;
			this.config = config;
		}

        private void enterForeground() {
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

        private void exitForeground() {
            stopForeground(true);
        }

        public boolean isCasting() {
            return running;
        }

		public void start() throws IOException {
			// Create the H264 encoder and input surface for the virtual display
			encoder = MediaCodecEncoder.createEncoder(config);

            if (config.audioChannels != 0) {
                audioCap = new AudioCapturer(config.audioChannels);
            }

            int res = FfmpegMuxer.initializeMuxer(config.streamMuxType, config.streamUrl,
                    config.width, config.height, config.frameRate, config.iFrameIntervalSecs,
                    config.audioBitrate, config.audioChannels);
            if (res != 0) {
                projection.stop();

                encoder.release();
                encoder = null;

                if (audioCap != null) {
                    audioCap.release();
                    audioCap = null;
                }

                throw new IOException("Failed to start casting: "+res);
            }

            // Create the new virtual display
			display = projection.createVirtualDisplay("AndCast Virtual Display",
					config.width, config.height, config.dpi,
					DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
					encoder.getInputSurface(), new VirtualDisplay.Callback() {
				@Override
				public void onPaused() {
					System.out.println("onPaused");
                    pauseDataSources();
				}

				@Override
				public void onResumed() {
					System.out.println("onResumed");
                    startDataSources();
				}

				@Override
				public void onStopped() {
					System.out.println("onStop");
                    releaseDataSources();

				}
			}, null);
            if (display == null) {
                projection.stop();

                FfmpegMuxer.cleanupMuxer();

                if (audioCap != null) {
                    audioCap.release();
                    audioCap = null;
                }

                encoder.release();
                encoder = null;

                throw new IOException("Failed to create virtual display");
            }

            // Become a foreground service so we're unlikely to be killed
            enterForeground();
		}

        private void startDataSources() {
            System.out.println("startDataSources");

            if (!running) {
                if (encoder != null) {
                    encoder.start();
                }

                if (audioCap != null) {
                    audioCap.start();
                }

                running = true;
            }
        }

        private void pauseDataSources() {
            System.out.println("pauseDataSources");

            if (running) {
                if (encoder != null) {
                    encoder.stop();
                }

                if (audioCap != null) {
                    audioCap.stop();
                }

                running = false;
            }
        }

        private void releaseDataSources() {
            System.out.println("releaseDataSources");

            if (reaped) {
                return;
            }

            reaped = true;

            if (running) {
                pauseDataSources();
            }

            FfmpegMuxer.cleanupMuxer();

            if (audioCap != null) {
                audioCap.release();
                audioCap = null;
            }

            if (encoder != null) {
                encoder.release();
                encoder = null;
            }

            exitForeground();
        }

		public void stop() {
            releaseDataSources();
            projection.stop();
			display.release();
		}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}
	
}
