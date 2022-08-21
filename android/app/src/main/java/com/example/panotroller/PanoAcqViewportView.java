package com.example.panotroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import java.util.List;

public class PanoAcqViewportView extends ViewportView {

    private Panorama currentPanorama;
    private boolean doDrawPanorama = false;
    private Paint mPanoRegionPaint;
    private Paint mPointsPaint;

    public PanoAcqViewportView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // TODO use styledAtrributes instead of hardcoding
        // get colors
        int panoRegionColor, finishedPointsColor;
        int DEFAULT_PANO_COLOR = context.getColor(R.color.green_accent);
        int DEFAULT_POINTS_COLOR = context.getColor(R.color.green_accent);

        panoRegionColor = DEFAULT_PANO_COLOR;
        finishedPointsColor = DEFAULT_POINTS_COLOR;

        // create Paint objects from colors
        mPanoRegionPaint = new Paint();
        mPanoRegionPaint.setAntiAlias(true);
        mPanoRegionPaint.setColor(panoRegionColor);
        mPanoRegionPaint.setStyle(Paint.Style.STROKE);

        mPointsPaint = new Paint();
        mPointsPaint.setAntiAlias(true);
        mPointsPaint.setColor(finishedPointsColor);
        mPointsPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected  void onDraw(Canvas canvas) {
        if(doDrawPanorama) {
            if (currentPanorama != null) {
                RectF panoRegionDeg = currentPanorama.getFullRegion();
                if(panoRegionDeg != null)
                    canvas.drawRect(super.scaleRectToAxes(panoRegionDeg), mPanoRegionPaint);

            }
        }
        // draw camera and axis limits last
        super.onDraw(canvas);
    }

    public void updatePanorama(Panorama newPanorama) {
        currentPanorama = newPanorama;
        // TODO add way to turn off panorama
        if(newPanorama != null) {
            doDrawPanorama = true;
            // ensure camera set in super (ViewportView) is up to date with that of panorama
            setCamera(newPanorama.settings.camera);
            // this super eventually calls invalidate(), which will call onDraw() for us
        }
        else {
            doDrawPanorama = false;
        }
    }
}
