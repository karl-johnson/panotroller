#ifndef BLUETOOTHINSTRUCTION_HPP
#define BLUETOOTHINSTRUCTION_HPP

#include <Arduino.h>
#include <SoftwareSerial.h>
#include "GeneratedConstants.h"

class BluetoothInstruction { // class to abstract our BT communication protocol
  // this is REALLY similar to its analogue in the Java code
public:
  // constructors:
  BluetoothInstruction(); // sets nothing
  BluetoothInstruction(byte, float); // from float
  BluetoothInstruction(byte, int, int); // from ints
  BluetoothInstruction(byte, byte*); // from byte array
  void send(SoftwareSerial*);
  void decodeFromBytes(byte*);
  bool isCorrupted = false;
  byte inst = 0x00;
  int intValue1 = 0;
  int intValue2 = 0;
  float floatValue = 0;
  bool isFloatInstruction = false;
private:
  bool isRawBytes = false;
  byte rawByteValue[4];
  byte XORbyteArray(byte*, int);
};

#endif
