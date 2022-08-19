package com.example.panotroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import java.util.List;

public class PanoSetupViewportView extends ViewportView {

    private Panorama currentPanorama;
    private boolean doDrawPanorama = false;
    private Paint mPanoRegionPaint;
    private Paint mPointsPaint;

    public PanoSetupViewportView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // TODO use styledAtrributes instead of hardcoding
        // get colors
        int panoRegionColor, definingPointsColor;
        int DEFAULT_PANO_COLOR = context.getColor(R.color.green_accent);
        int DEFAULT_POINTS_COLOR = context.getColor(R.color.white);

        panoRegionColor = DEFAULT_PANO_COLOR;
        definingPointsColor = DEFAULT_POINTS_COLOR;

        // create Paint objects from colors
        mPanoRegionPaint = new Paint();
        mPanoRegionPaint.setAntiAlias(true);
        mPanoRegionPaint.setColor(panoRegionColor);
        mPanoRegionPaint.setStyle(Paint.Style.STROKE);

        mPointsPaint = new Paint();
        mPointsPaint.setAntiAlias(true);
        mPointsPaint.setColor(definingPointsColor);
        mPointsPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected  void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // this draws panorama in front of camera fov :(
        if(doDrawPanorama) {
            if (currentPanorama != null) {
                RectF panoRegionDeg = currentPanorama.getRegion();
                if(panoRegionDeg != null)
                    canvas.drawRect(super.scaleRectToAxes(panoRegionDeg), mPanoRegionPaint);
                List<PointF> currentPoints = currentPanorama.getDefiningPoints();
                for(PointF thisPoint : currentPoints) {
                    canvas.drawRect(scaleRectToAxes(getCameraRegionFromCenter(thisPoint)), mPointsPaint);
                }
            }
        }
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
