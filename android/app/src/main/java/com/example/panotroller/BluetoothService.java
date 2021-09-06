package com.example.panotroller;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class BluetoothService extends Service {
    // service to enable continuous bluetooth communication regardless of what activity is open
    // might eventually allow control of connection through notif without active activity
    // the service is started in MainActivity in onStart()
    // this means startService can be called multiple times, but it's only created on the first call

    /* CONSTANT MEMBERS */

    // constants for connectionStatus
    public final static int STATUS_OFF = 0; // BT is off
    public final static int STATUS_DISCONNECTED = 1; // BT is on but disconnected
    public final static int STATUS_CONNECTING = 2; // Currently starting/verifying connection
    public final static int STATUS_CONNECTED = 3; // Connection is active and good

    // constants for mHandler
    public final static int CONN_STATUS_UPDATED = 0;
    public final static int NEW_INSTRUCTION_IN = 1;
    public final static int NEW_INSTRUCTION_CORRUPTED = 2;

    // UUID randomly generated on 2021-09-01
    //private static final UUID BTMODULEUUID = UUID.fromString("967dbd75-51b3-422d-a9af-4430bec19f57");
    // I can't connect to a device unless I use this UUID, what the fuck? TODO: ...
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /* OTHER IMPORTANT MEMBERS */

    // the BluetoothSocket is the abstraction for "where" the other device connects
    private BluetoothSocket internalBTSocket = null;
    // this is the thread which independently handles incoming and outgoing data over bluetooth
    private ConnectedThread internalConnectedThread = null;
    /*
     the active Activity must provide a handler as a way for information to be passed from the
     ConnectedThread to the Activity. The Handler is not only how information is sent, but also
     determines what is done with that information the the main Activity
     */
    private Handler mHandler;
    // the binder has to do with how this Service is "bound" to each Activity which uses it
    private final IBinder binder = new LocalBinder();

    // simple variable to track whether we're connected
    private int connectionStatus = STATUS_DISCONNECTED;
    public void setConnectionStatus(int newStatus) {
        // unfortunately we generally rely on outside code to update the connection status
        // inefficient but readable check to see if this is a valid status
        if(Arrays.asList(STATUS_OFF,STATUS_DISCONNECTED,STATUS_CONNECTING,STATUS_CONNECTED)
                .contains(newStatus)) {
            connectionStatus = newStatus;
        }
        else {
            Log.e("BAD_BT_STATUS", "setConnectionStatus() passed invalid newStatus");
        }
    }

    public int getConnectionStatus() {return connectionStatus;}

    // who are WE (this app) connected to, if we are?
    public BluetoothDevice connectedDevice = null; // track the single device

    @Override
    public void onCreate() {
        Toast.makeText(this, "BT service started", Toast.LENGTH_SHORT).show();
    }

    public void setHandler(Handler handlerIn) {
        mHandler = handlerIn;
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
                if (mHandler == null) {
                    Log.e("NO_HANDLER_PASSED","No handler provided to BT service.");
                }
                internalConnectedThread = new ConnectedThread(internalBTSocket, mHandler);
                internalConnectedThread.start();
                Log.d("GET_BT_THREAD", "Created BT Thread: " + String.valueOf(internalConnectedThread != null));
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
        if (internalConnectedThread != null) {
            internalConnectedThread.writeArduinoInstruction(instructionIn);
        }
        else {
            Log.e("SEND_W_NO_THREAD","Tried to send with no thread!");
        }
    }

    public void disconnect() {

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
        Toast.makeText(this, "BT service stopped", Toast.LENGTH_SHORT).show();
    }
}