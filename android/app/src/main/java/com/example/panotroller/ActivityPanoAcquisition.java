package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class ActivityPanoAcquisition extends AppCompatActivity {

    /* MEMBERS */
    private boolean mShouldUnbind = false;
    private AcquisitionService mAcquisitionService;

    /* UI OBJECTS */
    private FragmentBluetoothBar mBluetoothBar;
    private TextView mPhotosText;
    private TextView mTimeText;
    private TextView mRemainingText;
    private View mProgressBar;
    private TextView mProgressBarText;
    private ImageButton mPausePlayButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pano_acquisition);

        // assign UI objects
        mBluetoothBar = (FragmentBluetoothBar) getSupportFragmentManager().findFragmentById(
                R.id.pano_acq_bt_bar);
        mPhotosText = (TextView) findViewById(R.id.pano_acq_photos_text);
        mTimeText = (TextView) findViewById(R.id.pano_acq_time_text);
        mRemainingText = (TextView) findViewById(R.id.pano_acq_remaining_text);
        mProgressBar = (View) findViewById(R.id.pano_acq_progressbar);
        mProgressBarText = (TextView) findViewById(R.id.pano_acq_progressbar_text);
        mPausePlayButton = (ImageButton) findViewById(R.id.pause_play);

        // default view configuration that can't be done in xml
        // e.g. set progress bar to 100% until we're connected to Acquisition service
        mProgressBarText.setWidth(mProgressBar.getWidth());

        // toolbar setup
        setTitle("Panorama Acquisition");
        Toolbar thisToolbar = (Toolbar) findViewById(R.id.pano_acq_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar

        // assign actions to buttons
        mPausePlayButton.setOnClickListener(this::onPausePlayButtonPress);

    }

    public void onResume() {
        super.onResume();
        if(!mShouldUnbind) { // if not already bound
            // bind to acquisition service created in setup activity
            Intent AcqServiceIntent = new Intent(this, AcquisitionService.class);
            mShouldUnbind = bindService(
                    AcqServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mShouldUnbind) {
            mShouldUnbind = false;
            unbindService(mServiceConnection);
        }
    }

    private void onPausePlayButtonPress(View view) {
        // pause/resume acquisition and update image button drawable
        if(mAcquisitionService.isFinished()) {
            // if our acquisition service is finished, play button should restart it
            mAcquisitionService.restartAcquisition();
        }
        else {
            if(mAcquisitionService.isRunning()) {
                // the acquisition is running so we need to pause it and update button to "play"
                mAcquisitionService.pauseAcquisition();
                mPausePlayButton.setBackground(getDrawable(R.drawable.ic_baseline_play_arrow));
            }
            else {
                // the acquisition is paused so we need to start it and update button to "pause"
                mAcquisitionService.resumeAcquisition();
                mPausePlayButton.setBackground(getDrawable(R.drawable.ic_baseline_pause));
            }
        }
    }

    /* SERVICE CONNECTIONS - NEEDED TO CONNECT TO SERVICE */
    // IMPORTANT NOTE! Unlike all other Activities, this one doesn't bind to BluetoothService!
    // Instead, we bind to AcquisitionService, which binds to BluetoothService for us
    // The reason for this is because AcquisitionService *really* needs to bind to BluetoothService
    // but this activity only needs to do so for the bluetooth status bar (e-stops are in hardware)
    // As such, we can just pass BluetoothService's connection status updates through Acq. service
    // This is a bit messy, but if we consider AcquisitionService to grouped together with this
    // ... activity as a client of BluetoothService, it's not totally horrible structure-wise

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AcquisitionService.LocalBinder binder = (AcquisitionService.LocalBinder) service;
            mAcquisitionService = binder.getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // make back button actually go back
        if (item.getItemId() == android.R.id.home) {
            // TODO show popup about stopping acquisition
            // TODO stop AcquisitionService
            // first have to unbind prior to stopping service
            if (mShouldUnbind) {
                mShouldUnbind = false;
                unbindService(mServiceConnection);
            }
            stopService(new Intent(this, AcquisitionService.class));
            // now go to previous activity
            onBackPressed(); // use back button logic to do this for us
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* HANDLER - WHAT TO DO WITH ACQUISITION AND BLUETOOTH SERVICE UPDATES */

    class PanoAcquisitionHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case AcquisitionService.ACQUISITION_STATUS_UPDATE:
                    if(mAcquisitionService.isFinished()) {
                        // update UI to indicate panorama is finished
                        mProgressBarText.setWidth(mProgressBar.getWidth());
                        mProgressBarText.setBackgroundColor(getColor(R.color.blue_connected));
                        mProgressBarText.setText("Acquisition Complete");
                        // also set play button to look like a replay button
                        mPausePlayButton.setBackground(getDrawable(R.drawable.ic_baseline_replay));
                    }
                    else {
                        // update progress bar and text fields with normal progress
                        float newProgress = mAcquisitionService.getProgress();
                        mProgressBarText.setWidth((int) newProgress*mProgressBar.getWidth());
                        int newProgressPercent = (int) (100*newProgress);
                        mProgressBarText.setText(newProgressPercent + "%");
                    }
                    break;
                case AcquisitionService.BLUETOOTH_STATUS_UPDATE:
                    mBluetoothBar.update((BluetoothService.BluetoothBarInfo) msg.obj);
                    break;
            }
        }
    }
}