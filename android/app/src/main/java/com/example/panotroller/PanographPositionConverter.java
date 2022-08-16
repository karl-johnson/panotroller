package com.example.panotroller;

import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;

// position converter for my implementation of the pan-tilt device
public class PanographPositionConverter extends PositionConverter {
    PanographPositionConverter() {
        updateOutputDegreesPerStep();
    }
    PanographPositionConverter(int microstepIn, PointF originIn) {
        origin = originIn; microstep = microstepIn;
        updateOutputDegreesPerStep();
    }
    // conversion parameters that can change
    private int microstep = GeneratedConstants.DEFAULT_MICROSTEP; // microsteps/full step
    private float outputDegreesPerStep = 0; // same for both axes
    private void updateOutputDegreesPerStep() {
        // mechanical parameters that won't change during program
        // steps per rev on stepper motor shaft
        float motorSteps = 200f;
        // belt reduction on motors
        float reduction = 5f;
        outputDegreesPerStep = (float) (360/(motorSteps*microstep* reduction));
    }

    // for microstepping changes we need to be able to know/update convertStepToDegrees(0,0)
    // this is what the value of this.origin represents
    // by doing this, we can change microstepping on a whim simply by:
    // - setting origin = convertStepToDegrees(current position)
    // - 0 step counters on device
    // - update microstep value
    // this also allows us to detect and handle 16 bit overflow issues with step counters simply
    // by re-zeroing the on-board step counters + updating this when we're nearing +/- 2^15
    private PointF origin = new PointF(0,0);
    public void setOrigin(PointF newOrigin) {origin = newOrigin;}

    // TODO: ON-THE-FLY MICROSTEPPING CHANGES - may need to move this class to scope w/ BTService
    // simply send instruction to change microstep and zero origin to current position

    // simple linear conversions
    public Point convertDegreesToSteps(PointF posDegrees) {
        return new Point(
                Math.round((posDegrees.x - origin.x)/outputDegreesPerStep),
                Math.round((posDegrees.y - origin.y)/outputDegreesPerStep));
    }
    public PointF convertStepsToDegrees(Point posSteps) {
        Log.d("CONVERT_STEPS", "In: " + posSteps.toString());
        PointF output = new PointF(
                origin.x + outputDegreesPerStep*posSteps.x,
                origin.x + outputDegreesPerStep*posSteps.y);
        // position in steps is a position purely relative to origin
        Log.d("CONVERT_STEPS", "Out: " + output.toString());
        return output;
    }
}
