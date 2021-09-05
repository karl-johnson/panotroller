package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    /* MEMBERS */
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;

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
        bindService(BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        // NOTE: service won't start if it's not in AndroidManifest.xml
        mShouldUnbind = true;
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mShouldUnbind) {
            unbindService(mServiceConnection);
            mShouldUnbind = false;
        }
    }

    /* SERVICE CONNECTION - NEEDED TO CONNECT TO BLUETOOTH SERVICE */

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            mBluetoothService = binder.getService();
            Log.d("SERVICE_CONNECTED","BT Service Connected");
            Log.d("BIND_TEST","bind fin, mbtservice != null: "+String.valueOf(mBluetoothService != null));
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // don't need to do anything but apparently we need to say this anyways
        }
    };


    public void launchPanoSetup(View view) {
        Intent intent = new Intent(this, PanoSetup.class);
        startActivity(intent);
        // no need to pass any bluetooth data, as it's all handled by Bluetooth Service
    }
}