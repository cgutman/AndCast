package org.andcast.casting;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class AudioCapturer {

    private AudioRecord record;
    private Thread recordingThread;

    public AudioCapturer(int channels) {

        if (channels == 1) {
            channels = AudioFormat.CHANNEL_IN_MONO;
        }
        else if (channels == 2) {
            channels = AudioFormat.CHANNEL_IN_STEREO;
        }
        else {
            throw new RuntimeException("Invalid channel count");
        }

        record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 44100, channels,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(44100, channels, AudioFormat.ENCODING_PCM_16BIT));
    }

    public void start() {
        record.startRecording();

        recordingThread = new Thread() {
            @Override
            public void run() {
                byte[] buffer = new byte[FfmpegMuxer.getRequiredAudioBufferSize()];

                for (;;) {
                    int length;

                    length = record.read(buffer, 0, buffer.length);
                    if (length != 0) {
                        int ret = FfmpegMuxer.submitAudioFrame(buffer, length, (long) 0);
                        System.out.println("Audio "+length+" ret: "+ret);
                    }
                }
            }
        };

        recordingThread.start();
    }

    public void stop() {
        record.stop();

        if (recordingThread != null) {
            recordingThread.interrupt();

            try {
                recordingThread.join();
            } catch (InterruptedException e) {}
        }
    }

    public void release() {
        record.release();
    }

}
