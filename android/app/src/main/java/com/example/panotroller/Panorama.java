package com.example.panotroller;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

// object to define a panorama and export the movements/instructions needed to acquire that panorama
public class Panorama {
    /*
     to shift micro-stepping concerns to lower-level portions of the code, this object is designed
     to work with all coordinates in degrees (panorama widths max out at 360 deg)
     however, x coordinates coming in should NOT be limited to [0,360] w/ wrapping or clipping
     this is cause a panorama with x limits [-10, 10] is much different from [10,350] & [0,10] etc.
     updateRegionFromPoints() handles what to do in situations with width > 360 deg etc.
    */

    /* CONSTANTS */
    // constants for user-configuration of what order the tile mosaic will be taken during acq.
    // these are based on PTGui's align-to-grid menus because those are very clean and intuitive
    // for what overall configuration the
    public final static int ORDER_ZIGZAG = 0;
    public final static int ORDER_WRAP = 1; // PTGui calls this "unidirectional"
    // for what direction acquisition starts moving first (e.g. row-by-row vs. column-by-column)
    public final static int DIRECTION_ROW = 0;
    public final static int DIRECTION_COLUMN = 1;
    // for which corner acquisition starts at
    public final static int CORNER_TOP_LEFT = 0;
    public final static int CORNER_TOP_RIGHT = 1;
    public final static int CORNER_BOT_LEFT = 2;
    public final static int CORNER_BOT_RIGHT = 3;

    /* MEMBERS */
    // list of points to include in panorama (panorama is bounding box)
    private List<PointF> definingPoints = new ArrayList<PointF>();
    public List<PointF> getDefiningPoints() {return definingPoints;}

    // rectangle which defines region that panorama will cover
    /*
     NOTE that this region is only the region covered by the CENTERS of the tiles, except for 360's.
     This is because during pano setup, we only track a single point as the "position" of the image
     frame, whereas in reality the image frame covers a region of some width and height
     For this abstraction of the panorama, we consider this point as the center of the image frame.
     The panorama region is simply the bounding box of the defining points (in most cases), so
     the actual size of the output stitched panorama will have 1/2 an image frame extra on each size
     of this internal 'region' abstraction
     However, for 360 panoramas, we would like to avoid this because it's most efficient to equally
     space our frame azimuth values out over the 360 degrees, respecting overlap, focal length, etc.
     This is why 360's are handled differently at the tile generation stage
     */
    private RectF region;
    public RectF getRegion() {return region;}

    // panorama settings members
    // settings which impact tile generation
    public int panoOrder = ORDER_ZIGZAG;
    public int panoDirection = DIRECTION_COLUMN;
    public int panoCorner = CORNER_TOP_LEFT;
    private boolean is360pano = false;
    public PanoramaCamera camera = null;
    public float focalLength = 0;
    public float overlap = 0.2f; // desired overlap between tiles in panorama
    // settings which impact timing
    public float settleTime = 0; // desired settle time after end of move before exposure starts
    public float exposureTime = 0; // desired still time after exposure triggered before next move

    /* future advanced function - take rows/columns without slowing down for each photo.
       in this case, we send single instructions for entire lines of photos as the timing for this
       during a row/column is precise and should be handled by microcontroller (not over bluetooth)
       if enabled settleTime and exposureTime will be ignored
    */
    // private boolean isContinuousPano = false;

    public void addPoint(PointF in) {
        // TODO WRAP INPUT?
        definingPoints.add(in);
        updateRegionFromPoints();
    }

    public void removeNearestPoint(PointF in) {
        if(definingPoints.isEmpty()) return; // no need to do anything
        PointF nearestPoint = null;
        Float nearestPointDistance = null, thisDistance = null;
        for(PointF thisPoint : definingPoints) {
            // iterate through points and see which is closest to the given point
            if(nearestPointDistance == null) { // first point is definitely the closest so far
                nearestPointDistance = squareDistance(in, thisPoint);
                nearestPoint = thisPoint;
            }
            else {
                if((thisDistance = squareDistance(in,thisPoint)) < nearestPointDistance) {
                    nearestPointDistance = thisDistance;
                    nearestPoint = thisPoint;
                }
            }
        }
        definingPoints.remove(nearestPoint); // remove the nearest point we found
        updateRegionFromPoints();
    }

    public List<PointF> generateTiles() {
        // TODO input sanitation
        // generates a list of tiles which span panorama region according to setting members
        List<PointF> out = new ArrayList<PointF>();
        // first calculate fundamental parameters of tiling common to all configurations
        // origin of tiling (different from this.region due to oversize)
        PointF tileOrigin = new PointF();
        // tile delta; abs val is spacing, sign encodes what direction tiling is from the origin
        // and thus what corner the origin is
        PointF tileDelta = new PointF();
        Point tileNum = new Point(); // number of tiles in each direction
        tileDelta.x = (float) Math.atan2(camera.xSize/2,focalLength) * (1-overlap);
        tileDelta.y = (float) Math.atan2(camera.ySize/2,focalLength) * (1-overlap);
        tileNum.x = (int) Math.ceil(region.width()/tileDelta.x);
        tileNum.y = (int) Math.ceil(region.height()/tileDelta.y);
        if(is360pano) { // if we have a 360 pano we can adjust spacing slightly to optimize overlap
            tileDelta.x = (float) 360.0/tileNum.x; // space out evenly with slightly more overlap
        }
        // this is the amount the corner of our tiling will have to be shifted in order to center
        // our over-sized acquisition region on the requested acquisition region
        PointF tileOriginShift = new PointF();
        tileOriginShift.x = (tileDelta.x*tileNum.x - region.width())/2;
        tileOriginShift.y = (tileDelta.y*tileNum.y - region.height())/2;
        // now we have to do case-by-case parameters
        // due to helper functions, all we have to do are play with signs of things
        switch(panoCorner) { // all corner information encoded in origin and spacing vectors
            case CORNER_TOP_LEFT:
                tileOrigin.y = region.top + tileOriginShift.y;
                tileOrigin.x = region.left - tileOriginShift.x;
                tileDelta.y*=-1; // flip sign of spacing so vector points down
                break;
            case CORNER_TOP_RIGHT:
                tileOrigin.y = region.top + tileOriginShift.y;
                tileOrigin.x = region.right + tileOriginShift.x;
                tileDelta.x*=-1; tileDelta.y*=-1; // flip more signs etc.
                break;
            case CORNER_BOT_LEFT:
                tileOrigin.y = region.bottom - tileOriginShift.y;
                tileOrigin.x = region.left - tileOriginShift.x;
                // no need to flip any here - starting in bottom left so both deltas are positive
                break;
            case CORNER_BOT_RIGHT:
                tileOrigin.y = region.bottom - tileOriginShift.y;
                tileOrigin.x = region.right + tileOriginShift.x;
                tileDelta.x*=-1;
                break;
            default:
                // TODO ERROR
        }
        switch(panoOrder) {
            case ORDER_ZIGZAG:
                out = generateZigzagTiling(tileOrigin,tileDelta, tileNum, panoDirection);
                break;
            case ORDER_WRAP:
                out = generateWrapTiling(tileOrigin,tileDelta, tileNum, panoDirection);
                break;
            default:
                // TODO ERROR
        }
        return out;
    }

    public List<BluetoothInstruction> generateInstructionList() {
        // TODO - generates a list of instructions that will acquire panorama if executed in order
        List<BluetoothInstruction> out = new ArrayList<BluetoothInstruction>();
        // blah blah blah iterate through generateTiles and convert these into instructions
        // this requires conversions to relative stepper motor coordinates in steps - yuck!
        return out;
    }

    /* HELPER METHODS */

    private void updateRegionFromPoints() {
        // if we ignore the fact that our azimuth (x) wraps irl, this line would be sufficient:
        region = getBoundingBox(definingPoints);
        // however at this point we could have x limits [-10, 370] which results in redundant acq.
        // as such we
    }

    private RectF getBoundingBox(List<PointF> pointsIn) {
        if(pointsIn == null || pointsIn.isEmpty()) return null; // no points = no bounding box
        RectF out = new RectF();
        for(PointF thisPoint : pointsIn) {
            if(out == null) { // this is the first point - set 0-width box
                out.left = out.right = thisPoint.x;
                out.bottom = out.top = thisPoint.y;
            }
            else { // for any other point, expand bounding box for every point that lies outside it
                // X coordinates
                if(thisPoint.x > out.right) out.right = thisPoint.x;
                else if(thisPoint.x < out.left) out.left = thisPoint.x;
                // Y coordinates
                if(thisPoint.y > out.top) out.top = thisPoint.y;
                else if(thisPoint.y < out.bottom) out.bottom = thisPoint.y;
            }
        }
        return out;
    }

    private Float squareDistance(PointF point1, PointF point2) {
        // all the cases where we use distance to a point only care about comparative dist
        // therefore we can avoid an expensive square root
        return (float) (Math.pow(point1.x - point2.x,2) + Math.pow(point1.y - point2.y,2));
    }

    private List<PointF> generateWrapTiling(
        PointF origin, PointF delta, Point num, int direction) {
        // TODO input sanitation
        // generate wrapping tiling of a region given vectors:
        // - origin (coordinates of starting corner)
        // - delta (spacing between tiles, with sign of components indicating what corner origin is)
        // - num (number of tiles in each direction)
        List<PointF> out = new ArrayList<PointF>();
        int xIndex = 0, yIndex = 0;
        if(direction == DIRECTION_ROW) {
            // long straightaways are rows, so OUTSIDE loop needs to be row index (y)
            for(yIndex = 0; yIndex < num.y; yIndex++) {
                for(xIndex = 0; xIndex < num.x; xIndex++) {
                    out.add(getTileFromIndex(xIndex,yIndex,origin,delta));
                }
            }
        }
        else if(direction == DIRECTION_COLUMN) {
            // long straightaways are columns, so OUTSIDE loop needs to be column index (x)
            for(xIndex = 0; xIndex < num.x; xIndex++) {
                for(yIndex = 0; yIndex < num.y; yIndex++) {
                    out.add(getTileFromIndex(xIndex,yIndex,origin,delta));
                }
            }
        }
        else {
            // TODO ERROR!
        }
        return out;
    }

    private List<PointF> generateZigzagTiling(
            PointF origin, PointF delta, Point num, int direction) {
        // TODO input sanitation
        // generate serpentine/zig-zag tiling of a region given vectors:
        // - origin (coordinates of starting corner)
        // - delta (spacing between tiles, with sign of components indicating what corner origin is)
        // - num (number of tiles in each direction)
        List<PointF> out = new ArrayList<PointF>();
        int xIndex = 0, yIndex = 0;
        if(direction == DIRECTION_ROW) {
            // long straightaways are rows, so OUTSIDE loop needs to be row index (y)
            while(yIndex < num.y) {
                for(xIndex = 0; xIndex < num.x; xIndex++) {
                    out.add(getTileFromIndex(xIndex,yIndex,origin,delta));
                }
                if(++yIndex == num.y) break; // odd number of rows requires intermediate break
                for(xIndex = num.x - 1; xIndex >= 0; xIndex--) {
                    out.add(getTileFromIndex(xIndex,yIndex,origin,delta));
                }
            }
        }
        else if(direction == DIRECTION_COLUMN) {
            // long straightaways are columns, so OUTSIDE loop needs to be column index (x)
            while(xIndex < num.x) {
                for(yIndex = 0; yIndex < num.y; yIndex++) {
                    out.add(getTileFromIndex(xIndex,yIndex,origin,delta));
                }
                if(++xIndex == num.x) break; // odd number of rows requires intermediate break
                for(yIndex = num.y - 1; yIndex >= 0; yIndex--) {
                    out.add(getTileFromIndex(xIndex,yIndex,origin,delta));
                }
            }
        }
        else {
            // TODO ERROR!
        }
        return out;
    }

    public PointF getTileFromIndex(int xIndexIn, int yIndexIn, PointF originIn, PointF deltaIn) {
        // common simple calculation shows up in all tile config calcs
        // this is because all tile configs have same positions but just a different alg
        // to calculate order of 2D indices
        return new PointF(originIn.x + xIndexIn*deltaIn.x,originIn.y + yIndexIn*deltaIn.y);
    }

    public static class PanoramaCamera {
        PanoramaCamera(float xS, float yS, int xR, int yR, float fR) {
            xSize = xS; ySize = yS; xRes = xR; yRes = yR; frameRate = fR;
        }
        // the sensor size is used along with the lens being used to space tiles properly
        public final float xSize; // width of the sensor in mm
        public final float ySize; // height of the sensor in
        // the resolution of the sensor is used in the estimate of the final pano resolution
        public final int xRes; // width of the sensor in px
        public final int yRes; // height of the sensor in px
        // frame rate for continuous acquisitions
        public final float frameRate;
    }
}