package com.example.panotroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class ViewportView extends View {


    private RectF axisLimits = new RectF(-180,90,180,-90);
    private Panorama currentPanorama;
    private boolean doDrawPanorama = false;
    private PointF currentPoint; // direction the camera is pointing
    private Paint panoRegionPaint;



    public ViewportView(Context context) {
        super(context);
    }

    public ViewportView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initViewportView();
    }

    protected void initViewportView() {

    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {


    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(doDrawPanorama) {
            RectF panoRegionDeg = currentPanorama.getRegion();
            canvas.drawRect(scaleRectToAxes(panoRegionDeg), panoRegionPaint);
        }


    }

    private RectF scaleRectToAxes(RectF rectIn) {
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        //return new RectF()
        return null;
    }

    private float linmap(float value, float valueMin, float valueMax, float outMin, float outMax) {
        return (value - valueMin) * (outMax - outMin) / (valueMax - valueMin) + outMin;
    }

}
