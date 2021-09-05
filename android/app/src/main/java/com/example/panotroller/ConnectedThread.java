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
    private BluetoothSocket mSocket; // connection point for device we're connected to
    private final Handler mHandler; // how to get information out of this thread
    private InputStream mInStream; // incoming data stream from BT device
    private OutputStream mOutStream; // outgoing data stream to BT device

    private boolean messageInProgress = false; // keep track of whether message is in progress
    private int byteIndex = 0; // keep track of location in message
    private byte[] saveArray = new byte[GeneratedConstants.MESSAGE_LENGTH];

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
                                ArduinoInstruction newInstruction = new ArduinoInstruction(saveArray);
                                mHandler.obtainMessage(BluetoothService.NEW_INSTRUCTION_IN, newInstruction).sendToTarget();
                                Log.d("SENT","Sent instruction handler");
                            } catch (ArduinoInstruction.CorruptedInstructionException e) {
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
        }
    }

    public void writeArduinoInstruction(ArduinoInstruction inputInstruction) {
        // pretty simple to actually write data!
        //Log.d("SENDING_INSTRUCTION", "Attempting to write instruction bytes");
        byte[] sendBytes = inputInstruction.convertInstructionToBytes();
        Log.d("BYTES_SENT","0x"+bytesToHex(sendBytes));
        try {
            mOutStream.write(sendBytes);
        } catch (IOException e) {
            Log.e("WRITE_INSTR_FAILED", "Attempt to write instruction bytes failed");
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
            mInStream = null;
        }
        if (mOutStream != null) {
            try {mOutStream.close();} catch (Exception e) {
                Log.d("BT_CONNECTION", "Exception thrown while closing OutStream");
            }
            mOutStream = null;
        }
        if (mSocket != null) {
            try {mSocket.close();} catch (Exception e) {
                Log.d("BT_CONNECTION", "Exception thrown while closing Socket");
            }
            mSocket = null;
        }
    }
}

