package com.example.panotroller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Binder;
import android.os.Build;
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
    public final static int CAMERA_POS_UPDATE = 3;
    // will need to include position updates here in the future, if we want the viewport to work

    // max time that we'll wait for a response after sending an instruction
    public final static int MAX_WAIT_FOR_RESPONSE_TIME = 5000;

    public final static int CAM_UPDATE_PERIOD = 100; // ms

    public final static int ONGOING_NOTIFICATION_ID = 1;
    public final static String ACQ_NOTIF_CHANNEL_ID = "PANO_ACQ";
    /* MEMBERS */
    // for bluetooth service
    private boolean mShouldUnbind = false;
    private BluetoothService mBluetoothService;
    // specific to this service
    List<BluetoothInstruction> instructionList = null;
    ListIterator<BluetoothInstruction> instructionListIterator = null;
    BluetoothInstruction lastSentInstruction = null;
    Handler mInternalHandler = new AcquisitionServiceHandler();
    Handler mExternalHandler;

    private Panorama mPanorama;
    public void setPanorama(Panorama panoramaIn) {mPanorama = panoramaIn;}
    public Panorama getPanorama() { return mPanorama;}
    private final PanographPositionConverter mPositionConverter = new PanographPositionConverter();

    private boolean isEnabled = false; // have we been given an instruction list?
    public boolean isEnabled() {return isEnabled;}
    private boolean isRunning = false; // tracks pause/resumed
    public boolean isRunning() {return isRunning;}
    private boolean isWaitingForResponse = false;
    private long lastSentInstructionTime = 0;

    private boolean isFinished = false;
    public boolean isFinished() {return isFinished;}

    private int totalPhotos = 0; // total number of photos in acquisition
    public int getTotalPhotos() {return totalPhotos;}
    private int photoProgress = 0; // number of photos which we've gotten confirmation back on
    public int getPhotosProgress() {return photoProgress;}

    /* PUBLIC METHODS */

    @Override
    public void onCreate() {
        Toast.makeText(this, "Acquisition service started", Toast.LENGTH_SHORT).show();
        // setup foreground stuff
        Intent notificationIntent = new Intent(this, ActivityPanoAcquisition.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE);
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Panotroller Acquisition";
            String description = "lol";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(ACQ_NOTIF_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Notification notification =
                    new Notification.Builder(this, ACQ_NOTIF_CHANNEL_ID)
                            .setContentTitle("Panotroller")
                            .setContentText("Acquisition ongoing")
                            .setSmallIcon(R.drawable.icon)
                            .setContentIntent(pendingIntent)
                            .setTicker("lol what is this")
                            .build();
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        }
        else {
            Log.e("ACQ_SERVICE", "Failed to start notif channel because old sdk");
        }
        // bind to BluetoothService
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        mShouldUnbind = bindService(
                BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        Log.d("ACQUISITION", "Service destroyed...");
        stopForeground(true);
        super.onDestroy();
    }

    public void enableAcquisition(List<BluetoothInstruction> listIn) {
        // need to get instruction list in order for us to execute it!
        instructionList = listIn;
        instructionListIterator = instructionList.listIterator();
        // for now, iterate through list and count number of photos in it
        // in future, if this takes a long time we can simply pass in # of photos TODO
        for(BluetoothInstruction instruction : instructionList) {
            if(instruction.inst == GeneratedConstants.INST_TRIG_PHOT) totalPhotos++;
        }
        Log.d("ACQUISITION", "Enabling acquisition w/ instruction list of length " + listIn.size() + " containing " + totalPhotos + " photos");
        isEnabled = true;

        final Handler periodicUpdatesHandler = new Handler();
        Runnable periodicUpdatesRunnable = new Runnable() {

            @Override
            public void run() {
                try{
                    mBluetoothService.sendInstructionViaThread(
                           new BluetoothInstruction(GeneratedConstants.INST_GET_POS,(short) 0, (short) 0));
                }
                catch (Exception e) {
                    // TODO: handle exception
                }
                finally{
                    //also call the same runnable to call it at regular interval
                    periodicUpdatesHandler.postDelayed(this, CAM_UPDATE_PERIOD);
                }
            }
        };
        // start the postDelayed chain
        periodicUpdatesHandler.post(periodicUpdatesRunnable);
    }

    public void setExternalHandler(Handler handlerIn) {mExternalHandler = handlerIn;}

    public boolean resumeAcquisition() {
        //Log.d("ACQUISITION", "Acquisition resume called");
        // only do things if we're not running + are enabled
        if(isEnabled && !isRunning) {
            isRunning = true;
            updateAcquisition();
            //Log.d("ACQUISITION", "Acquisition started/resumed");
            return true;
        }
        //Log.d("ACQUISITION", "Acquisition resume called but not resumed");
        return false; // acquisition didn't resume
    }

    public void pauseAcquisition() {
        //Log.d("ACQUISITION", "Acquisition pause called");
        if(isRunning) {
            //Log.d("ACQUISITION", "Acquisition paused");
            isRunning = false;
        }
    }

    private void updateAcquisition() {
        // only do things if we're un-paused and not still waiting on an instruction
        if(isRunning && !isWaitingForResponse) {
            // advance iterator and send next instruction
            if(instructionListIterator.hasNext()) {
                lastSentInstruction = instructionListIterator.next();
                //Log.d("ACQUISITION", "Progressing to next instruction: " + ConnectedThread.bytesToHex(new byte[]{lastSentInstruction.inst}));
                mBluetoothService.sendInstructionViaThread(lastSentInstruction);
                lastSentInstructionTime = System.currentTimeMillis();
                isWaitingForResponse = true;
            }
            else {
                // acquisition is done! update accordingly
                Log.d("ACQUISITION", "Acquisition done!");
                isRunning = false;
                isFinished = true;
            }
            // an update was executed so notify client (if we've been provided a handler)
            if(mExternalHandler != null) {
                mExternalHandler.obtainMessage(ACQUISITION_STATUS_UPDATE).sendToTarget();
            }
            else {
                Log.w("ACQUISITION", "Attempted to send message to null handler!");
            }
        }
        else {
            Log.d("ACQUISITION", "update() called but no action taken");
        }
    }

    public void restartAcquisition() {
        Log.d("ACQUISITION", "Acquisition restarted");
        instructionListIterator = instructionList.listIterator();
        isFinished = false;
        isRunning = true;
        photoProgress = 0;
        updateAcquisition();
    }

    public float getProgress() {
        //Log.d("ACQUISITION", "Acquisition progress called: " + instructionListIterator.nextIndex() + "/" + instructionList.size());
        // next index works because list indices start at 0
        // also when this is exactly 1.0f the acquisition is done
        return (float) instructionListIterator.nextIndex() / instructionList.size();
    }

    private void retryLastInstruction() {
        // it's important we be careful retrying instructions
        // in timelapses, really want to avoid accidentally taking pictures twice for example
        // for now, literally just re-send instruction
        Log.d("ACQ_SERVICE", "Retrying last instruction");
        Toast.makeText(this, "No inst. response, retrying", Toast.LENGTH_SHORT).show();
        mBluetoothService.sendInstructionViaThread(lastSentInstruction);
        lastSentInstructionTime = System.currentTimeMillis();
        isWaitingForResponse = true;
    }

    public BluetoothService.BluetoothBarInfo getBluetoothBarInfo() {
        if(mBluetoothService != null) {
            return mBluetoothService.getBluetoothBarInfo();
        }
        return null; // TODO BETTER BASE CASE
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
            Log.d("ACQUISITION","Acq. service connected to BT service");
            // begin executing instructions
            mBluetoothService.setHandler(mInternalHandler);
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
                    //Log.d("ACQUISITION", "Acquisition got a new instruction, inst = " + ConnectedThread.bytesToHex(new byte[]{newInstruction.inst}));
                    // is this a response to the last instruction we sent?
                    // check by comparing instruction code against expected response code
                    // flipping first bit of android instruction gives arduino response code
                    if (lastSentInstruction != null) { // don't do this if we haven't sent one yet
                        if (newInstruction.inst == (lastSentInstruction.inst ^ (byte) 0x80)) {
                            // TODO verify data inside instruction is what we expect as well
                            // increment photo counter
                            if (newInstruction.inst == GeneratedConstants.INST_TOOK_PHOT)
                                photoProgress++;
                            isWaitingForResponse = false;
                            updateAcquisition();
                        }
                        else {
                            if(isWaitingForResponse) {
                                if(System.currentTimeMillis() - lastSentInstructionTime > MAX_WAIT_FOR_RESPONSE_TIME) {
                                    // if we're getting instructions for a while but not a response to the
                                    // one that we sent, we need to do something, as a message was probably
                                    // dropped.
                                    // TODO make this timer updated by something other than pings
                                    retryLastInstruction();
                                }
                            }
                        }
                        // pass on frequent camera position updates
                        if (newInstruction.inst == GeneratedConstants.INST_GOT_POS) {
                            PointF newPositionDeg = mPositionConverter.convertStepsToDegrees(new Point(newInstruction.int1, newInstruction.int2));
                            mExternalHandler.obtainMessage(CAMERA_POS_UPDATE, newPositionDeg).sendToTarget();
                        }
                    }
                    break;
                case BluetoothService.NEW_INSTRUCTION_CORRUPTED:
                    // TODO HANDLE CORRUPTED INSTRUCTIONS (THIS IS WHERE IT WILL MATTER THE MOST)
                    break;
            }
            // pretty safe to update bluetooth bar anytime this happens
            if(mExternalHandler != null) {
                mExternalHandler.obtainMessage(
                        BLUETOOTH_STATUS_UPDATE, mBluetoothService.getBluetoothBarInfo()).sendToTarget();
            }
            else {
                Log.w("ACQUISITION", "Attempted to send message to null handler!");
            }
        }
    }

    /* THREAD TO PERIODICALLY PERFORM UPDATES/CHECKS */


}