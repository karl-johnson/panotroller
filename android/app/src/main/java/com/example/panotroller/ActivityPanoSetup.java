package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationSet;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.Toolbar;

import java.util.List;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class ActivityPanoSetup extends AppCompatActivity {

    /* CONSTANTS */
    private final static int MAX_JOY_MOTOR_SPEED = 400;
    private final static int JOY_UPDATE_FREQUENCY = 100; // update period of joystick in ms
    private final static int DIALOG_MARGIN = 40;

    // DEBUG: HARDCODED PANORAMA AND CAMERAS
    private Panorama.PanoramaCamera testCamera = Panorama.builtInCameras.get("CANON_5D_MARK_II");
    private PanographPositionConverter mPositionConverter = new PanographPositionConverter();

    /* MEMBERS */
    private boolean mShouldUnbind = false;
    // are we waiting for a position back from bluetooth connection to add to pano?
    private boolean mHasOutstandingAdd = false;
    private boolean mHasOutstandingRemove = false;

    private BluetoothService mBluetoothService;
    private Handler mHandler = new PanoSetupHandler();
    private Panorama mPanorama; // panorama being edited in this activity


    /* UI OBJECTS */
    private FragmentBluetoothBar mBluetoothBar;
    private JoystickView mJoystick;
    private ImageButton mAddButton;
    private ImageButton mRemoveButton;
    private ImageButton mSettingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // apply layout
        setContentView(R.layout.activity_pano_setup);
        // assign UI members
        mBluetoothBar = (FragmentBluetoothBar) getSupportFragmentManager().findFragmentById(
                R.id.pano_setup_bt_bar);
        mJoystick = (JoystickView) findViewById(R.id.joystick);
        mAddButton = (ImageButton) findViewById(R.id.add_point);
        mRemoveButton = (ImageButton) findViewById(R.id.remove_point);
        mSettingsButton = (ImageButton) findViewById(R.id.pano_settings);

        // set up button actions
        mAddButton.setOnClickListener(this::onAddButton);
        mRemoveButton.setOnClickListener(this::onRemoveButton);
        mSettingsButton.setOnClickListener(this::onSettingsButton);

        // setup action bar
        setTitle("Panorama Setup");
        Toolbar thisToolbar = (Toolbar) findViewById(R.id.pano_setup_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar
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
            mBluetoothService.setHandler(mHandler);
            mBluetoothBar.update(mBluetoothService.getBluetoothBarInfo());
            Log.d("SERVICE_CONNECTED","BT Service Connected");
            // set device to velocity mode
            mBluetoothService.sendInstructionViaThread(new BluetoothInstruction(GeneratedConstants.INST_SET_MODE,(short) 0, (short) 0));
            setJoystickListeners();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };

    /* BUTTON METHODS */
    private void onAddButton(View view) {
        // set "waiting for add" flag and request current position from remote device
        // only do this if we're not already waiting for either add/remove (e.g. double press)
        if(!mHasOutstandingAdd && !mHasOutstandingRemove) {
            mHasOutstandingAdd = true;
            mBluetoothService.sendInstructionViaThread(new BluetoothInstruction(GeneratedConstants.INST_GET_POS, (short) 0, (short) 0));
            // actual adding of point is done in Handler when we get position back
        }
    }

    private void onRemoveButton(View view) {
        if(!mHasOutstandingRemove && !mHasOutstandingAdd) {
            mHasOutstandingRemove = true;
            mBluetoothService.sendInstructionViaThread(new BluetoothInstruction(GeneratedConstants.INST_GET_POS, (short) 0, (short) 0));
        }
    }

    private void onSettingsButton(View view) {
        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.dialog_pano_settings, null);

        int width = LinearLayout.LayoutParams.MATCH_PARENT;
        int height = LinearLayout.LayoutParams.MATCH_PARENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        // TODO change to startActivityForResult https://stackoverflow.com/questions/35264383/how-to-retrieve-values-from-popup-to-main
        
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
    }

    /* JOYSTICK METHODS */

    private void setJoystickListeners() {
        mJoystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // flip Y axis so DOWN means +Y (to align with Android.Point sign convention)
                sendMotorVels((short) (MAX_JOY_MOTOR_SPEED * 0.01 * strength * Math.cos(Math.toRadians(angle))),
                        (short) (MAX_JOY_MOTOR_SPEED * -0.01 * strength * Math.sin(Math.toRadians(angle))));
            }
        }, JOY_UPDATE_FREQUENCY);
    }

    public void sendMotorVels(short XVel, short YVel) {
        // send motor velocity in 1/8 steps per second
        mBluetoothService.sendInstructionViaThread(new BluetoothInstruction(
                GeneratedConstants.INST_SET_MTR, XVel, YVel));
        //Log.d("VELS_SENT", String.valueOf(XVel) + " " + String.valueOf(YVel));
    }

    /* TOOLBAR SETUP METHODS */
    @Override
    // what to do when buttons in toolbar are pressed
    public boolean onOptionsItemSelected(MenuItem item) {
        // make back button actually go back
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // use back button logic to do this for us

            // stop acquisition service - this i
            return true;
        }
        else if(item.getItemId() == R.id.continue_button) {
            // TODO indicate loading, because this may take noticeable time
            Log.d("ACQUISITION_SETUP", "Starting Acquisition service...");
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
            Log.d("ACQUISITION_SETUP", "Acquisition service connected");
            // This is called in the process of launching the acquisition activity
            AcquisitionService.LocalBinder binder = (AcquisitionService.LocalBinder) service;
            AcquisitionService acquisitionService = binder.getService();
            // now that we've started and bound to the new acquisitionService,
            // generate instruction list
            List<BluetoothInstruction> exportInstructionList = mPanorama.generateInstructionList(mPositionConverter);
            // NOTE: if you're having issues with null instruction list, look here
            acquisitionService.enableAcquisition(exportInstructionList);
            // launch next activity
            Log.d("ACQUISITION_SETUP", "Attempting to launch acquisition activity");
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
            if(msg.what == BluetoothService.NEW_INSTRUCTION_IN) {
                BluetoothInstruction newInstruction = (BluetoothInstruction) msg.obj;
                if(newInstruction.inst == GeneratedConstants.INST_GOT_POS) {
                    if(mHasOutstandingAdd) {
                        // get position (in steps) out of instruction
                        Point newPosition = new Point(newInstruction.int1, newInstruction.int2);
                        Log.d("PANORAMA", "Adding point " + newPosition.toString());
                        // convert this step position to degree pos and add to panorama
                        mPanorama.addPoint(mPositionConverter.convertStepsToDegrees(newPosition));
                        mHasOutstandingAdd = false;
                        // haptic feedback on success
                        mAddButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    }
                    else if(mHasOutstandingRemove) {
                        Point newPosition = new Point(newInstruction.int1, newInstruction.int2);
                        Log.d("PANORAMA", "Removing point " + newPosition.toString());
                        // convert step position and remove nearest point in panorama
                        mPanorama.removeNearestPoint(mPositionConverter.convertStepsToDegrees(newPosition));
                        mHasOutstandingRemove = false;
                        mRemoveButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    }
                }
            }
            // any new status or instruction in gives us reason to update status bar
            // may want to add a timer in the future as to prevent this from happening too much
            if(msg.what == BluetoothService.CONN_STATUS_UPDATED || msg.what == BluetoothService.NEW_INSTRUCTION_IN) {
                mBluetoothBar.update(mBluetoothService.getBluetoothBarInfo());
            }
        }
    }
}