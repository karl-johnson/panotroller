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
import android.view.MenuItem;

public class ActivityPanoAcquisition extends AppCompatActivity {

    /* MEMBERS */
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;

    /* UI OBJECTS */
    private FragmentBluetoothBar mBluetoothBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pano_acquisition);

        mBluetoothBar = (FragmentBluetoothBar) getSupportFragmentManager().findFragmentById(
                R.id.pano_acq_bt_bar);

        // toolbar setup
        setTitle("Panorama Acquisition");
        Toolbar thisToolbar = (Toolbar) findViewById(R.id.pano_acq_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar
    }

    public void onResume() {
        super.onResume();
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        mShouldUnbind = bindService(
                BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mShouldUnbind) {
            mShouldUnbind = false;
            unbindService(mServiceConnection);
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
            // start Bluetooth Bar's 1-second interval self-updating clock
            mBluetoothBar.beginUpdates(mBluetoothService);
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBluetoothBar.stopUpdates();
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // make back button actually go back
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // use back button logic to do this for us
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}