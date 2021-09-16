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

import java.util.List;
import java.util.ListIterator;


public class AcquisitionService extends Service {
    // executes a list of BluetoothInstructions to perform an acquisition
    // also displays a status notification that shows progress bar and allows pause/play

    /* MEMBERS */
    // for bluetooth service
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;
    // specific to this service
    List<BluetoothInstruction> instructionList;
    ListIterator<BluetoothInstruction> instructionListIterator;
    BluetoothInstruction lastSentInstruction = null;

    private boolean isRunning = true; // tracks pause/resumed
    private boolean isWaitingForResponse = false;

    // the binder has to do with how this Service is "bound" to each client which uses it
    private final IBinder binder = new AcquisitionService.LocalBinder();

    public AcquisitionService(List<BluetoothInstruction> listIn) {
        instructionList = listIn;
        instructionListIterator = instructionList.listIterator();
    }

    /* PUBLIC METHODS */
    @Override
    public void onCreate() {
        // bind to BluetoothService
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        mShouldUnbind = bindService(
                BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        resumeAcquisition();
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
            resumeAcquisition();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };

    public void resumeAcquisition() {
        isRunning = true;
        updateAcquisition();
    }

    public void pauseAcquisition() {
        isRunning = false;
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
                // acquisition is done! automatically pause it
                // TODO add logic to close service
                isRunning = false;
            }
        }
    }

    public float getProgress() {
        // next index works because list indices start at 0
        // also when this is exactly 1.0f the acquisition is done
        return (float) instructionListIterator.nextIndex()/instructionList.size();
    }

    public class LocalBinder extends Binder {
        AcquisitionService getService() {
            return AcquisitionService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

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
        }
    }
}