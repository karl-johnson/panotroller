package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    /* MEMBERS */
    private boolean mShouldUnbind = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // configure action bar
        Toolbar thisToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setLogo(R.drawable.actionbar);
        getSupportActionBar().setDisplayUseLogoEnabled(true);

        // define button actions
        findViewById(R.id.panoramaModeButton).setOnClickListener(this::launchPanoSetup);


        // start BluetoothService
        // though onCreate is called whenever MainActivity is re-opened,
        // startService only creates a new service on the very first call
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        startService(BTServiceIntent);
        bindService(BTServiceIntent, connection, Context.BIND_AUTO_CREATE);
        mShouldUnbind = true;
    }

    public void onPause() {
        if (mShouldUnbind) {
            unbindService(connection);
            mShouldUnbind = false;
        }
    }

    public void launchPanoSetup(View view) {
        Intent intent = new Intent(this, PanoSetup.class);
        startActivity(intent);
        // no need to pass any bluetooth data, as it's all handled by Bluetooth Service
    }
}