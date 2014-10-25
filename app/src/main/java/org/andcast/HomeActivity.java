package org.andcast;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import static android.view.View.OnClickListener;


public class HomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        final Button streamButton = (Button) findViewById(R.id.stream_button);
        streamButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                launchStream();
            }
        });


        final Intent moveToSettingsActivity = new Intent(this, SettingsActivity.class);
        final Button settingsButton = (Button) findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
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

        //use settings in here to launch el streem

    }
}
