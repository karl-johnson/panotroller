package com.example.panotroller;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.UUID;

public class BluetoothService extends Service {
    // service to enable continuous bluetooth communication regardless of what activity is open
    // might eventually allow control of connection through notif without active activity

    /* CONSTANT MEMBERS */

    // constants for connectionStatus
    public final static int STATUS_DISCONNECTED = 0;
    public final static int STATUS_HALF_CONNECTED = 1;
    public final static int STATUS_CONNECTED = 2;

    // constants for mmHandler
    public final static int CONN_STATUS_UPDATED = 0;
    public final static int NEW_INSTRUCTION_IN = 1;
    public final static int NEW_INSTRUCTION_CORRUPTED = 2;

    // UUID randomly generated on 2021-09-01
    private static final UUID BTMODULEUUID = UUID.fromString("967dbd75-51b3-422d-a9af-4430bec19f57");

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
    private Handler mmHandler;
    // the binder has to do with how this Service is "bound" to each Activity which uses it
    private final IBinder binder = new LocalBinder();
    // simple variable to track whether we're connected
    public int connectionStatus = STATUS_DISCONNECTED;

    @Override
    public void onCreate() {
        Toast.makeText(this, "BT service started", Toast.LENGTH_SHORT).show();
    }

    public void setHandler(Handler handlerIn) {
        mmHandler = handlerIn;
    }

    public BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        internalBTSocket = device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        return internalBTSocket;
        // creates secure outgoing connection with BT device using UUID
    }

    public BluetoothSocket getBluetoothSocket() { return internalBTSocket; }
    public ConnectedThread createBluetoothThread() throws IOException {
        // get BT thread if exists, create if not
        if(internalBTSocket != null) {
            if (internalConnectedThread == null) {
                if (mmHandler == null) {
                    Log.e("NO_HANDLER_PASSED","No handler provided to BT service.");
                }
                internalConnectedThread = new ConnectedThread(internalBTSocket, mmHandler);
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
    public void sendInstructionViaThread(ArduinoInstruction instructionIn) {
        if (internalConnectedThread != null) {
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
        Toast.makeText(this, "BT service stopped", Toast.LENGTH_SHORT).show();
    }
}