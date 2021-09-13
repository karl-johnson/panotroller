package com.example.panotroller;

import android.graphics.Point;
import android.graphics.PointF;

public abstract class PositionConverter {
    // interface for converting a position in spherical coordinates to stepper motor positions
    // this varies based on each physical device so we can use a different extension of this
    // class for each physical implementation of a pan/tilt device
    public abstract Point convertDegreesToSteps(PointF posDegrees);
    public abstract PointF convertStepsToDegrees(Point posSteps);
}
