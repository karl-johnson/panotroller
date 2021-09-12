package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.widget.Toolbar;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class ActivityPanoSetup extends AppCompatActivity {

    /* CONSTANTS */
    private final static int MAX_JOY_MOTOR_SPEED = 400;
    private final static int JOY_UPDATE_FREQUENCY = 100; // update frequency of joystick in ms
    // TODO LIST OF BUILT-IN CAMERAS


    /* MEMBERS */
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;

    /* UI OBJECTS */
    private FragmentBluetoothBar mBluetoothBar;
    private JoystickView mJoystick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // apply layout
        setContentView(R.layout.activity_pano_setup);
        // assign UI members
        mBluetoothBar = (FragmentBluetoothBar) getSupportFragmentManager().findFragmentById(
                R.id.pano_setup_bt_bar);
        mJoystick = (JoystickView) findViewById(R.id.joystick);
        // setup action bar
        setTitle("Panorama Setup");
        Toolbar thisToolbar = (Toolbar) findViewById(R.id.pano_setup_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar
        // bind to bluetooth service
    }

    public void onResume() {
        super.onResume();
        Log.d("TRY_BT_SERVICE", "Trying to connect to BT service");
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        mShouldUnbind = bindService(
                BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        Log.d("TRY_BT_SERVICE", "Past BT service code");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("PANO_SETUP", "onPause");
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
            setJoystickListeners();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBluetoothBar.stopUpdates();
        }
    };

    /* JOYSTICK METHODS */

    private void setJoystickListeners() {
        mJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                sendMotorVels((short) (MAX_JOY_MOTOR_SPEED * 0.01 * strength * Math.cos(Math.toRadians(angle))),
                        (short) (MAX_JOY_MOTOR_SPEED * 0.01 * strength * Math.sin(Math.toRadians(angle))));
            }
        }, JOY_UPDATE_FREQUENCY);
    }

    public void sendMotorVels(short XVel, short YVel) {
        // send motor velocity in 1/8 steps per second
        mBluetoothService.sendInstructionViaThread(new BluetoothInstruction(
                GeneratedConstants.INST_SET_MTR, XVel, YVel));
        Log.d("VELS_SENT", String.valueOf(XVel) + " " + String.valueOf(YVel));
    }

    /* TOOLBAR SETUP METHODS */
    @Override
    // what to do when buttons in toolbar are pressed
    public boolean onOptionsItemSelected(MenuItem item) {
        // make back button actually go back
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // use back button logic to do this for us
            return true;
        }
        else if(item.getItemId() == R.id.continue_button) {
            Intent intent = new Intent(this, ActivityPanoAcquisition.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.pano_setup_menu, menu);
        return true;
    }
}