// *** GENERATED BY COMMON/GENERATEHEADERS.PY *** 
// Header file to store common communication information 
#include <Arduino.h> 
#ifndef GLOBAL_HEADER
#define GLOBAL_HEADER
// ** BEGIN INSTRUCTION DEFINITIONS **
typedef enum {
	// Instruction pair: ping_int
	INST_PING_INT = 0b10000010,
	INST_PONG_INT = 0b00000010,
	// Instruction pair: error
	INST_AN_ERROR = 0b10000100,
	INST_AR_ERROR = 0b00000100,
	// Instruction pair: led_color
	INST_SET_LED = 0b10000110,
	INST_CNF_LED = 0b00000110,
	// Instruction pair: motor_speed
	INST_SET_MTR = 0b10001000,
	INST_CNF_MTR = 0b00001000,
	// Instruction pair: position
	INST_GET_POS = 0b10001010,
	INST_GOT_POS = 0b00001010,
	// Instruction pair: home
	INST_HOME_POS = 0b10001100,
	INST_HOMD_POS = 0b00001100,
	// Instruction pair: move
	INST_MOVE_ABS = 0b10001110,
	INST_MOVD_ABD = 0b00001110,
	// Instruction pair: ustep
	INST_SET_USTEP = 0b10010000,
	INST_CNF_USTEP = 0b00010000,
	// Instruction pair: mode
	INST_SET_MODE = 0b10010010,
	INST_CNF_MODE = 0b00010010,
	// Instruction pair: photo
	INST_TRIG_PHOT = 0b10010100,
	INST_TOOK_PHOT = 0b00010100,
	// Instruction pair: stop_all
	INST_STOP_ALL = 0b11111110,
	INST_STPD_ALL = 0b01111110,
	// Instruction pair: ping_float
	INST_PING_FLOAT = 0b10000011,
	INST_PONG_FLOAT = 0b00000011
} instr_code;

#define START_BYTE 0b00000000
#define MESSAGE_LENGTH 6

#endif