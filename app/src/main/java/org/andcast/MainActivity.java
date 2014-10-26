package org.andcast;

import java.io.IOException;

import org.andcast.casting.CastConfiguration;
import org.andcast.casting.CastingService;
import org.andcast.casting.CastingService.CastingBinder;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

public class MainActivity extends Activity {
	
	private CastingBinder binder;
	private MediaProjectionManager mgr;
    private final static String BASE_TWITCH_URL = "rtmp://live.twitch.tv/app/";
    private final static String BASE_YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2/";

	private ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			binder = (CastingBinder) service;
            ImageButton startButton = (ImageButton) MainActivity.this.findViewById(R.id.start_button);
            if (binder.isCasting()) {
                startButton.setImageResource(R.drawable.button_on);
            } else {
                startButton.setImageResource(R.drawable.button);
            }
        }

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
			
		}
		
	};

    // Gets the output string (RTMP or filename based on the preferences)
    private String getOutputUrl() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPref.getBoolean("checkbox_twitch", false)) {
            String twitchStreamkey = sharedPref.getString("twitch_stream_key", "");
            if (twitchStreamkey.isEmpty()) {
                return null;
            } else {
                return BASE_TWITCH_URL + twitchStreamkey;
            }
        } else if (sharedPref.getBoolean("checkbox_youtube", false)) {
            String youtubeStreamKey = sharedPref.getString("youtube_stream_key", "");
            if (youtubeStreamKey.isEmpty()) {
                return null;
            } else {
                return BASE_YOUTUBE_URL + youtubeStreamKey;
            }
        } else if (sharedPref.getBoolean("checkbox_sdcard", true)) {
            String folderLocation = sharedPref.getString("sdcard_folder", "");
            if (folderLocation.isEmpty()) {
                return null;
            } else {
                return folderLocation;
            }
        }
        return null;
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)  
	{  
		super.onActivityResult(requestCode, resultCode, data);

		System.out.println("Got result: "+resultCode);

		CastConfiguration config = new CastConfiguration();
        config.streamMuxType = "flv";
        config.streamUrl = getOutputUrl();

        if (config.streamUrl == null) {
            // This means shit broke so lets throw the users some fancy dialog box thingy
        }


        config.width = 1280;
		config.height = 720;
		config.dpi = 100;
		config.bitrate = 1 * 1000 * 1000;
		config.frameRate = 30;
		config.iFrameIntervalSecs = 1;

        config.audioBitrate = 0;
        config.audioChannels = 0;
		
		MediaProjection mediaProj = mgr.getMediaProjection(resultCode, data);
		if (mediaProj == null) {
            // TODO: Display a dialog here
			return;
		}
		
		binder.initialize(mediaProj, config);
		try {
			binder.start();
            ImageButton startButton = (ImageButton) MainActivity.this.findViewById(R.id.start_button);
            startButton.setImageResource(R.drawable.button_on);
		} catch (IOException e) {
            // TODO: Display a dialog here
			e.printStackTrace();
			return;
		}
	}  

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mgr = (MediaProjectionManager) MainActivity.this.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        bindService(new Intent(this, CastingService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);


        setContentView(R.layout.activity_home);

        final ImageButton streamButton = (ImageButton) findViewById(R.id.start_button);
        streamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!binder.isCasting() ) {
                    launchStream();
                }
                else{
                    endStream();
                }
            }
        });


        final Button settingsButton = (Button) findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent moveToSettingsActivity = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(moveToSettingsActivity);
            }
        });
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void launchStream() {

        System.out.println("Starting capture intent");
        MainActivity.this.startActivityForResult(mgr.createScreenCaptureIntent(), 1);

    }


    public void endStream() {
        if(binder.isCasting()) {
            ImageButton startButton = (ImageButton) MainActivity.this.findViewById(R.id.start_button);
            startButton.setImageResource(R.drawable.button);
            binder.stop();
        }
    }
}
