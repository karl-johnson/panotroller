package com.example.panotroller;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// object to define a panorama and export the movements/instructions needed to acquire that panorama
public class Panorama implements Parcelable {
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

    // data from digicamdb.com; TODO experiments to determine frame rate
    public final static Map<String, PanoCamera> builtInCameras;
    static {
        // kinda yucky but you can't spend forever on little organizational things like this:
        // camera spinner relies on keys here being the same as what each PanoCamera's displayName is
        builtInCameras = new HashMap<String, PanoCamera>() {{
            put("Canon 5D Mark II", new PanoCamera("Canon 5D Mark II", 36.0f, 24.0f, 5616, 3744, 1f, 30));
            put("Canon 6D", new PanoCamera("Canon 6D", 36.0f, 24.0f, 5472, 3648, 1f, 30));
            put("Canon 7D Mark II", new PanoCamera("Canon 7D Mark II", 22.4f, 15f, 5486, 3682, 1f, 25));
            put("Canon 40D", new PanoCamera("Canon 40D", 22.2f, 14.8f, 3888, 2592, 1f, 12));
        }};
    }

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
    // NOTE RectF sign convention has (+,+) corner as BOTTOM right! (common image coordinate conv.)
    // we rely on the position in DEGREES being passed in
    private RectF region = new RectF(0,0,0,0);

    public RectF getRegion() {return region;}

    public PanoramaSettings settings = new PanoramaSettings();
    private boolean is360pano = false; // TODO ensure 360 work properly

    /* future advanced function - take rows/columns without slowing down for each photo.
       in this case, we send single instructions for entire lines of photos as the timing for this
       during a row/column is precise and should be handled by microcontroller (not over bluetooth)
       if enabled settleTime and exposureTime will be ignored
    */
    // private boolean isContinuousPano = false;

    /* PARCELABLE CODE */

    protected Panorama(Parcel in) {
        definingPoints = in.createTypedArrayList(PointF.CREATOR);
        region = in.readParcelable(RectF.class.getClassLoader());
        settings = new PanoramaSettings(in.readBundle(PanoramaSettings.class.getClassLoader()));
        is360pano = in.readByte() != 0;
    }

    public static final Creator<Panorama> CREATOR = new Creator<Panorama>() {
        @Override
        public Panorama createFromParcel(Parcel in) {
            return new Panorama(in);
        }

        @Override
        public Panorama[] newArray(int size) {
            return new Panorama[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(definingPoints);
        dest.writeParcelable(region, flags);
        dest.writeBundle(settings.writeToBundle());
        dest.writeByte((byte) (is360pano ? 1 : 0));
    }

    /* METHODS */
    // DEBUG CONSTRUCTOR
    Panorama() {};

    Panorama(RectF regionIn) {region = regionIn;}

    public PanoramaDetails getPanoramaDetails() {
        // calculate some useful information about the details of this panorama
        Point tileNum = getTileNum();
        PanoCamera thisCamera = settings.camera;
        double rawFileSize = tileNum.x * tileNum.y * thisCamera.rawSize; // in MB
        Point resolution = new Point(0,0);
        resolution.x = (int) Math.floor(tileNum.x * (1.0f-settings.overlap) * thisCamera.xRes);
        resolution.y = (int) Math.floor(tileNum.y * (1.0f-settings.overlap) * thisCamera.yRes);
        // from looking at all my current panoramas, the size of the zipped jpeg DZI tiles in
        // MB is generally at or under 100* number of gigapixels
        // https://www.desmos.com/calculator/ew3ykycnz8
        // i.e. 1 MB = 0.01 GP = 10 MP -> 1 B = 10 pixels! that's quite good compression!
        double finalPanoSize = 0.1 * resolution.x*resolution.y * 1e-9; // in GB
        return new PanoramaDetails(tileNum, rawFileSize, resolution, finalPanoSize);
    }

    public void addPoint(PointF in) {
        // TODO WRAP INPUT?
        definingPoints.add(in);
        updateRegionFromPoints();
        Log.d("PANORAMA", "Added point, points are " + definingPoints.toString());
        Log.d("PANORAMA", "Added point, new region is " + region.toString());
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
        if(region != null)
            Log.d("PANORAMA", "Removed point, new region is " + region.toString());
        else
            Log.d("PANORAMA", "Removed point, new region is null.");
    }

    private PointF getBasicTileDelta() {
        PointF cameraFov = settings.camera.getCameraFovDeg();
        PointF tileDelta = new PointF(0,0);
        tileDelta.x = cameraFov.x * (1.0f-settings.overlap);
        tileDelta.y = cameraFov.y * (1.0f-settings.overlap);
        return tileDelta;
    }

    private Point getTileNum() {
        // for cases where we only want number, and don't care about access to delta
        return getTileNumFromDelta(getBasicTileDelta());
    }

    private Point getTileNumFromDelta(PointF deltaIn) {
        Point tileNum = new Point(0,0);
        if(is360pano) tileNum.x = (int) Math.ceil(360.0/deltaIn.x);
        else tileNum.x = (int) Math.ceil(region.width()/deltaIn.x) + 1;
        tileNum.y = (int) Math.ceil(region.height()/deltaIn.y) + 1;
        return tileNum;
    }

    public PointF getAdjustedTileDelta() {
        PointF tileDelta = getBasicTileDelta();
        Point tileNum = getTileNumFromDelta(tileDelta);
        return getAdjustedTileDelta(tileDelta, tileNum);
    }

    private PointF getAdjustedTileDelta(PointF tileDelta, Point tileNum) {
        // again, have to copy code because there are cases where we don't need anything but delta
        if(is360pano) { // if we have a 360 pano we should adjust spacing slightly to optimize overlap
            tileDelta.x = (float) 360.0/tileNum.x; // space out evenly with slightly more overlap
        }
        return tileDelta;
    }

    public RectF getFullRegion() {
        if(region != null) {
            // unlike rest of code, for visualizations we might want camera region including cam FOV
            PointF tileDelta = getBasicTileDelta();
            Point tileNum = getTileNumFromDelta(tileDelta);
            tileDelta = getAdjustedTileDelta(tileDelta, tileNum);
            PointF tileOriginShift = getTileOriginShift(tileDelta, tileNum);
            PointF cameraFov = settings.camera.getCameraFovDeg();
            PointF fullOrigin = new PointF(region.left - tileOriginShift.x - cameraFov.x / 2,
                    region.top - tileOriginShift.y - cameraFov.y / 2);
            PointF adjustedRegionWidth = new PointF(tileDelta.x * (tileNum.x - 1) + cameraFov.x,
                    tileDelta.y * (tileNum.y - 1) + cameraFov.y);
            return new RectF(fullOrigin.x, fullOrigin.y,
                    fullOrigin.x + adjustedRegionWidth.x,
                    fullOrigin.y + adjustedRegionWidth.y);
        }
        else return null;
    }

    private PointF getTileOriginShift(PointF tileDelta, Point tileNum) {
        PointF tileOriginShift = new PointF();
        tileOriginShift.x = (tileDelta.x*(tileNum.x-1) - region.width())/2;
        tileOriginShift.y = (tileDelta.y*(tileNum.y-1) - region.height())/2;
        return tileOriginShift;
    }

    public List<PointF> generateTiles() {
        // note - sign conventions get messy here, but it works so I'm not going to bother
        Log.d("PANORAMA", "Generate tiles called with region " + region.toString());
        Log.d("PANORAMA", "Generate tiles called with region size + (" + region.width() + "x" + region.height() + ")");
        // TODO input sanitation
        // generates a list of tiles which span panorama region according to setting members
        List<PointF> out = new ArrayList<PointF>();
        // first calculate fundamental parameters of tiling common to all configurations
        // origin of tiling (different from this.region due to oversize)
        PointF tileOrigin = new PointF();
        // tile delta; abs val is spacing, sign encodes what direction tiling is from the origin
        // and thus what corner the origin is
        PointF tileDelta;
        Point tileNum; // number of tiles in each direction
        //Log.d("PANORAMA", "Focal length " + settings.camera.focalLength + ", camera sensor " + camera.xSize + "x" + camera.ySize + "mm (" + camera.displayName + ")");
        tileDelta = getBasicTileDelta(); // does not account for 360
        tileNum = getTileNumFromDelta(tileDelta);
        tileDelta = getAdjustedTileDelta(tileDelta, tileNum); // account for 360 degree panos
        Log.d("PANORAMA", "Generate tiles calculated tile numbers " + tileNum.toString() + " and tile deltas " + tileDelta.toString());
        // this is the amount the corner of our tiling will have to be shifted in order to center
        // our over-sized acquisition region on the requested acquisition region
        PointF tileOriginShift = getTileOriginShift(tileDelta, tileNum);
        // now we have to do case-by-case parameters
        // due to helper functions, all we have to do are play with signs of things
        switch(settings.corner) { // all corner information encoded in origin and spacing vectors
            case CORNER_TOP_LEFT:
                // top left is corner with most negative coordinates (image convention)
                tileOrigin.y = region.top - tileOriginShift.y;
                tileOrigin.x = region.left - tileOriginShift.x;
                break;
            case CORNER_TOP_RIGHT:
                // top right has most positive x but most negative y
                tileOrigin.y = region.top - tileOriginShift.y;
                tileOrigin.x = region.right + tileOriginShift.x;
                tileDelta.x*=-1; // need to go (-x, +y) to get to panorama tiles
                break;
            case CORNER_BOT_LEFT:
                // bottom left has most positive y but most negative x
                tileOrigin.y = region.bottom + tileOriginShift.y;
                tileOrigin.x = region.left - tileOriginShift.x;
                tileDelta.y*=1; // need to go (+x, -y) to get to panorama tiles
                break;
            case CORNER_BOT_RIGHT:
                // top right has most positive x and y
                tileOrigin.y = region.bottom + tileOriginShift.y;
                tileOrigin.x = region.right + tileOriginShift.x;
                tileDelta.x*=-1; tileDelta.y*=1; // both negative as we are at most positive coords
                break;
            default:
                // TODO ERROR
        }
        switch(settings.order) {
            case ORDER_ZIGZAG:
                out = generateZigzagTiling(tileOrigin, tileDelta, tileNum, settings.direction);
                break;
            case ORDER_WRAP:
                out = generateWrapTiling(tileOrigin, tileDelta, tileNum, settings.direction);
                break;
            default:
                // TODO ERROR
        }
        return out;
    }

    public List<BluetoothInstruction> generateInstructionList(PositionConverter converterIn) {

        Log.d("PANORAMA", "Generating instruction list...");
        // generates a list of instructions that will acquire panorama if executed in order
        // requires a PositionConverter implementation to convert degrees to stepper motor positions
        List<BluetoothInstruction> out = new ArrayList<BluetoothInstruction>();
        Log.d("PANORAMA", "Calling generate tiles");
        List<PointF> tiles = generateTiles();
        // first instruction in acquisition is changing mode of motors to point-by-point
        out.add(new BluetoothInstruction(GeneratedConstants.INST_SET_MODE, (short) 1, (short) 0));
        // iterate through tiles and convert these into instructions for moving + taking photos
        Point convertedPosition;
        for(PointF tile : tiles) {
            // convert tile's position in degrees to position in steps of motors
            convertedPosition = converterIn.convertDegreesToSteps(tile);
            // instruction to move to position
            // TODO WARN ABOUT 16-bit overflow or handle in a smart way
            Log.d("PANORAMA", "Adding tile instruction at position " + convertedPosition.x + ", " + convertedPosition.y);
            out.add(new BluetoothInstruction(GeneratedConstants.INST_MOVE_ABS,
                    (short) convertedPosition.x, (short) convertedPosition.y));
            // instruction to take photo, which includes settling and exposure time
            out.add(new BluetoothInstruction(GeneratedConstants.INST_TRIG_PHOT,
                    settings.exposureTime, settings.settleTime));
        }
        return out;
    }

    /* HELPER METHODS */

    private void updateRegionFromPoints() {
        // if we ignore the fact that our azimuth (x) wraps irl, this line would be sufficient:
        region = getBoundingBox(definingPoints);
        // however at this point we could have x limits [-10, 370] which results in redundant acq.
        is360pano = region.width() >= 360;
        // TODO might need more 360 panorama handling
    }

    private RectF getBoundingBox(List<PointF> pointsIn) {
        if(pointsIn == null || pointsIn.isEmpty()) return null; // no points = no bounding box
        RectF out = new RectF();
        boolean isFirstPoint = true;
        for(PointF thisPoint : pointsIn) {
            if(isFirstPoint) { // this is the first point - set 0-width box
                out.left = out.right = thisPoint.x;
                out.bottom = out.top = thisPoint.y;
                isFirstPoint = false;
            }
            else { // for any other point, expand bounding box for every point that lies outside it
                // X coordinates
                if(thisPoint.x > out.right) out.right = thisPoint.x;
                else if(thisPoint.x < out.left) out.left = thisPoint.x;
                // Y coordinates
                if(thisPoint.y > out.bottom) out.bottom = thisPoint.y;
                else if(thisPoint.y < out.top) out.top = thisPoint.y;
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
                yIndex++;
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
                xIndex++;
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
        Log.d("PANORAMA", "Calculating tile at index " + xIndexIn + ", " + yIndexIn);
        return new PointF(originIn.x + xIndexIn*deltaIn.x,originIn.y + yIndexIn*deltaIn.y);
    }

    public static class PanoramaSettings {
        // class to encapsulate panorama settings that can be edited by PanoramaSettingsMenu
        // does not include panorama region and defining points etc.
        public int order = ORDER_ZIGZAG;
        public int direction = DIRECTION_COLUMN;
        public int corner = CORNER_TOP_LEFT;
        // to avoid parceling cameras (which are static anyways) simply store name
        public PanoCamera camera = builtInCameras.get("Canon 5D Mark II"); // default camera
        public float overlap = 0.2f; // desired overlap between tiles in panorama (change to %?)
        // settings which impact timing
        public short settleTime = 0; // desired settle time after end of move before exposure starts
        public short exposureTime = 0; // desired still time after exposure triggered before next move

        public PanoramaSettings() {}

        // method to print settings as string for debugging
        public String toString() {
            return "cam: " + camera.toString() +
                    "o: " + overlap +
                    ", s: " + settleTime +
                    ", e: " + exposureTime;
        }

        /* PARCELABLE METHODS */

        PanoramaSettings(Bundle bundleIn) {
            order = bundleIn.getInt("order");
            direction = bundleIn.getInt("direction");
            corner = bundleIn.getInt("corner");
            camera = new PanoCamera(bundleIn.getBundle("camera"));
            overlap = bundleIn.getFloat("overlap");
            settleTime = bundleIn.getShort("settleTime");
            exposureTime = bundleIn.getShort("exposureTime");
        }

        public Bundle writeToBundle() {
            Bundle bundleOut = new Bundle();
            bundleOut.putInt("order", order);
            bundleOut.putInt("direction", direction);
            bundleOut.putInt("corner",corner);
            bundleOut.putBundle("camera",camera.writeToBundle());
            bundleOut.putFloat("overlap",overlap);
            bundleOut.putShort("settleTime",settleTime);
            bundleOut.putShort("exposureTime",exposureTime);
            return bundleOut;
        }
    }

    public static class PanoramaDetails {
        PanoramaDetails(Point numTiles, double rawFilesSize, Point resolution, double finalPanoSize) {
            this.numTiles = numTiles; this.rawFilesSize = rawFilesSize;
            this.resolution = resolution; this.finalPanoSize = finalPanoSize;
        }
        public final Point numTiles;
        public final double rawFilesSize;
        public final Point resolution;
        public final double finalPanoSize;
    }

}