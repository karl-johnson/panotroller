package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class ActivityPanoSetup extends AppCompatActivity {

    /* CONSTANTS */
    private final static int MAX_JOY_MOTOR_SPEED = 400;
    private final static int JOY_UPDATE_FREQUENCY = 100; // update frequency of joystick in ms
    // TODO LIST OF BUILT-IN CAMERAS

    // DEBUG: HARDCODED PANORAMA AND CAMERAS
    private Panorama.PanoramaCamera testCamera = Panorama.builtInCameras.get("CANON_5D_MARK_II");
    private Panorama testPanorama = new Panorama(new RectF(0,30.0f,30.0f,0));
    private PanographPositionConverter mPositionConverter = new PanographPositionConverter();

    /* MEMBERS */
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;
    private Panorama mPanorama = testPanorama; // panorama being edited in this activity


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
            setJoystickListeners();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
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
            // TODO indicate loading, because this may take noticeable time

            // start acquisition service
            Intent AcqServiceIntent = new Intent(this, AcquisitionService.class);
            startService(AcqServiceIntent);
            // bind to service so we can give it the instruction list
            mShouldUnbind = bindService(AcqServiceIntent, mAcquisitionServiceConnection, Context.BIND_AUTO_CREATE);
            // since this binding is asynchronous, the rest of the pano acquisition launching
            // is found in mAcquisitionServiceConnection.onServiceConnected()
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* SERVICE CONNECTION - NEEDED TO CONNECT TO ACQUISITION SERVICE */

    private ServiceConnection mAcquisitionServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called in the process of launching the acquisition activity
            AcquisitionService.LocalBinder binder = (AcquisitionService.LocalBinder) service;
            AcquisitionService acquisitionService = binder.getService();
            // now that we've started and bound to the new acquisitionService,
            // generate instruction list
            List<BluetoothInstruction> exportInstructionList = mPanorama.generateInstructionList(mPositionConverter);
            // NOTE: if you're having issues with null instruction list, look here
            acquisitionService.enableAcquisition(exportInstructionList);

            // launch next activity
            // TODO THIS MIGHT JUST NOT WORK LMAO
            Intent intent = new Intent(getApplicationContext(), ActivityPanoAcquisition.class);
            startActivity(intent);
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.pano_setup_menu, menu);
        return true;
    }

    /* HANDLER - DETERMINES WHAT WE DO WITH AN INCOMING MESSAGE FROM BLUETOOTH SERVICE */
    class PanoSetupHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            // any new status or instruction in gives us reason to update status bar
            // may want to add a timer in the future as to prevent this from happening too much
            if(msg.what == BluetoothService.CONN_STATUS_UPDATED || msg.what == BluetoothService.NEW_INSTRUCTION_IN) {
                mBluetoothBar.update(mBluetoothService.getBluetoothBarInfo());
            }
        }
    }
}