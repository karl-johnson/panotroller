package com.example.panotroller;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

import static java.lang.Math.abs;

public class ViewportView extends View {




    private RectF axisLimits = new RectF(-180,-90,180,90);
    private Rect axisRegion = new Rect(0,0,0,0); // to preserve aspect ratio in degrees, don't use full viewport

    private Panorama currentPanorama;
    private boolean doDrawPanorama = false;
    private PointF cameraRegionCenter = new PointF(0,0); // current FOV center point
    private RectF cameraRegion = null; // current camera FOV
    private Paint mAxisRegionPaint;
    private Paint mPanoRegionPaint;
    private Paint mCameraRegionPaint;
    private Paint mPointsPaint;

    public ViewportView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);



        TypedArray styledAttributes = context.obtainStyledAttributes(attrs,R.styleable.ViewportView);

        int panoRegionColor, cameraRegionColor, definingPointsColor;

        int DEFAULT_PANO_COLOR = context.getColor(R.color.green_accent);
        int DEFAULT_CAM_COLOR = context.getColor(R.color.green_accent);
        int DEFAULT_POINTS_COLOR = context.getColor(R.color.white);

        try {
            panoRegionColor = styledAttributes.getColor(R.styleable.ViewportView_panoRegionColor, DEFAULT_PANO_COLOR);
            cameraRegionColor = styledAttributes.getColor(R.styleable.ViewportView_camRegionColor, DEFAULT_CAM_COLOR);
            definingPointsColor = styledAttributes.getColor(R.styleable.ViewportView_camRegionColor, DEFAULT_POINTS_COLOR);

        } finally {
            styledAttributes.recycle();
        }

        //setBackground(null);

        mAxisRegionPaint = new Paint();
        mAxisRegionPaint.setAntiAlias(true);
        mAxisRegionPaint.setColor(0xff999999);
        mAxisRegionPaint.setStyle(Paint.Style.FILL);

        mPanoRegionPaint = new Paint();
        mPanoRegionPaint.setAntiAlias(true);
        mPanoRegionPaint.setColor(panoRegionColor);
        mPanoRegionPaint.setStyle(Paint.Style.STROKE);

        mCameraRegionPaint = new Paint();
        mCameraRegionPaint.setAntiAlias(true);
        mCameraRegionPaint.setColor(cameraRegionColor);
        mCameraRegionPaint.setStyle(Paint.Style.STROKE);

        mPointsPaint = new Paint();
        mPointsPaint.setAntiAlias(true);
        mPointsPaint.setColor(definingPointsColor);
        mPointsPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // calculate axisRegion
        double desiredAspect = abs(axisLimits.width()/axisLimits.height());
        // there's probably a built-in way to do this
        //Log.d("VIEWPORT", "Size changed " + w + " " + h + " " + desiredAspect);
        if(w >= desiredAspect*(double) h) {
            // size of the View is too wide for the desired aspect ratio
            // "black bars" will be on the sides
            axisRegion.top = 0;
            axisRegion.bottom = h;
            int scaledWidth = (int) (h*desiredAspect);
            axisRegion.left = (w-scaledWidth)/2;
            axisRegion.right = axisRegion.left + scaledWidth;
        }
        else { // h >= w/desiredAspect
            // size of the View is too tall
            // "black bars" will be on the top/bottom
            axisRegion.left = 0;
            axisRegion.right = w;
            int scaledHeight = (int) (w/desiredAspect);

            //Log.d("VIEWPORT", "Size changed " + h + " " + scaledHeight);
            axisRegion.top = (h-scaledHeight)/2;
            axisRegion.bottom = axisRegion.top + scaledHeight;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO could optimize this using
        //  an occasionally updated background layer + rapidly updated camera layer
        // draw axes rectangle
        if(axisRegion != null) {
            canvas.drawRect(axisRegion, mAxisRegionPaint);
            //Log.d("VIEWPORT", "Drew axis region " + axisRegion.toString());
        }
        // canvas.drawRect(0, 0, getWidth(), getHeight(),
        if(doDrawPanorama) {
            if (currentPanorama != null) {
                RectF panoRegionDeg = currentPanorama.getRegion();
                if(panoRegionDeg != null)
                    canvas.drawRect(scaleRectToAxes(panoRegionDeg), mPanoRegionPaint);
                List<PointF> currentPoints = currentPanorama.getDefiningPoints();
                for(PointF thisPoint : currentPoints) {
                    canvas.drawRect(scaleRectToAxes(getCameraRegionFromCenter(thisPoint)), mPointsPaint);
                }
            }
        }
        if(cameraRegion != null) {
            //Log.d("VIEWPORT", "Drawing camera region: " + scaleRectToAxes(cameraRegion).toString());
            canvas.drawRect(scaleRectToAxes(cameraRegion), mCameraRegionPaint);
        }
    }

    public void updatePanorama(Panorama newPanorama) {
        currentPanorama = newPanorama;
        // TODO add way to turn off panorama
        if(newPanorama != null) doDrawPanorama = true;
        updateCameraFov(); // invalidate() called in here to redraw
    }

    public void updateCameraPos(PointF newPositionDeg) {
        cameraRegionCenter = newPositionDeg;
        //Log.d("VIEWPORT", "New center: " + String.valueOf(newPositionDeg));
        updateCameraFov();
    }

    private void updateCameraFov() {
        cameraRegion = getCameraRegionFromCenter(cameraRegionCenter);
        //Log.d("VIEWPORT", "New FOV: " + cameraRegion.toString());
        invalidate();
    }

    private RectF getCameraRegionFromCenter(PointF center) {
        RectF thisRegion = new RectF(0,0,0,0);
        PointF cameraDimensions = currentPanorama.getCameraFovDeg();
        thisRegion.left = center.x - cameraDimensions.x/2;
        thisRegion.right = center.x + cameraDimensions.x/2;
        thisRegion.top = center.y - cameraDimensions.y/2;
        thisRegion.bottom = center.y + cameraDimensions.y/2;
        return thisRegion;
    }

    private RectF scaleRectToAxes(RectF rectIn) {
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        // god there's gotta be a better way to do this
        return new RectF(
                linmap(rectIn.left,axisLimits.left, axisLimits.right, axisRegion.left, axisRegion.right),
                linmap(rectIn.top,axisLimits.top, axisLimits.bottom, axisRegion.top, axisRegion.bottom),
                linmap(rectIn.right,axisLimits.left, axisLimits.right, axisRegion.left, axisRegion.right),
                linmap(rectIn.bottom,axisLimits.top, axisLimits.bottom, axisRegion.top, axisRegion.bottom));
    }

    private float linmap(float value, float valueMin, float valueMax, float outMin, float outMax) {
        return (value - valueMin) * (outMax - outMin) / (valueMax - valueMin) + outMin;
    }

}
