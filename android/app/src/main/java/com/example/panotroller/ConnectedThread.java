package com.example.panotroller;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectedThread extends Thread {

    // thread to send and receive data over Bluetooth

    /* MEMBERS */
    private final BluetoothSocket mSocket; // connection point for device we're connected to
    private final Handler mHandler; // how to get information out of this thread
    private final InputStream mInStream; // incoming data stream from BT device
    private final OutputStream mOutStream; // outgoing data stream to BT device

    private boolean messageInProgress = false; // keep track of whether message is in progress
    private int byteIndex = 0; // keep track of location in message
    private byte[] saveArray = new byte[GeneratedConstants.MESSAGE_LENGTH];

    private long lastLatency = 0; // last calculated ping
    private long lastSentPingTime = 0; // last time a ping was sent
    private short lastSentPingData = 0;
    // number of times in a row we've sent a ping and not gotten a response within PING_PERIOD
    private int numOutstandingPings = 0;
    // set false if numOutstandingPings > MAX_PINGS
    public boolean isConnectionHealthy = false;

    /* CONSTANTS */
    public final static int MAX_PINGS = 3; // how many unanswered pings before we question our connection
    public final static int PING_PERIOD = 500; // how frequent to send pings, in ms

    /* CONSTRUCTOR */
    public ConnectedThread(BluetoothSocket socket, Handler mHandlerIn) {
        mSocket = socket;
        mHandler = mHandlerIn;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams, using temp objects because member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        mInStream = tmpIn;
        mOutStream = tmpOut;
    }

    public void run() {
        while (true) {
            // constantly look for new bytes from our InputStream
            try {
                // look for start character, start array once seen, end array once right length
                while(mInStream.available() > 0) {
                    //Log.d("BYTE","byte from Arduino");
                    byte inByte = (byte) mInStream.read();
                    if(messageInProgress) {
                        saveArray[byteIndex] = inByte; // add byte to array
                        byteIndex++;
                        if(byteIndex == GeneratedConstants.MESSAGE_LENGTH) {
                            //Log.d("MESSAGE_COMPLETE","Got to Message Completion");
                            // if our read data is now the length of a message, decode instruction
                            // and send ArduinoInstruction object via handler
                            try {
                                BluetoothInstruction newInstruction = new BluetoothInstruction(saveArray);
                                checkIfPing(newInstruction);
                                mHandler.obtainMessage(BluetoothService.NEW_INSTRUCTION_IN, newInstruction).sendToTarget();
                                Log.d("CONNECTED_THREAD","Received new instruction, sent to handler");
                            } catch (BluetoothInstruction.CorruptedInstructionException e) {
                                mHandler.obtainMessage(BluetoothService.NEW_INSTRUCTION_CORRUPTED).sendToTarget();
                            } catch (IOException e2) {
                                Log.e("BAD_ENC_MESSAGE_LENGTH",e2.getMessage());
                            }
                            byteIndex = 0; // overwrite old message
                            messageInProgress = false; // message is over
                        }
                    }
                    else if(inByte == GeneratedConstants.START_BYTE) {
                        // start message after start byte; don't need to include this in array
                        messageInProgress = true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            // handling pinging
            if(System.currentTimeMillis() > lastSentPingTime + PING_PERIOD) {
                sendPing();
            }
        }
    }

    public void writeArduinoInstruction(BluetoothInstruction inputInstruction) {
        // pretty simple to actually write data!
        //Log.d("SENDING_INSTRUCTION", "Attempting to write instruction bytes");
        byte[] sendBytes = inputInstruction.convertInstructionToBytes();
        //Log.d("BYTES_SENT","0x"+bytesToHex(sendBytes));
        try {
            mOutStream.write(sendBytes);
        } catch (IOException e) {
            Log.e("WRITE_INSTR_FAILED", "Attempt to write instruction bytes failed");
            e.printStackTrace();
        }
    }

    // debug function to print byte array in hex
    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes();
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /* Call this to shutdown the connection */

    public void disconnect() {
        if (mInStream != null) {
            try {mInStream.close();} catch (Exception e) {
                Log.e("BT_CONNECTION", "Exception thrown while closing InStream");
            }
            //mInStream = null;
        }
        if (mOutStream != null) {
            try {mOutStream.close();} catch (Exception e) {
                Log.e("BT_CONNECTION", "Exception thrown while closing OutStream");
            }
            //mOutStream = null;
        }
        if (mSocket != null) {
            try {mSocket.close();} catch (Exception e) {
                Log.e("BT_CONNECTION", "Exception thrown while closing Socket");
            }
            //mSocket = null;
        }
    }

    private void sendPing() {
        // simple helper function to send pings for us
            numOutstandingPings++;
            lastSentPingTime = System.currentTimeMillis();
            lastSentPingData = (short) (System.currentTimeMillis() % 32767);
            writeArduinoInstruction(new BluetoothInstruction(
                    GeneratedConstants.INST_PING_INT,
                    lastSentPingData, lastSentPingData));
            Log.d("OUTSTANDING_PINGS",String.valueOf(numOutstandingPings));
            if(numOutstandingPings > MAX_PINGS) {
                // update connection to "Connecting" and let BT service know of a change
                isConnectionHealthy = false;
                mHandler.obtainMessage(BluetoothService.CONN_STATUS_UPDATED).sendToTarget();
            }
    }

    private void checkIfPing(BluetoothInstruction bluetoothInstructionIn) {
        // see if an instruction we received is a ping
        // if so, update everything
        // TODO this will give false positives once every couple decades or something
        if(bluetoothInstructionIn.int1 ==  lastSentPingData) {
            // this doesn't account for us having multiple outstanding + get an old ping
            // but if we have a healthy connection, we should get the most recent one soon enough
            lastLatency = System.currentTimeMillis() - lastSentPingTime;
            Log.d("LATENCY", String.valueOf(lastLatency));
            numOutstandingPings = 0;
            // if we were unhealthy but are now healthy, need to update everyone else
            if(isConnectionHealthy = false) {
                // need to do this yucky way because order matters
                isConnectionHealthy = true;
                mHandler.obtainMessage(BluetoothService.CONN_STATUS_UPDATED).sendToTarget();
            }
            else {
                isConnectionHealthy = true;
            }
        }
    }

    public long getLastLatency() {
        return lastLatency;
    }
}

