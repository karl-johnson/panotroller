#include "BluetoothInstruction.hpp"

BluetoothInstruction::BluetoothInstruction() {}

BluetoothInstruction::BluetoothInstruction(byte instruction, float value) {
  isFloatInstruction = true;
  inst = instruction;
  floatValue = value;
}

BluetoothInstruction::BluetoothInstruction(
      byte instruction, int value1, int value2) {
  inst = instruction;
  intValue1 = value1;
  intValue2 = value2;
}

BluetoothInstruction::BluetoothInstruction(byte instruction, byte value[4]) {
  isRawBytes = true;
  inst = instruction;
  for (int i=0; i<4; ++i) {
    rawByteValue[i] = value[i];
  }
}

void BluetoothInstruction::send(SoftwareSerial* serialDevice) {
  byte messageSend[MESSAGE_LENGTH] = {0};
  messageSend[0] = this->inst; // this-> for clarity
  // next 4 bytes depend on data type
  if(isRawBytes) {
    for(int i = 0; i < 4; i++) {
      messageSend[i+1] = rawByteValue[i];
    }
  }
  else if(isFloatInstruction) {
    // to get bytes of float use memcpy
    memcpy(messageSend+1, &(this->floatValue), sizeof(float));
  }
  else { // dual int instruction
    // to get bytes of ints use memcpy
    memcpy(messageSend+1, &(this->intValue1), sizeof(intValue1));
    memcpy(messageSend+3, &(this->intValue2), sizeof(intValue2));
  }
  // compute checksum and put in last byte
  messageSend[MESSAGE_LENGTH-1] = XORbyteArray(messageSend,MESSAGE_LENGTH-1);
  // now write to serialDevice along with start byte
  serialDevice->write((byte) START_BYTE);
  serialDevice->write(messageSend, MESSAGE_LENGTH);
  //Serial.print("Sent: ");
  //for(int i = 0; i < MESSAGE_LENGTH; i++) Serial.print(messageSend[i], HEX);
  //Serial.println();

}

void BluetoothInstruction::decodeFromBytes(byte inBytes[MESSAGE_LENGTH]) {
  if(XORbyteArray(inBytes, MESSAGE_LENGTH)) {
    // if XOR is non-zero, instruction is corrupted and no need to decode
    isCorrupted = true;
    return;
  }
  if(inBytes[0]%2) { // if LSB is 1, this instruction is for a float value
    isFloatInstruction = true;
    this->inst = inBytes[0];
    // chad pointer casting to recover float
    this->floatValue = *((float*) (inBytes+1));
  }
  else { // this is a dual int instruction (OR the one M->S bye instruction)
    this->inst = inBytes[0];
    // I don't know why I don't use pointer casting here but this works
    this->intValue1 = int(
        ((byte) (inBytes[1])) << 8 |
        ((byte) (inBytes[2])));
    this->intValue2 = int(
        ((byte) (inBytes[3])) << 8 |
        ((byte) (inBytes[4])));
  }
}

byte BluetoothInstruction::XORbyteArray(byte* input, int len) {
  byte xorResult = 0;
  for(int xorIndex = 0; xorIndex < len; xorIndex++) {
    xorResult = xorResult ^ input[xorIndex];
  }
  return xorResult;
}
