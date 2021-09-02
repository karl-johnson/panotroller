package com.example.panotroller;

import java.io.IOException;

public class ArduinoInstruction {
    // abstraction for the data format we use to robustly send data over Bluetooth

    public byte instructionValue = 0x00;
    public boolean isFloatInstruction = false;
    private boolean isRawBytes = false;
    public short intValue1 = 0;
    public short intValue2 = 0;
    public float floatValue = 0;
    public byte[] rawByteValue = new byte[4];

    //public final static byte START_BYTE = 0b00000000; // just null
    //public final static int MESSAGE_LENGTH = 6;

    // this constructor is for making an instruction from bytes received from Arduino
    public ArduinoInstruction(byte[] rawDataIn) throws CorruptedInstructionException, IOException {
        convertBytesToInstruction(rawDataIn);
    }

    // these constructors are for assembling instructions to send
    public ArduinoInstruction(byte instruction, float value) {
        isFloatInstruction = true;
        instructionValue = instruction;
        floatValue = value;
    }
    public ArduinoInstruction(byte instruction, short value1, short value2) {
        isFloatInstruction = false;
        instructionValue = instruction;
        intValue1 = value1;
        intValue2 = value2;
    }
    public ArduinoInstruction(byte instruction, byte[] value) {
        isRawBytes = true;
        instructionValue = instruction;
        rawByteValue = value;
    }
    private void convertBytesToInstruction(byte[] inBytes) throws CorruptedInstructionException, IOException {

        if(inBytes.length != GeneratedConstants.MESSAGE_LENGTH) {
            throw new IOException("Wrong message length: "+inBytes.length);
        }
        if(XORByteArray(inBytes) != 0) {
            // we have a corrupted instruction
            throw new CorruptedInstructionException("Got Corrupted Instruction");
        }
        instructionValue = inBytes[0];
        // hard-coded nature of our comm protocol: LSB 1 for float instruction
        isFloatInstruction = (instructionValue & (byte) 0x01) == 1;
        if(isFloatInstruction) {
            int intBits = inBytes[4] << 24
                    | (inBytes[3] & 0xFF) << 16
                    | (inBytes[2] & 0xFF) << 8
                    | (inBytes[1] & 0xFF);
            floatValue = Float.intBitsToFloat(intBits);
        }
        else {
            intValue1 = (short) (((inBytes[1] & 0xFF) << 8) | (inBytes[2] & 0xFF));
            intValue2 = (short) (((inBytes[3] & 0xFF) << 8) | (inBytes[4] & 0xFF));
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
        internalBytes[1] = instructionValue;
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
                internalBytes[2] = (byte) (intValue1 >> 8);
                internalBytes[3] = (byte) (intValue1);
                internalBytes[4] = (byte) (intValue2 >> 8);
                internalBytes[5] = (byte) (intValue2);
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
