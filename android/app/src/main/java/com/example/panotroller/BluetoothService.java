package com.example.panotroller;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

public class BluetoothService extends Service {
    // service to enable continuous bluetooth communication regardless of what activity is open
    // might eventually allow control of connection through notification without active activity
    // the service is started in MainActivity in onStart()
    // this means startService can be called multiple times, but it's only created on the first call

    /* CONSTANT MEMBERS */

    // constants for connectionStatus
    public final static int STATUS_OFF = 0; // BT is off
    public final static int STATUS_DISCONNECTED = 1; // BT is on but disconnected
    public final static int STATUS_CONNECTING = 2; // Currently starting/verifying connection
    public final static int STATUS_CONNECTED = 3; // Connection is active and good

    // constants for mHandler
    public final static int CONN_STATUS_UPDATED = 0; // used anytime latency info updated
    public final static int NEW_INSTRUCTION_IN = 1;
    public final static int NEW_INSTRUCTION_CORRUPTED = 2;
    public final static int BAT_STATUS_UPDATED = 3;

    // UUID randomly generated on 2021-09-01
    //private static final UUID BTMODULEUUID = UUID.fromString("967dbd75-51b3-422d-a9af-4430bec19f57");
    // I can't connect to a device unless I use this UUID, what the fuck? TODO: ...
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /* OTHER IMPORTANT MEMBERS */

    private final Random mRandom = new Random();

    // the BluetoothSocket is the abstraction for "where" the other device connects
    private BluetoothSocket internalBTSocket = null;
    public BluetoothAdapter mBTAdapter = BluetoothAdapter.getDefaultAdapter();
    // this is the thread which independently handles incoming and outgoing data over bluetooth
    private ConnectedThread internalConnectedThread = null;

    // all incoming data from the bluetooth stream goes through us before being sent back:
    // ConnectedThread --(internalHandler)--> BluetoothService --(externalHandler)--> Activity
    // Handler for getting information from ConnectedThread to this BluetoothService
    private Handler internalHandler = new BluetoothServiceHandler();
    // Handler for forwarding information from us (BluetoothService) to whoever wants it
    private Handler externalHandler = null;

    // the binder has to do with how this Service is "bound" to each Activity which uses it
    private final IBinder binder = new LocalBinder();

    public BluetoothInstruction lastSentInstruction = null; // last instruction sent to Arduino
    //public long lastSentInstructionTime = 0; // time at which last instruction was sent to Arduino
    //public long lastLatency = 0; // last calculated latency

    // who are WE (this app) connected to, if we are?
    public BluetoothDevice connectedDevice = null; // track the single device

    // simple variable to track whether we're connected
    private int connectionStatus = STATUS_DISCONNECTED;
    public void setConnectionStatus(int newStatus) {
        // unfortunately we generally rely on outside code to update the connection status
        // inefficient but readable check to see if this is a valid status
        if(Arrays.asList(STATUS_OFF,STATUS_DISCONNECTED,STATUS_CONNECTING,STATUS_CONNECTED)
                .contains(newStatus)) {
            connectionStatus = newStatus;
            if(newStatus == STATUS_OFF || newStatus == STATUS_DISCONNECTED) {
                connectedDevice = null; // neither of these should have a valid connectedDevice
            }
        }
        else {
            Log.e("BAD_BT_STATUS", "setConnectionStatus() passed invalid newStatus");
        }
    }

    public int getConnectionStatus() {return connectionStatus;}

    @Override
    public void onCreate() {
        Toast.makeText(this, "BT service started", Toast.LENGTH_SHORT).show();
        // register broadcast receiver, using an intent filter we make for it
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        // https://stackoverflow.com/questions/30222409/android-broadcast-receiver-bluetooth-events-catching
        registerReceiver(bluetoothLostConnection, filter);
    }

    public void setHandler(Handler handlerIn) {
        externalHandler = handlerIn;
    }

    public BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(device != null) {
            internalBTSocket = device.createRfcommSocketToServiceRecord(BTMODULEUUID);
            return internalBTSocket;
        }
        else throw new IOException("Null device passed into createBluetoothSocket");
        // creates secure outgoing connection with BT device using UUID
    }

    public BluetoothSocket getBluetoothSocket() { return internalBTSocket; }
    public ConnectedThread createBluetoothThread() throws IOException {
        // get BT thread if exists, create if not
        if(internalBTSocket != null) {
            if (internalConnectedThread == null) {
                if (externalHandler == null) {
                    Log.w("NO_HANDLER_PASSED","No external handler provided to BT service.");
                }
                internalConnectedThread = new ConnectedThread(internalBTSocket, internalHandler);
                internalConnectedThread.start();
                Log.d("GET_BT_THREAD",
                        "Created BT Thread: " + String.valueOf(internalConnectedThread != null));
                Log.d("BT_THREAD_ALIVE",String.valueOf(internalConnectedThread.isAlive()));
            }
        }
        else {
            throw new IOException("No Bluetooth Socket to Create Thread");
        }
        return internalConnectedThread;
    }
    public ConnectedThread getBluetoothThread() {return internalConnectedThread; }

    public void sendInstructionViaThread(BluetoothInstruction instructionIn) {
        // sends the given instruction using the BT thread, if it exists
        // this must be passed through us (the service) so we can calculate latency and also
        // update the lastSentInstruction properly
        if (internalConnectedThread != null) {
            lastSentInstruction = instructionIn;
            //lastSentInstructionTime = System.currentTimeMillis();
            internalConnectedThread.writeArduinoInstruction(instructionIn);
        }
        else {
            Log.e("SEND_W_NO_THREAD","Tried to send with no thread!");
        }
    }

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    @Override
    public void onDestroy() {
        // destroy bluetooth socket

        // unregister broadcast receiver
        unregisterReceiver(bluetoothLostConnection);
        Toast.makeText(this, "BT service stopped", Toast.LENGTH_SHORT).show();
    }

    /* BLUETOOTH SERVICE HANDLER */

    class BluetoothServiceHandler extends Handler {
        // what we do with messages coming from ConnectedThread
        @Override
        public void handleMessage(Message msg) {
            //Log.d("BL_SERVICE_HANDLER", "Bluetooth Service handler called!");
            // for now, only need to look at timing of message received to compute latency
            // eventually may want to use this to auto re-send corrupted messages etc.
            if(msg.what == CONN_STATUS_UPDATED) {
                // check status of bluetooth objects and update clients with new status
                checkConnectionStatus();
                // no need to send along what ConnectedThread told us - we just sent an update
            }
            // all other message types can simply be forwarded on
            else {
                if(msg.what == NEW_INSTRUCTION_IN) {
                    if(connectionStatus != STATUS_CONNECTED) {
                        checkConnectionStatus();
                    }
                }
                if(externalHandler != null) externalHandler.sendMessage(Message.obtain(msg));
                else Log.w("MESSAGE_NOT_SENT",
                        "Message didn't exit BTService because externalHandler is null!");
            }

        }
    }

    public void setStatusConnecting() {
        // there is ONE circumstance where we want an external client to update the status
        // want the status to change to "connecting" ASAP after a device is selected
        connectionStatus = STATUS_CONNECTING;
    }

    public void checkConnectionStatus() {
        // this method allows this service to fully maintain its own connection status
        // in some situations clients need to call this manually for the most up-to-date info
        // if the adapter is disabled, the user has turned off bluetooth
        Log.d("BT_SERVICE", "checkConnectionStatus()");
        if(!mBTAdapter.isEnabled()) {
            connectionStatus = STATUS_OFF;
        }
        else if(internalConnectedThread != null) {
            if(internalConnectedThread.isConnectionHealthy) {
                // if we have a healthy connection, we're connected
                connectionStatus = STATUS_CONNECTED;
            }
            else {
                // if the thread exists but we have not yet confirmed that we can talk to the
                // Arduino on the other end, show it as "CONNECTING"
                connectionStatus = STATUS_CONNECTING;
            }
        }
        else {
            // no thread means we must be disconnected
            connectionStatus = STATUS_DISCONNECTED;
        }
        if(externalHandler != null) {
            // for safety, if we called this, we should update any clients bound to this service
            externalHandler.obtainMessage(BluetoothService.CONN_STATUS_UPDATED).sendToTarget();
        }
        else Log.w("MESSAGE_NOT_SENT",
                "Message didn't exit BTService because externalHandler is null!");
    }

    /* BLUETOOTH BAR HELPERS */

    public class BluetoothBarInfo {
        // way to localize data sent to bluetooth bar fragment when it is updated
        // doing it like this allows to expand what info is sent in the future more easily
        public int status;
        public long latency;
        public double[] voltages;
        public BluetoothBarInfo(int statusIn, long latencyIn, double[] voltagesIn) {
            status = statusIn; latency = latencyIn; voltages = null;
            if(voltagesIn != null) voltages = voltagesIn.clone();
        }
    }

    // simple way for any activity to quickly send data to update bluetooth bar fragment
    public BluetoothBarInfo getBluetoothBarInfo() {
        long lastLatency = -1;
        double[] currentVoltages = null; // dangerous :/
        if(internalConnectedThread != null) {
            lastLatency = internalConnectedThread.getLastLatency();
            currentVoltages = internalConnectedThread.getCellVoltages();
        }
        return new BluetoothBarInfo(connectionStatus, lastLatency, currentVoltages);
    }

    /* BROADCAST RECEIVING STUFF */

    private final BroadcastReceiver bluetoothLostConnection = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("BT_BROADCAST","Bluetooth broadcast caught");
            if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                Log.d("BT_DISCONNECTED","Bluetooth disconnect broadcast triggered");
                // we received a broadcast that the bluetooth connection changed, and see that it is now off
                // therefore we are disconnected - update status to reflect this and inform rest of app
                stopAllBluetooth();
            }
        }
    };

    public void stopAllBluetooth() {
        setConnectionStatus(STATUS_DISCONNECTED);
        // TODO PROBABLY NEED TO KILL MORE THINGS HERE
        if(internalConnectedThread != null) {
            internalConnectedThread.interrupt();
            internalConnectedThread = null;
        }

        try{
            if(internalBTSocket != null) internalBTSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        internalBTSocket = null;
        // if we have an external handler, tell it that the status has updated
        if(externalHandler != null)
            externalHandler.obtainMessage(
                    BluetoothService.CONN_STATUS_UPDATED,
                    BluetoothService.STATUS_DISCONNECTED, 0).sendToTarget();
    }
}