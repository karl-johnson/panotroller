package com.example.panotroller;

import java.io.IOException;

public class BluetoothInstruction {
    // abstraction for the data format we use to robustly send data over Bluetooth

    public byte inst = 0x00;
    public boolean isFloatInstruction = false;
    private boolean isRawBytes = false;
    public short int1 = 0;
    public short int2 = 0;
    public float floatValue = 0;
    public byte[] rawByteValue = new byte[4];

    // this constructor is for making an instruction from bytes received from Arduino
    public BluetoothInstruction(byte[] rawDataIn) throws CorruptedInstructionException, IOException {
        decodeFromBytes(rawDataIn);
    }

    // these constructors are for assembling instructions to send
    public BluetoothInstruction(byte instruction, float value) {
        isFloatInstruction = true;
        inst = instruction;
        floatValue = value;
    }
    public BluetoothInstruction(byte instruction, short value1, short value2) {
        isFloatInstruction = false;
        inst = instruction;
        int1 = value1;
        int2 = value2;
    }
    public BluetoothInstruction(byte instruction, byte[] value) {
        isRawBytes = true;
        inst = instruction;
        rawByteValue = value;
    }
    private void decodeFromBytes(byte[] inBytes) throws CorruptedInstructionException, IOException {

        if(inBytes.length != GeneratedConstants.MESSAGE_LENGTH) {
            throw new IOException("Wrong message length: "+inBytes.length);
        }
        if(XORByteArray(inBytes) != 0) {
            // we have a corrupted instruction
            throw new CorruptedInstructionException("Got Corrupted Instruction");
        }
        inst = inBytes[0];
        // hard-coded nature of our comm protocol: LSB 1 for float instruction
        isFloatInstruction = (inst & (byte) 0x01) == 1;
        if(isFloatInstruction) {
            int intBits = inBytes[4] << 24
                    | (inBytes[3] & 0xFF) << 16
                    | (inBytes[2] & 0xFF) << 8
                    | (inBytes[1] & 0xFF);
            floatValue = Float.intBitsToFloat(intBits);
        }
        else {
            int1 = (short) (((inBytes[2] & 0xFF) << 8) | (inBytes[1] & 0xFF));
            int2 = (short) (((inBytes[4] & 0xFF) << 8) | (inBytes[3] & 0xFF));
        }
    }
    /*
    public void constructFloatInstruction(byte instruction, float value) {
        isFloatInstruction = true;
        instructionValue = instruction;
        floatValue = value;
    }
    public void constructIntInstruction(byte instruction, short value1, short value2) {
        isFloatInstruction = false;
        instructionValue = instruction;
        intValue1 = value1;
        intValue2 = value2;
    }
    public void constructRawByteInstruction(byte instruction, byte[] bytes) {
        isRawBytes = true;
        instructionValue = instruction;
        rawByteValue = bytes;
    }*/
    public byte[] convertInstructionToBytes() {

        byte[] internalBytes = new byte[GeneratedConstants.MESSAGE_LENGTH+1]; // should be initialized to 0
        // length MESSAGE_LENGTH + 1 b/c start byte
        internalBytes[0] = 0x00;
        internalBytes[1] = inst;
        if(isRawBytes) {
            System.arraycopy(rawByteValue, 0, internalBytes, 2, 4);
        }
        else {
            if (isFloatInstruction) {
                int intBits = Float.floatToIntBits(floatValue);
                internalBytes[5] = (byte) (intBits >> 24);
                internalBytes[4] = (byte) (intBits >> 16);
                internalBytes[3] = (byte) (intBits >> 8);
                internalBytes[2] = (byte) (intBits);
                // swap endianness for Arduino. This is stupid but works
            } else {
                internalBytes[2] = (byte) (int1 >> 8);
                internalBytes[3] = (byte) (int1);
                internalBytes[4] = (byte) (int2 >> 8);
                internalBytes[5] = (byte) (int2);
            }
        }
        internalBytes[6] = XORByteArray(internalBytes); // index 5 is 0 prior to this so OK
        return internalBytes;
    }
    private byte XORByteArray(byte[] input) {
        byte xorResult = 0;
        for (byte b : input) {
            xorResult = (byte) (xorResult ^ b);
        }
        return xorResult;
    }

    public static class CorruptedInstructionException extends Exception {
        public CorruptedInstructionException(String message) {
            super(message);
        }
    }
}
