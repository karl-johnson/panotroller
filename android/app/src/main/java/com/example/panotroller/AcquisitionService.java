package com.example.panotroller;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.ListIterator;


public class AcquisitionService extends Service {
    // executes a list of BluetoothInstructions to perform an acquisition
    // also, if provided a Handler, will send updates about acquisition (and all BluetoothService
    // ... handler messages)
    // TODO also displays a status notification that shows progress bar and allows pause/play

    /* CONSTANTS */
    // constants for acquisition activity we're going to be sending messages to
    public final static int ACQUISITION_STATUS_UPDATE = 1;
    public final static int BLUETOOTH_STATUS_UPDATE = 2; // includes latency
    // will need to include position updates here in the future, if we want the viewport to work


    /* MEMBERS */
    // for bluetooth service
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;
    // specific to this service
    List<BluetoothInstruction> instructionList = null;
    ListIterator<BluetoothInstruction> instructionListIterator = null;
    BluetoothInstruction lastSentInstruction = null;
    Handler mExternalHandler;

    private boolean isEnabled = false; // have we been given an instruction list?
    public boolean isEnabled() {return isEnabled;}
    private boolean isRunning = true; // tracks pause/resumed
    public boolean isRunning() {return isRunning;}
    private boolean isWaitingForResponse = false;
    private boolean isFinished = false;
    public boolean isFinished() {return isFinished;}

    /* PUBLIC METHODS */

    @Override
    public void onCreate() {
        Toast.makeText(this, "Acquisition service started", Toast.LENGTH_SHORT).show();
        // bind to BluetoothService
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        mShouldUnbind = bindService(
                BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void enableAcquisition(List<BluetoothInstruction> listIn) {
        // need to get instruction list in order for us to execute it!
        instructionList = listIn;
        instructionListIterator = instructionList.listIterator();
        isEnabled = true;
    }

    public void setExternalHandler(Handler handlerIn) {mExternalHandler = handlerIn;}

    public boolean resumeAcquisition() {
        // only do things if we're not running + are enabled
        if(isEnabled && !isRunning) {
            isRunning = true;
            updateAcquisition();
            return true;
        }
        return false; // acquisition didn't resume
    }

    public void pauseAcquisition() {
        if(isRunning) {
            isRunning = false;
        }
    }

    private void updateAcquisition() {
        // only do things if we're un-paused and not still waiting on an instruction
        if(isRunning && !isWaitingForResponse) {
            // advance iterator and send next instruction
            if(instructionListIterator.hasNext()) {
                lastSentInstruction = instructionListIterator.next();
                mBluetoothService.sendInstructionViaThread(lastSentInstruction);
                isWaitingForResponse = true;

            }
            else {
                // acquisition is done! update accordingly
                isRunning = false;
                isFinished = true;
            }
            // an update was executed so notify client
            mExternalHandler.obtainMessage(ACQUISITION_STATUS_UPDATE).sendToTarget();
        }
    }

    public void restartAcquisition() {
        instructionListIterator = instructionList.listIterator();
        isFinished = false;
        isRunning = true;
        updateAcquisition();
    }

    public float getProgress() {
        // next index works because list indices start at 0
        // also when this is exactly 1.0f the acquisition is done
        return (float) instructionListIterator.nextIndex()/instructionList.size();
    }

    /* STUFF TO ALLOW CLIENTS TO BIND TO US */

    // the binder has to do with how this Service is "bound" to each client which uses it
    private final IBinder binder = new AcquisitionService.LocalBinder();

    public class LocalBinder extends Binder {
        AcquisitionService getService() {
            return AcquisitionService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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
            // begin executing instructions
            isWaitingForResponse = false; // ensure that we're not waiting to start acq.
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };

    /* HANDLER - DETERMINES WHAT WE DO WITH AN INCOMING MESSAGE FROM BLUETOOTH SERVICE */

    class AcquisitionServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case BluetoothService.CONN_STATUS_UPDATED:
                    // TODO indication of connection status in notification
                    break;
                case BluetoothService.NEW_INSTRUCTION_IN:
                    BluetoothInstruction newInstruction = (BluetoothInstruction) msg.obj;
                    // is this a response to the last instruction we sent?
                    // check by comparing instruction code against expected response code
                    // flipping first bit of android instruction gives arduino response code
                    if(newInstruction.inst == (lastSentInstruction.inst ^ 0x80)) {
                        // TODO verify data inside instruction is what we expect as well
                        isWaitingForResponse = false;
                        updateAcquisition();
                    }
                    break;
                case BluetoothService.NEW_INSTRUCTION_CORRUPTED:
                    // TODO HANDLE CORRUPTED INSTRUCTIONS (THIS IS WHERE IT WILL MATTER THE MOST)
                    break;
            }
            // pretty safe to update bluetooth bar anytime this happens
            mExternalHandler.obtainMessage(
                    BLUETOOTH_STATUS_UPDATE, mBluetoothService.getBluetoothBarInfo()).sendToTarget();
        }
    }
}