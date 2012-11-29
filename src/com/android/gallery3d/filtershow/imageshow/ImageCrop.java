/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;

import com.android.gallery3d.R;

public class ImageCrop extends ImageGeometry {
    private static final boolean LOGV = false;
    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;

    //Corners
    private static final int TOP_LEFT = MOVE_TOP | MOVE_LEFT;
    private static final int TOP_RIGHT = MOVE_TOP | MOVE_RIGHT;
    private static final int BOTTOM_RIGHT = MOVE_BOTTOM | MOVE_RIGHT;
    private static final int BOTTOM_LEFT = MOVE_BOTTOM | MOVE_LEFT;

    private static final float MIN_CROP_WIDTH_HEIGHT = 0.1f;
    private static int mTouchTolerance = 45;

    private boolean mFirstDraw = true;
    private float mAspectWidth = 1;
    private float mAspectHeight = 1;
    private boolean mFixAspectRatio = false;

    private float mLastRot = 0;
    private final Paint borderPaint;

    private int movingEdges;
    private final Drawable cropIndicator;
    private final int indicatorSize;
    private final int mBorderColor = Color.argb(128, 255, 255, 255);

    private static final String LOGTAG = "ImageCrop";

    private String mAspect = "";
    private int mAspectTextSize = 24;

    public void setAspectTextSize(int textSize){
        mAspectTextSize = textSize;
    }

    public void setAspectString(String a){
        mAspect = a;
    }

    private static final Paint gPaint = new Paint();

    public ImageCrop(Context context) {
        super(context);
        Resources resources = context.getResources();
        cropIndicator = resources.getDrawable(R.drawable.camera_crop);
        indicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(mBorderColor);
        borderPaint.setStrokeWidth(2f);
    }

    public ImageCrop(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        cropIndicator = resources.getDrawable(R.drawable.camera_crop);
        indicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(mBorderColor);
        borderPaint.setStrokeWidth(2f);
    }

    @Override
    public String getName() {
        return getContext().getString(R.string.crop);
    }

    private void swapAspect(){
        float temp = mAspectWidth;
        mAspectWidth = mAspectHeight;
        mAspectHeight = temp;
    }

    public static void setTouchTolerance(int tolerance){
        mTouchTolerance = tolerance;
    }

    private boolean switchCropBounds(int moving_corner, RectF dst) {
        RectF crop = getCropBoundsDisplayed();
        float dx1 = 0;
        float dy1 = 0;
        float dx2 = 0;
        float dy2 = 0;
        if ((moving_corner & MOVE_RIGHT) != 0) {
            dx1 = mCurrentX - crop.right;
        } else if ((moving_corner & MOVE_LEFT) != 0) {
            dx1 = mCurrentX - crop.left;
        }
        if ((moving_corner & MOVE_BOTTOM) != 0) {
            dy1 = mCurrentY - crop.bottom;
        } else if ((moving_corner & MOVE_TOP) != 0) {
            dy1 = mCurrentY - crop.top;
        }
        RectF newCrop = null;
        //Fix opposite corner in place and move sides
        if (moving_corner == BOTTOM_RIGHT) {
            newCrop = new RectF(crop.left, crop.top, crop.left + crop.height(), crop.top
                    + crop.width());
        } else if (moving_corner == BOTTOM_LEFT) {
            newCrop = new RectF(crop.right - crop.height(), crop.top, crop.right, crop.top
                    + crop.width());
        } else if (moving_corner == TOP_LEFT) {
            newCrop = new RectF(crop.right - crop.height(), crop.bottom - crop.width(),
                    crop.right, crop.bottom);
        } else if (moving_corner == TOP_RIGHT) {
            newCrop = new RectF(crop.left, crop.bottom - crop.width(), crop.left
                    + crop.height(), crop.bottom);
        }
        if ((moving_corner & MOVE_RIGHT) != 0) {
            dx2 = mCurrentX - newCrop.right;
        } else if ((moving_corner & MOVE_LEFT) != 0) {
            dx2 = mCurrentX - newCrop.left;
        }
        if ((moving_corner & MOVE_BOTTOM) != 0) {
            dy2 = mCurrentY - newCrop.bottom;
        } else if ((moving_corner & MOVE_TOP) != 0) {
            dy2 = mCurrentY - newCrop.top;
        }
        if (Math.sqrt(dx1*dx1 + dy1*dy1) > Math.sqrt(dx2*dx2 + dy2*dy2)){
             Matrix m = getCropBoundDisplayMatrix();
             Matrix m0 = new Matrix();
             if (!m.invert(m0)){
                 if (LOGV)
                     Log.v(LOGTAG, "FAILED TO INVERT CROP MATRIX");
                 return false;
             }
             if (!m0.mapRect(newCrop)){
                 if (LOGV)
                     Log.v(LOGTAG, "FAILED TO MAP RECTANGLE TO RECTANGLE");
                 return false;
             }
             swapAspect();
             dst.set(newCrop);
             return true;
        }
        return false;
    }

    public void apply(float w, float h){
        mFixAspectRatio = true;
        mAspectWidth = w;
        mAspectHeight = h;
        setLocalCropBounds(getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten()));
        cropSetup();
        saveAndSetPreset();
        invalidate();
    }

    public void applyOriginal() {
        mFixAspectRatio = true;
        RectF photobounds = getLocalPhotoBounds();
        float w = photobounds.width();
        float h = photobounds.height();
        float scale = Math.min(w, h);
        mAspectWidth = w / scale;
        mAspectHeight = h / scale;
        setLocalCropBounds(getUntranslatedStraightenCropBounds(photobounds,
                getLocalStraighten()));
        cropSetup();
        saveAndSetPreset();
        invalidate();
    }

    public void applyClear() {
        mFixAspectRatio = false;
        setLocalCropBounds(getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten()));
        cropSetup();
        saveAndSetPreset();
        invalidate();
    }

    private float getScaledMinWidthHeight() {
        RectF disp = new RectF(0, 0, getWidth(), getHeight());
        float scaled = Math.min(disp.width(), disp.height()) * MIN_CROP_WIDTH_HEIGHT
                / computeScale(getWidth(), getHeight());
        return scaled;
    }

    protected Matrix getCropRotationMatrix(float rotation, RectF localImage) {
        Matrix m = getLocalGeoFlipMatrix(localImage.width(), localImage.height());
        m.postRotate(rotation, localImage.centerX(), localImage.centerY());
        if (!m.rectStaysRect()) {
            return null;
        }
        return m;
    }

    protected Matrix getCropBoundDisplayMatrix(){
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());
        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO MAP CROP BOUNDS TO RECTANGLE");
            m = new Matrix();
        }
        float zoom = computeScale(getWidth(), getHeight());
        m.postTranslate(mXOffset, mYOffset);
        m.postScale(zoom, zoom, mCenterX, mCenterY);
        return m;
    }

    protected RectF getCropBoundsDisplayed() {
        RectF bounds = getLocalCropBounds();
        RectF crop = new RectF(bounds);
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO MAP CROP BOUNDS TO RECTANGLE");
            m = new Matrix();
        } else {
            m.mapRect(crop);
        }
        m = new Matrix();
        float zoom = computeScale(getWidth(), getHeight());
        m.setScale(zoom, zoom, mCenterX, mCenterY);
        m.preTranslate(mXOffset, mYOffset);
        m.mapRect(crop);
        return crop;
    }

    private RectF getRotatedCropBounds() {
        RectF bounds = getLocalCropBounds();
        RectF crop = new RectF(bounds);
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO MAP CROP BOUNDS TO RECTANGLE");
            return null;
        } else {
            m.mapRect(crop);
        }
        return crop;
    }

    private RectF getUnrotatedCropBounds(RectF cropBounds) {
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO GET ROTATION MATRIX");
            return null;
        }
        Matrix m0 = new Matrix();
        if (!m.invert(m0)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO INVERT ROTATION MATRIX");
            return null;
        }
        RectF crop = new RectF(cropBounds);
        if (!m0.mapRect(crop)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO UNROTATE CROPPING BOUNDS");
            return null;
        }
        return crop;
    }

    private RectF getRotatedStraightenBounds() {
        RectF straightenBounds = getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten());
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO MAP STRAIGHTEN BOUNDS TO RECTANGLE");
            return null;
        } else {
            m.mapRect(straightenBounds);
        }
        return straightenBounds;
    }

    /**
     * Sets cropped bounds; modifies the bounds if it's smaller than the allowed
     * dimensions.
     */
    public void setCropBounds(RectF bounds) {
        // Avoid cropping smaller than minimum width or height.
        RectF cbounds = new RectF(bounds);
        float minWidthHeight = getScaledMinWidthHeight();
        float aw = mAspectWidth;
        float ah = mAspectHeight;
        if (mFixAspectRatio) {
            minWidthHeight /= aw * ah;
            int r = (int) (getLocalRotation() / 90);
            if (r % 2 != 0) {
                float temp = aw;
                aw = ah;
                ah = temp;
            }
        }

        float newWidth = cbounds.width();
        float newHeight = cbounds.height();
        if (mFixAspectRatio) {
            if (newWidth < (minWidthHeight * aw) || newHeight < (minWidthHeight * ah)) {
                newWidth = minWidthHeight * aw;
                newHeight = minWidthHeight * ah;
            }
        } else {
            if (newWidth < minWidthHeight) {
                newWidth = minWidthHeight;
            }
            if (newHeight < minWidthHeight) {
                newHeight = minWidthHeight;
            }
        }
        RectF pbounds = getLocalPhotoBounds();
        if (pbounds.width() < minWidthHeight) {
            newWidth = pbounds.width();
        }
        if (pbounds.height() < minWidthHeight) {
            newHeight = pbounds.height();
        }

        cbounds.set(cbounds.left, cbounds.top, cbounds.left + newWidth, cbounds.top + newHeight);
        RectF straightenBounds = getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten());
        cbounds.intersect(straightenBounds);

        if (mFixAspectRatio) {
            fixAspectRatio(cbounds, aw, ah);
        }
        setLocalCropBounds(cbounds);
        invalidate();
    }

    private void detectMovingEdges(float x, float y) {
        RectF cropped = getCropBoundsDisplayed();
        movingEdges = 0;

        // Check left or right.
        float left = Math.abs(x - cropped.left);
        float right = Math.abs(x - cropped.right);
        if ((left <= mTouchTolerance) && (left < right)) {
            movingEdges |= MOVE_LEFT;
        }
        else if (right <= mTouchTolerance) {
            movingEdges |= MOVE_RIGHT;
        }

        // Check top or bottom.
        float top = Math.abs(y - cropped.top);
        float bottom = Math.abs(y - cropped.bottom);
        if ((top <= mTouchTolerance) & (top < bottom)) {
            movingEdges |= MOVE_TOP;
        }
        else if (bottom <= mTouchTolerance) {
            movingEdges |= MOVE_BOTTOM;
        }
        // Check inside block.
        if (cropped.contains(x, y) && (movingEdges == 0)) {
            movingEdges = MOVE_BLOCK;
        }
        if (mFixAspectRatio && (movingEdges != MOVE_BLOCK)) {
            movingEdges = fixEdgeToCorner(movingEdges);
        }
        invalidate();
    }

    private int fixEdgeToCorner(int moving_edges){
        if (moving_edges == MOVE_LEFT) {
            moving_edges |= MOVE_TOP;
        }
        if (moving_edges == MOVE_TOP) {
            moving_edges |= MOVE_LEFT;
        }
        if (moving_edges == MOVE_RIGHT) {
            moving_edges |= MOVE_BOTTOM;
        }
        if (moving_edges == MOVE_BOTTOM) {
            moving_edges |= MOVE_RIGHT;
        }
        return moving_edges;
    }

    private RectF fixedCornerResize(RectF r, int moving_corner, float dx, float dy){
        RectF newCrop = null;
        //Fix opposite corner in place and move sides
        if (moving_corner == BOTTOM_RIGHT) {
            newCrop = new RectF(r.left, r.top, r.left + r.width() + dx, r.top + r.height()
                    + dy);
        } else if (moving_corner == BOTTOM_LEFT) {
            newCrop = new RectF(r.right - r.width() + dx, r.top, r.right, r.top + r.height()
                    + dy);
        } else if (moving_corner == TOP_LEFT) {
            newCrop = new RectF(r.right - r.width() + dx, r.bottom - r.height() + dy,
                    r.right, r.bottom);
        } else if (moving_corner == TOP_RIGHT) {
            newCrop = new RectF(r.left, r.bottom - r.height() + dy, r.left
                    + r.width() + dx, r.bottom);
        }
        return newCrop;
    }

    private void moveEdges(float dX, float dY) {
        RectF cropped = getRotatedCropBounds();
        float minWidthHeight = getScaledMinWidthHeight();
        float scale = computeScale(getWidth(), getHeight());
        float deltaX = dX / scale;
        float deltaY = dY / scale;
        int select = movingEdges;
        if (mFixAspectRatio && (select != MOVE_BLOCK)) {

            // TODO: add in orientation change for fixed aspect
            /*if (select == TOP_LEFT || select == TOP_RIGHT ||
                    select == BOTTOM_LEFT || select == BOTTOM_RIGHT){
                RectF blank = new RectF();
                if(switchCropBounds(select, blank)){
                    setCropBounds(blank);
                    return;
                }
            }*/
            if (select == MOVE_LEFT) {
                select |= MOVE_TOP;
            }
            if (select == MOVE_TOP) {
                select |= MOVE_LEFT;
            }
            if (select == MOVE_RIGHT) {
                select |= MOVE_BOTTOM;
            }
            if (select == MOVE_BOTTOM) {
                select |= MOVE_RIGHT;
            }
        }

        if (select == MOVE_BLOCK) {
            RectF straight = getRotatedStraightenBounds();
            // Move the whole cropped bounds within the photo display bounds.
            deltaX = (deltaX > 0) ? Math.min(straight.right - cropped.right, deltaX)
                    : Math.max(straight.left - cropped.left, deltaX);
            deltaY = (deltaY > 0) ? Math.min(straight.bottom - cropped.bottom, deltaY)
                    : Math.max(straight.top - cropped.top, deltaY);
            cropped.offset(deltaX, deltaY);
        } else {
            float dx = 0;
            float dy = 0;

            if ((select & MOVE_LEFT) != 0) {
                dx = Math.min(cropped.left + deltaX, cropped.right - minWidthHeight) - cropped.left;
            }
            if ((select & MOVE_TOP) != 0) {
                dy = Math.min(cropped.top + deltaY, cropped.bottom - minWidthHeight) - cropped.top;
            }
            if ((select & MOVE_RIGHT) != 0) {
                dx = Math.max(cropped.right + deltaX, cropped.left + minWidthHeight)
                        - cropped.right;
            }
            if ((select & MOVE_BOTTOM) != 0) {
                dy = Math.max(cropped.bottom + deltaY, cropped.top + minWidthHeight)
                        - cropped.bottom;
            }

            if (mFixAspectRatio) {
                RectF crop = getCropBoundsDisplayed();
                float [] l1 = {crop.left, crop.bottom};
                float [] l2 = {crop.right, crop.top};
                if(movingEdges == TOP_LEFT || movingEdges == BOTTOM_RIGHT){
                    l1[1] = crop.top;
                    l2[1] = crop.bottom;
                }
                float[] b = { l1[0] - l2[0], l1[1] - l2[1] };
                float[] disp = {dx, dy};
                float[] bUnit = GeometryMath.normalize(b);
                float sp = GeometryMath.scalarProjection(disp, bUnit);
                dx = sp * bUnit[0];
                dy = sp * bUnit[1];
                RectF newCrop = fixedCornerResize(crop, select, dx * scale, dy * scale);
                Matrix m = getCropBoundDisplayMatrix();
                Matrix m0 = new Matrix();
                if (!m.invert(m0)){
                    if (LOGV)
                        Log.v(LOGTAG, "FAILED TO INVERT CROP MATRIX");
                    return;
                }
                if (!m0.mapRect(newCrop)){
                    if (LOGV)
                        Log.v(LOGTAG, "FAILED TO MAP RECTANGLE TO RECTANGLE");
                    return;
                }
                setCropBounds(newCrop);
                return;
            } else {
                if ((select & MOVE_LEFT) != 0) {
                    cropped.left += dx;
                }
                if ((select & MOVE_TOP) != 0) {
                    cropped.top += dy;
                }
                if ((select & MOVE_RIGHT) != 0) {
                    cropped.right += dx;
                }
                if ((select & MOVE_BOTTOM) != 0) {
                    cropped.bottom += dy;
                }
            }
        }
        movingEdges = select;
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());
        Matrix m0 = new Matrix();
        if (!m.invert(m0)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO INVERT ROTATION MATRIX");
        }
        if (!m0.mapRect(cropped)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO UNROTATE CROPPING BOUNDS");
        }
        setCropBounds(cropped);
    }

    private void drawIndicator(Canvas canvas, Drawable indicator, float centerX, float centerY) {
        int left = (int) centerX - indicatorSize / 2;
        int top = (int) centerY - indicatorSize / 2;
        indicator.setBounds(left, top, left + indicatorSize, top + indicatorSize);
        indicator.draw(canvas);
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
        detectMovingEdges(x, y);
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        movingEdges = 0;
    }

    @Override
    protected void setActionMove(float x, float y) {
        if (movingEdges != 0){
            moveEdges(x - mCurrentX, y - mCurrentY);
        }
        super.setActionMove(x, y);
    }

    private void cropSetup() {
        if (mFixAspectRatio) {
            RectF cb = getRotatedCropBounds();
            fixAspectRatio(cb, mAspectWidth, mAspectHeight);
            RectF cb0 = getUnrotatedCropBounds(cb);
            setCropBounds(cb0);
        } else {
            setCropBounds(getLocalCropBounds());
        }
    }

    @Override
    public void imageLoaded() {
        super.imageLoaded();
        syncLocalToMasterGeometry();
        applyClear();
        invalidate();
    }

    @Override
    protected void gainedVisibility() {
        float rot = getLocalRotation();
        // if has changed orientation via rotate
        if( ((int) ((rot - mLastRot) / 90)) % 2 != 0 ){
            swapAspect();
        }
        cropSetup();
        mFirstDraw = true;
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        cropSetup();
    }

    @Override
    protected void lostVisibility() {
        mLastRot = getLocalRotation();
    }

    private void drawRuleOfThird(Canvas canvas, RectF bounds, Paint p) {
        float stepX = bounds.width() / 3.0f;
        float stepY = bounds.height() / 3.0f;
        float x = bounds.left + stepX;
        float y = bounds.top + stepY;
        for (int i = 0; i < 2; i++) {
            canvas.drawLine(x, bounds.top, x, bounds.bottom, p);
            x += stepX;
        }
        for (int j = 0; j < 2; j++) {
            canvas.drawLine(bounds.left, y, bounds.right, y, p);
            y += stepY;
        }
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        // TODO: move style to xml
        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);
        gPaint.setARGB(255, 255, 255, 255);

        if (mFirstDraw) {
            cropSetup();
            mFirstDraw = false;
        }
        float rotation = getLocalRotation();

        RectF crop = drawTransformed(canvas, image, gPaint);
        gPaint.setColor(mBorderColor);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);
        drawRuleOfThird(canvas, crop, gPaint);

        if (mFixAspectRatio){
            float w = crop.width();
            float h = crop.height();
            float diag = (float) Math.sqrt(w*w + h*h);

            float dash_len = 20;
            int num_intervals = (int) (diag / dash_len);
            float [] tl = { crop.left, crop.top };
            float centX = tl[0] + w/2;
            float centY = tl[1] + h/2 + 5;
            float [] br = { crop.right, crop.bottom };
            float [] vec = GeometryMath.getUnitVectorFromPoints(tl, br);

            float [] counter = tl;
            for (int x = 0; x < num_intervals; x++ ){
                float tempX = counter[0] + vec[0] * dash_len;
                float tempY = counter[1] + vec[1] * dash_len;
                if ((x % 2) == 0 && Math.abs(x - num_intervals / 2) > 2){
                    canvas.drawLine(counter[0], counter[1], tempX, tempY, gPaint);
                }
                counter[0] = tempX;
                counter[1] = tempY;
            }

            gPaint.setTextAlign(Paint.Align.CENTER);
            gPaint.setTextSize(mAspectTextSize);
            canvas.drawText(mAspect, centX, centY, gPaint);
        }

        gPaint.setColor(mBorderColor);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);
        drawStraighten(canvas, gPaint);

        int decoded_moving = decoder(movingEdges, rotation);
        canvas.save();
        canvas.rotate(rotation, mCenterX, mCenterY);
        RectF scaledCrop = unrotatedCropBounds();
        boolean notMoving = decoded_moving == 0;
        if (((decoded_moving & MOVE_TOP) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.centerX(), scaledCrop.top);
        }
        if (((decoded_moving & MOVE_BOTTOM) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.centerX(), scaledCrop.bottom);
        }
        if (((decoded_moving & MOVE_LEFT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.left, scaledCrop.centerY());
        }
        if (((decoded_moving & MOVE_RIGHT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.right, scaledCrop.centerY());
        }
        canvas.restore();
    }

    private int bitCycleLeft(int x, int times, int d) {
        int mask = (1 << d) - 1;
        int mout = x & mask;
        times %= d;
        int hi = mout >> (d - times);
        int low = (mout << times) & mask;
        int ret = x & ~mask;
        ret |= low;
        ret |= hi;
        return ret;
    }

    protected int decoder(int movingEdges, float rotation) {
        int rot = constrainedRotation(rotation);
        switch (rot) {
            case 90:
                return bitCycleLeft(movingEdges, 3, 4);
            case 180:
                return bitCycleLeft(movingEdges, 2, 4);
            case 270:
                return bitCycleLeft(movingEdges, 1, 4);
            default:
                return movingEdges;
        }
    }
}
