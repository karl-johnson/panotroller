#include <Arduino.h>
#include <SoftwareSerial.h>
#include "PinDefinitions.h"
#include "GeneratedConstants.h"

#ifndef UTILS_H
#define UITLS_H


void setLedColor(unsigned long);

void updateRx(SoftwareSerial*, byte*, bool*);



#endif
