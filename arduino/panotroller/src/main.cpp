#include <Arduino.h>
#include <SoftwareSerial.h>
#include <AccelStepper.h>
#include <MultiStepper.h>

#include "GeneratedConstants.h"
#include "PinDefinitions.h"
#include "Utils.h"
#include "BluetoothInstruction.hpp"

#define STATE_DISCONNECTED LOW
#define DISCONNECTED_BLINK_COLOR 0xff0000
#define DISCONNECTED_BLINK_PERIOD 500 // (ms)
#define DISCONNECTED_POLL_RATE 5 // (ms)

#define UNVERIFIED_CONN_COLOR 0xffff00

#define CONNECTED_BLINK_COLOR 0x00ff00
#define CONNECTED_BLINK_PERIOD 250

#define SERIAL_BAUD_RATE 57600
#define HC05_BAUD_RATE 38400

// if connection drops after a non-zero motor speed is set, motor could runaway
// so if we don't get a set speed command for this amount of time, stop motors
#define MOTOR_TIMEOUT 1000 // (ms)


/* GLOBAL VARIABLES */
// communication variables
bool isConnectionVerified = false;
bool isMessageReady = false;
byte inputByteArray[MESSAGE_LENGTH];
BluetoothInstruction latestInstruction; // object to store decoded instruction
SoftwareSerial bluetooth(BT_RX, BT_TX);
void executeInstruction(BluetoothInstruction);

// functionality variables
/* two modes of motor control over BT:
* - able to directly set motor speeds (used for joystick control)
* - able to request motor move to an absolute or relative POINT w/ accel
*/
bool isPointMode = false;
// timeout for setting speed to prevent runaway on dropped connection
unsigned long lastSetSpeedTimer = 0;
void runMotors();

AccelStepper stepperX(AccelStepper::DRIVER, X_STEP, X_DIR);
AccelStepper stepperY(AccelStepper::DRIVER, Y_STEP, Y_DIR);

void setup() {
  // pinmodes are important for Software Serial
  pinMode(BT_RX, INPUT);
  pinMode(BT_TX, OUTPUT);
  Serial.begin(SERIAL_BAUD_RATE);
  bluetooth.begin(HC05_BAUD_RATE);

  stepperX.setMaxSpeed(400.0);
  stepperX.setAcceleration(500.0);
  stepperY.setMaxSpeed(400.0);
  stepperY.setAcceleration(500.0);
}

void loop() {
  // main loop
  if(digitalRead(BT_STATE) != STATE_DISCONNECTED) {
    // yes this looks weird but this definitely doesn't go in while loop
    setLedColor(UNVERIFIED_CONN_COLOR);
  }

  /****************** MAIN ACTION LOOP *******************/


  while(digitalRead(BT_STATE) != STATE_DISCONNECTED) {

    // motor tasks
    // TODO expand functionality away from just setting speed (include moves)

    runMotors();

    // poll SoftwareSerial bluetooth connection
    updateRx(&bluetooth, inputByteArray, &isMessageReady);
    if(isMessageReady) {
      // updateRx has signaled that an incoming message is ready
      latestInstruction.decodeFromBytes(inputByteArray);
      if(latestInstruction.isCorrupted) {
        // Uh oh, the checksum on this instruction failed
        Serial.println("nano got corrupted instruction lmao");
        // TODO send error code back to Android
      }
      else {
        // Not corrupted
        if(!isConnectionVerified) { // if our connection is not yet verified
          // this is the first instruction after BT connection established
          // this is our criteria for a verified connection
          isConnectionVerified = true;
          Serial.println("HC-05 Re-connected!");
          // blink LED green twice quickly to indicate this
          setLedColor(CONNECTED_BLINK_COLOR);
          delay(CONNECTED_BLINK_PERIOD);
          setLedColor(0);
          delay(CONNECTED_BLINK_PERIOD);
          setLedColor(CONNECTED_BLINK_COLOR);
          delay(CONNECTED_BLINK_PERIOD);
          setLedColor(0);
          // LED color is controlled entirely by Android instructions now
        }
        executeInstruction(latestInstruction);
      }
      isMessageReady = false;
    }
  } // end of while(bluetooth connected){}

  /**************** END OF MAIN ACTION LOOP ************/

  // if we are here it means the HC-05 module is in a disconnected state
  Serial.println("HC-05 Disconnected.");
  isConnectionVerified = false;
  while(digitalRead(BT_STATE) == STATE_DISCONNECTED) {
    // as long as it is in this state, blink the LED slowly
    if((millis()/DISCONNECTED_BLINK_PERIOD) % 2) setLedColor(0);
    else setLedColor(DISCONNECTED_BLINK_COLOR);
    delay(DISCONNECTED_POLL_RATE);
  }
  // if we are here it means we have seen a disconnected-to-connected transition
  // now if we loop back around to beginning of loop() we will become active

}

void runMotors() {
  if(isPointMode) {
    stepperX.run();
    stepperY.run();
  }
  else {
    // check for motor speed timeout
    if(millis() > (lastSetSpeedTimer + MOTOR_TIMEOUT)) {
      stepperX.stop();
      stepperY.stop();
    }
    stepperX.runSpeed();
    stepperY.runSpeed();
  }

}

void executeInstruction(BluetoothInstruction in) {
  // define behavior for all instructions we can receive
  if(!in.isFloatInstruction) { // int instructions only
    switch(in.inst) {
      // because of how switch statements work, these should be listed
      // in order of their priority (which needs low latency the most?)
      case INST_STOP_ALL:
        stepperX.stop();
        stepperY.stop();
        BluetoothInstruction(INST_STPD_ALL,0,0).send(&bluetooth);
      case INST_PING_INT:
        //Serial.print(in.intValue1, HEX);
        //Serial.print(in.intValue2, HEX);
        BluetoothInstruction(INST_PONG_INT,in.intValue1,in.intValue2).send(&bluetooth);
        break;
      case INST_PING_FLOAT:
        BluetoothInstruction(INST_PONG_FLOAT,in.floatValue).send(&bluetooth);
        break;
      case INST_SET_MODE:
        isPointMode = (bool) in.intValue1; // unnecesary cast for clarity
        BluetoothInstruction(INST_CNF_MODE,in.floatValue).send(&bluetooth);
      case INST_SET_MTR:
        stepperX.setSpeed(latestInstruction.intValue1);
        stepperY.setSpeed(latestInstruction.intValue2);
        Serial.print("Got speeds ");
        Serial.print(latestInstruction.intValue1);
        Serial.print(" ");
        Serial.println(latestInstruction.intValue2);
        lastSetSpeedTimer = millis(); // update
        break;
      case INST_SET_LED:
        // construct hex color code from 2 ints
        setLedColor(
          (((unsigned long) in.intValue1) << 16) || in.intValue2
        );
        BluetoothInstruction(INST_CNF_LED,in.intValue1,in.intValue2).send(&bluetooth);
        break;
      default:
        Serial.println("Unhandled int instruction");
    }
  }
  /*
  else {
    switch(in.inst) {
      //
    }
  }*/
}
