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
        Log.d("MAIN", "onCreate");
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
        // NOTE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // don't bind with getApplicationContext.bindService() all the time because then
        // activities will trip over each other due to trying to bind/unbind from same context
        mShouldUnbind = bindService(BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        // NOTE: service won't start if it's not in AndroidManifest.xml
        // mShouldUnbind = true;
    }

    public void onStart() {
        super.onStart();
        Log.d("MAIN", "onStart");

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("MAIN", "onResume");
        if(!mShouldUnbind) { // if not already bound (can be called separately from onCreate)
            Log.d("MAIN", "onResume binding attempt");
            Intent BTServiceIntent = new Intent(this, BluetoothService.class);
            mShouldUnbind = bindService(
                    BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            Log.d("MAIN", "mShouldUnbind: " + mShouldUnbind);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("MAIN", "onPause, mShouldUnbind " + mShouldUnbind);
        if(mShouldUnbind) {
            Log.d("MAIN", "Proceeding with unbinding (mShouldUnbind must be true!)");
            mShouldUnbind = false;
            unbindService(mServiceConnection);

        }
    }


    /* SERVICE CONNECTION - NEEDED TO CONNECT TO BLUETOOTH SERVICE */

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d("MAIN", "onServiceConnected");
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