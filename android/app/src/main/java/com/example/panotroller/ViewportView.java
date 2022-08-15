package com.example.panotroller;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

public class ViewportView extends View {




    private RectF axisLimits = new RectF(-180,90,180,-90);
    private Panorama currentPanorama;
    private boolean doDrawPanorama = false;
    private PointF cameraRegionCenter = new PointF(0,0); // current FOV center point
    private RectF cameraRegion = null; // current camera FOV
    private Paint mPanoRegionPaint;
    private Paint mCameraRegionPaint;

    public ViewportView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        TypedArray styledAttributes = context.obtainStyledAttributes(attrs,R.styleable.ViewportView);

        int panoRegionColor;
        int cameraRegionColor;

        int DEFAULT_PANO_COLOR = context.getColor(R.color.green_accent);
        int DEFAULT_CAM_COLOR = context.getColor(R.color.white);

        try {
            panoRegionColor = styledAttributes.getColor(R.styleable.ViewportView_panoRegionColor, DEFAULT_PANO_COLOR);
            cameraRegionColor = styledAttributes.getColor(R.styleable.ViewportView_camRegionColor, DEFAULT_CAM_COLOR);
        } finally {
            styledAttributes.recycle();
        }

        mPanoRegionPaint = new Paint();
        mPanoRegionPaint.setAntiAlias(true);
        mPanoRegionPaint.setColor(panoRegionColor);
        mPanoRegionPaint.setStyle(Paint.Style.FILL);

        mCameraRegionPaint = new Paint();
        mCameraRegionPaint.setAntiAlias(true);
        mCameraRegionPaint.setColor(cameraRegionColor);
        mCameraRegionPaint.setStyle(Paint.Style.STROKE);
    }



    @Override
    protected void onDraw(Canvas canvas) {
        // draw background rectangle
        // canvas.drawRect(0, 0, getWidth(), getHeight(),
        if(doDrawPanorama) {
            RectF panoRegionDeg = currentPanorama.getRegion();
            if(panoRegionDeg != null)
                canvas.drawRect(scaleRectToAxes(panoRegionDeg), mPanoRegionPaint);
        }
        if(cameraRegion != null) {
            Log.d("VIEWPORT", "Drawing camera region: " + scaleRectToAxes(cameraRegion).toString());
            canvas.drawRect(scaleRectToAxes(cameraRegion), mCameraRegionPaint);
        }

    }

    public void updatePanorama(Panorama newPanorama) {
        currentPanorama = newPanorama;
        updateCameraFov(); // invalidate() called in here to redraw
    }

    public void updateCameraPos(PointF newPositionDeg) {
        cameraRegionCenter = newPositionDeg;
        Log.d("VIEWPORT", "New center: " + String.valueOf(newPositionDeg));
        updateCameraFov();
    }

    private void updateCameraFov() {
        if(cameraRegion == null)
            cameraRegion = new RectF(0,0,0,0);
        PointF cameraDimensions = currentPanorama.getCameraFovDeg();
        cameraRegion.left = cameraRegionCenter.x - cameraDimensions.x/2;
        cameraRegion.right = cameraRegionCenter.x + cameraDimensions.x/2;
        cameraRegion.top = cameraRegionCenter.y - cameraDimensions.y/2;
        cameraRegion.bottom = cameraRegionCenter.y + cameraDimensions.y/2;
        Log.d("VIEWPORT", "New FOV: " + cameraRegion.toString());
        invalidate();
    }

    private RectF scaleRectToAxes(RectF rectIn) {
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        // god there's gotta be a better way to do this
        return new RectF(
                linmap(rectIn.left,axisLimits.left, axisLimits.right, 0, measuredWidth),
                linmap(rectIn.top,axisLimits.top, axisLimits.bottom, 0, measuredHeight),
                linmap(rectIn.right,axisLimits.left, axisLimits.right, 0, measuredWidth),
                linmap(rectIn.bottom,axisLimits.top, axisLimits.bottom, 0, measuredHeight));
    }

    private float linmap(float value, float valueMin, float valueMax, float outMin, float outMax) {
        return (value - valueMin) * (outMax - outMin) / (valueMax - valueMin) + outMin;
    }

}
