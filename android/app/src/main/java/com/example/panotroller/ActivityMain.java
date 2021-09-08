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

public class ActivityMain extends AppCompatActivity {

    /* MEMBERS */
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;

    /* UI OBJECTS */
    private FragmentBluetoothBar mBluetoothBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothBar = (FragmentBluetoothBar) getSupportFragmentManager().findFragmentById(R.id.main_bt_bar);

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

    @Override
    public void onResume() {
        super.onResume();

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

            // start Bluetooth Bar's 1-second interval self-updating clock
            mBluetoothBar.beginUpdates(mBluetoothService);

        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBluetoothBar.stopUpdates();
        }
    };


    public void launchPanoSetup(View view) {
        Intent intent = new Intent(this, ActivityPanoSetup.class);
        startActivity(intent);
    }
}