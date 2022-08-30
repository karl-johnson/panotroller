package com.example.panotroller;

import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class PanoCamera {
    PanoCamera(String displayName, float xSize, float ySize, int xRes, int yRes, float frameRate, float rawSize) {
        this.displayName = displayName;
        this.xSize = xSize; this.ySize = ySize;
        this.xRes = xRes; this.yRes = yRes;
        this.frameRate = frameRate; this.rawSize = rawSize;
    }

    PanoCamera(Bundle bundleIn) {
        displayName = bundleIn.getString("displayName");
        xSize = bundleIn.getFloat("xSize");
        ySize = bundleIn.getFloat("ySize");
        xRes = bundleIn.getInt("xRes");
        yRes = bundleIn.getInt("yRes");
        frameRate = bundleIn.getFloat("frameRate");
        rawSize = bundleIn.getFloat("rawSize");
        focalLength = bundleIn.getFloat("focalLength");
    }

    public Bundle writeToBundle() {
        Bundle bundleOut = new Bundle();
        bundleOut.putString("displayName", displayName);
        bundleOut.putFloat("xSize", xSize);
        bundleOut.putFloat("ySize",ySize);
        bundleOut.putInt("xRes",xRes);
        bundleOut.putInt("yRes",yRes);
        bundleOut.putFloat("frameRate",frameRate);
        bundleOut.putFloat("rawSize",rawSize);
        bundleOut.putFloat("focalLength",focalLength);
        return bundleOut;
    }

    public final String displayName;
    // the sensor size is used along with the lens being used to space tiles properly
    public final float xSize; // width of the sensor in mm
    public final float ySize; // height of the sensor in
    // the resolution of the sensor is used in the estimate of the final pano resolution
    public final int xRes; // width of the sensor in px
    public final int yRes; // height of the sensor in px
    // frame rate for continuous acquisitions
    public final float frameRate;
    public final float rawSize; // approx raw file size in MB
    public float focalLength = 50;

    public PointF getCameraFovDeg() {
        return new PointF(2.0f * (float) (180/Math.PI) * (float) Math.atan2(xSize/2,focalLength),
                2.0f * (float) (180/Math.PI) * (float) Math.atan2(ySize/2,focalLength));
    }

    public String toString() {
        return displayName;
    }

    public String toLongString() {
        return displayName + ": (" + xSize + ", " + ySize + "), ("+ xRes + ", " + yRes + "), " +
                frameRate + "fps, " + rawSize + " MB. " + focalLength + "mm.";
    }
}
