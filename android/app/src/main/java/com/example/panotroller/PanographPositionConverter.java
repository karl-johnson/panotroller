package com.example.panotroller;

import android.graphics.Point;
import android.graphics.PointF;

// position converter for my implementation of the pan-tilt device
public class PanographPositionConverter extends PositionConverter {
    PanographPositionConverter() {
        updateOutputDegreesPerStep();
    }
    PanographPositionConverter(int microstepIn, PointF originIn) {
        origin = originIn; microstep = microstepIn;
        updateOutputDegreesPerStep();
    }
    // mechanical parameters that won't change during program
    private final float motorSteps = 200f; // steps per rev on stepper motor shaft
    private final float reduction = 8f; // belt reduction on motors

    // conversion parameters that can change
    private int microstep = 8; // microsteps/full step
    private float outputDegreesPerStep = 0; // same for both axes
    private void updateOutputDegreesPerStep() {
        outputDegreesPerStep = 360f/(motorSteps*microstep*reduction);
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

    // simple linear conversions
    public Point convertDegreesToSteps(PointF posDegrees) {
        return new Point(
                Math.round((posDegrees.x - origin.x)/outputDegreesPerStep),
                Math.round((posDegrees.y - origin.y)/outputDegreesPerStep));
    }
    public PointF convertStepsToDegrees(Point posSteps) {
        // position in steps is a position purely relative to origin
        return new PointF(
                origin.x + outputDegreesPerStep*posSteps.x,
                origin.x + outputDegreesPerStep*posSteps.y);
    }
}
