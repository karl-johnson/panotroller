#include "Utils.h"

// ms we allow a message to be in progress before deleting it
#define MESSAGE_TIMEOUT_DURATION 10

void setLedColor(unsigned long rgb) {
  if(rgb > 0xFFFFFF) rgb = 0xFFFFFF; // clip input value
  analogWrite(RED_LED, rgb >> 16);
  analogWrite(GRN_LED, (rgb & 0x00ff00) >> 8);
  analogWrite(BLU_LED, (rgb & 0x0000ff));
}

void updateRx(SoftwareSerial* serialDevice, byte* saveArray, bool* readyFlag) {
  // designed not to require global vars
  static bool messageInProgress = false;
  static int byteIndex = 0;
  static unsigned long messageStartMs = 0;
  if(messageInProgress) {
    if(millis() > messageStartMs + MESSAGE_TIMEOUT_DURATION) {
      // it's been too long since the message started - give up!
      byteIndex = 0;
      messageInProgress = false;
      Serial.println("Message Timeout");
    }
  }
  while (serialDevice->available()) {
    byte inByte = serialDevice->read();
    if(messageInProgress) {
      //Serial.print(inByte, HEX);
      //Serial.print(" ");
      saveArray[byteIndex] = inByte; // add byte to array
      byteIndex++;
      if(byteIndex == MESSAGE_LENGTH) {
        // if our read data is now the length of a message
      //Serial.println();
        *readyFlag = true;
        byteIndex = 0; // overwrite old message - kinda dangerous
        messageInProgress = false; // message is over
        return; // need to exit now so that this instruction can be handled
        // then any consecutive instructions will be read in once this is called again
        // if I'm lucky, this one return fixes the issue of consecutive
        // instructions not being handled
      }
    }
    else if(inByte == START_BYTE) {
      // start message after start byte; don't need to include this in array
      messageInProgress = true;
      messageStartMs = millis();
    }
    // if we're not in a message or at the start of one, discard byte
  }
}

bool setMicrostep(int microstep) {
  // ugly but fast microstep decoding for A3967
  switch(microstep) {
    case 1:
      digitalWrite(MS1, LOW);
      digitalWrite(MS2, LOW);
      break;
    case 2:
      digitalWrite(MS1, HIGH);
      digitalWrite(MS2, LOW);
      break;
    case 4:
      digitalWrite(MS1, LOW);
      digitalWrite(MS2, HIGH);
      break;
    case 8:
      digitalWrite(MS1, HIGH);
      digitalWrite(MS2, HIGH);
      break;
    default:
      return false;
  }
  return true;
}
