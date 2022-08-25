package com.example.panotroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import java.util.List;

public class PanoAcqViewportView extends ViewportView {

    private Panorama currentPanorama;
    private int photoProgress;
    private Panorama.PanoramaDetails panoramaDetails;
    private boolean doDrawPanorama = false;
    private Paint mPanoRegionPaint;
    private Paint mPointsPaint;

    private RectF bigRectDeg = new RectF(0,0,0,0);
    private RectF smallRectDeg = new RectF(0,0,0,0);

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
                if(photoProgress >= 1) {
                    // draw solid regions for what's been completed
                    // for big panoramas (1k+ photos), wayyy inefficient to draw every one
                    // TODO intelligently draw rectangles for other tile configs
                    PointF cameraFovDeg = camera.getCameraFovDeg();
                    PointF tileDelta = currentPanorama.getAdjustedTileDelta();
                    int numCompleteColumns = photoProgress/panoramaDetails.numTiles.y;
                    if(numCompleteColumns >= 1) {
                        float completeColumnsWidth = cameraFovDeg.x + (numCompleteColumns - 1) * tileDelta.x;
                        // big rectangle covering complete columns
                        bigRectDeg.left = panoRegionDeg.left;
                        bigRectDeg.top = panoRegionDeg.top;
                        bigRectDeg.right = panoRegionDeg.left + completeColumnsWidth;
                        bigRectDeg.bottom = panoRegionDeg.bottom;
                        canvas.drawRect(super.scaleRectToAxes(bigRectDeg), mPointsPaint);
                    }
                    int numInColumn = photoProgress % panoramaDetails.numTiles.y;
                    if(numInColumn >= 1) {
                        float partialColumnHeight = cameraFovDeg.y + (numInColumn - 1) * tileDelta.y;
                        smallRectDeg.left = panoRegionDeg.left + numCompleteColumns * tileDelta.x;
                        smallRectDeg.right = smallRectDeg.left + cameraFovDeg.x;
                        // serpentine chaos
                        if(numCompleteColumns % 2 == 0) {
                            smallRectDeg.top = panoRegionDeg.top;
                            smallRectDeg.bottom = panoRegionDeg.top + partialColumnHeight;
                        }
                        else {
                            smallRectDeg.bottom = panoRegionDeg.bottom;
                            smallRectDeg.top = panoRegionDeg.bottom - partialColumnHeight;
                        }
                        canvas.drawRect(super.scaleRectToAxes(smallRectDeg), mPointsPaint);
                    }
                }
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
            panoramaDetails = currentPanorama.getPanoramaDetails();
        }
        else {
            doDrawPanorama = false;
        }
    }

    public void updateProgress(int progress) {
        photoProgress = progress;
    }
}
