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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.crop.BoundedRect;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.filtershow.crop.CropMath;
import com.android.gallery3d.filtershow.editors.EditorCrop;
import com.android.gallery3d.filtershow.ui.FramedTextButton;

public class ImageCrop extends ImageGeometry {
    private static final boolean LOGV = false;

    // Sides
    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;

    // Corners
    private static final int TOP_LEFT = MOVE_TOP | MOVE_LEFT;
    private static final int TOP_RIGHT = MOVE_TOP | MOVE_RIGHT;
    private static final int BOTTOM_RIGHT = MOVE_BOTTOM | MOVE_RIGHT;
    private static final int BOTTOM_LEFT = MOVE_BOTTOM | MOVE_LEFT;

    private static int mMinSideSize = 100;
    private static int mTouchTolerance = 45;

    private boolean mFirstDraw = true;
    private float mAspectWidth = 1;
    private float mAspectHeight = 1;
    private boolean mFixAspectRatio = false;

    private float mLastRot = 0;

    private BoundedRect mBounded = null;
    private int movingEdges;
    private final Drawable cropIndicator;
    private final int indicatorSize;
    private final int mBorderColor = Color.argb(128, 255, 255, 255);

    // Offset between crop center and photo center
    private float[] mOffset = {
            0, 0
    };
    private CropExtras mCropExtras = null;
    private boolean mDoingCropIntentAction = false;

    private static final String LOGTAG = "ImageCrop";

    private String mAspect = "";
    private static int mAspectTextSize = 24;

    private boolean mFixedAspect = false;

    private EditorCrop mEditorCrop;

    public static void setAspectTextSize(int textSize) {
        mAspectTextSize = textSize;
    }

    public void setAspectString(String a) {
        mAspect = a;
    }

    private static final Paint gPaint = new Paint();

    public ImageCrop(Context context) {
        super(context);
        Resources resources = context.getResources();
        cropIndicator = resources.getDrawable(R.drawable.camera_crop);
        indicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
    }

    public ImageCrop(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        cropIndicator = resources.getDrawable(R.drawable.camera_crop);
        indicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
    }

    @Override
    public String getName() {
        return getContext().getString(R.string.crop);
    }

    private void swapAspect() {
        if (mDoingCropIntentAction) {
            return;
        }
        float temp = mAspectWidth;
        mAspectWidth = mAspectHeight;
        mAspectHeight = temp;
    }

    /**
     * Set tolerance for crop marker selection (in pixels)
     */
    public static void setTouchTolerance(int tolerance) {
        mTouchTolerance = tolerance;
    }

    /**
     * Set minimum side length for crop box (in pixels)
     */
    public static void setMinCropSize(int minHeightWidth) {
        mMinSideSize = minHeightWidth;
    }

    public void setExtras(CropExtras e) {
        mCropExtras = e;
    }

    public void setCropActionFlag(boolean f) {
        mDoingCropIntentAction = f;
    }

    public void apply(float w, float h) {
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
        mAspectWidth = 1;
        mAspectHeight = 1;
        setLocalCropBounds(getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten()));
        cropSetup();
        saveAndSetPreset();
        invalidate();
    }

    public void clear() {
        if (mCropExtras != null) {
            int x = mCropExtras.getAspectX();
            int y = mCropExtras.getAspectY();
            if (mDoingCropIntentAction && x > 0 && y > 0) {
                apply(x, y);
            }
        } else {
            applyClear();
        }
    }

    private Matrix getPhotoBoundDisplayedMatrix() {
        float[] displayCenter = new float[2];
        RectF scaledCrop = new RectF();
        RectF scaledPhoto = new RectF();
        float scale = getTransformState(scaledPhoto, scaledCrop, displayCenter);
        Matrix m = GeometryMetadata.buildCenteredPhotoMatrix(scaledPhoto, scaledCrop,
                getLocalRotation(), getLocalStraighten(), getLocalFlip(), displayCenter);
        m.preScale(scale, scale);
        return m;
    }

    private Matrix getCropBoundDisplayedMatrix() {
        float[] displayCenter = new float[2];
        RectF scaledCrop = new RectF();
        RectF scaledPhoto = new RectF();
        float scale = getTransformState(scaledPhoto, scaledCrop, displayCenter);
        Matrix m1 = GeometryMetadata.buildWanderingCropMatrix(scaledPhoto, scaledCrop,
                getLocalRotation(), getLocalStraighten(), getLocalFlip(), displayCenter);
        m1.preScale(scale, scale);
        return m1;
    }

    /**
     * Takes the rotated corners of a rectangle and returns the angle; sets
     * unrotated to be the unrotated version of the rectangle.
     */
    private static float getUnrotated(float[] rotatedRect, float[] center, RectF unrotated) {
        float dy = rotatedRect[1] - rotatedRect[3];
        float dx = rotatedRect[0] - rotatedRect[2];
        float angle = (float) (Math.atan(dy / dx) * 180 / Math.PI);
        Matrix m = new Matrix();
        m.setRotate(-angle, center[0], center[1]);
        float[] unrotatedRect = new float[rotatedRect.length];
        m.mapPoints(unrotatedRect, rotatedRect);
        unrotated.set(CropMath.trapToRect(unrotatedRect));
        return angle;
    }

    /**
     * Sets cropped bounds; modifies the bounds if it's smaller than the allowed
     * dimensions.
     */
    public boolean setCropBounds(RectF bounds) {
        RectF cbounds = new RectF(bounds);
        Matrix mc = getCropBoundDisplayedMatrix();
        Matrix mcInv = new Matrix();
        mc.invert(mcInv);
        mcInv.mapRect(cbounds);
        // Avoid cropping smaller than minimum
        float newWidth = cbounds.width();
        float newHeight = cbounds.height();
        float scale = getTransformState(null, null, null);
        float minWidthHeight = mMinSideSize / scale;
        RectF pbounds = getLocalPhotoBounds();

        // if photo is smaller than minimum, refuse to set crop bounds
        if (pbounds.width() < minWidthHeight || pbounds.height() < minWidthHeight) {
            return false;
        }

        // if incoming crop is smaller than minimum, refuse to set crop bounds
        if (newWidth < minWidthHeight || newHeight < minWidthHeight) {
            return false;
        }

        float newX = bounds.centerX() - (getWidth() / 2f);
        float newY = bounds.centerY() - (getHeight() / 2f);
        mOffset[0] = newX;
        mOffset[1] = newY;

        setLocalCropBounds(cbounds);
        invalidate();
        return true;
    }

    private BoundedRect getBoundedCrop(RectF crop) {
        RectF photo = getLocalPhotoBounds();
        Matrix mp = getPhotoBoundDisplayedMatrix();
        float[] photoCorners = CropMath.getCornersFromRect(photo);
        float[] photoCenter = {
                photo.centerX(), photo.centerY()
        };
        mp.mapPoints(photoCorners);
        mp.mapPoints(photoCenter);
        RectF scaledPhoto = new RectF();
        float angle = getUnrotated(photoCorners, photoCenter, scaledPhoto);
        return new BoundedRect(angle, scaledPhoto, crop);
    }

    private void detectMovingEdges(float x, float y) {
        Matrix m = getCropBoundDisplayedMatrix();
        RectF cropped = getLocalCropBounds();
        m.mapRect(cropped);
        mBounded = getBoundedCrop(cropped);
        movingEdges = 0;

        float left = Math.abs(x - cropped.left);
        float right = Math.abs(x - cropped.right);
        float top = Math.abs(y - cropped.top);
        float bottom = Math.abs(y - cropped.bottom);

        // Check left or right.
        if ((left <= mTouchTolerance) && ((y + mTouchTolerance) >= cropped.top)
                && ((y - mTouchTolerance) <= cropped.bottom) && (left < right)) {
            movingEdges |= MOVE_LEFT;
        }
        else if ((right <= mTouchTolerance) && ((y + mTouchTolerance) >= cropped.top)
                && ((y - mTouchTolerance) <= cropped.bottom)) {
            movingEdges |= MOVE_RIGHT;
        }

        // Check top or bottom.
        if ((top <= mTouchTolerance) && ((x + mTouchTolerance) >= cropped.left)
                && ((x - mTouchTolerance) <= cropped.right) && (top < bottom)) {
            movingEdges |= MOVE_TOP;
        }
        else if ((bottom <= mTouchTolerance) && ((x + mTouchTolerance) >= cropped.left)
                && ((x - mTouchTolerance) <= cropped.right)) {
            movingEdges |= MOVE_BOTTOM;
        }
        if (movingEdges == 0) {
            movingEdges = MOVE_BLOCK;
        }
        if (mFixAspectRatio && (movingEdges != MOVE_BLOCK)) {
            movingEdges = fixEdgeToCorner(movingEdges);
        }
        invalidate();
    }

    private int fixEdgeToCorner(int moving_edges) {
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

    private RectF fixedCornerResize(RectF r, int moving_corner, float dx, float dy) {
        RectF newCrop = null;
        // Fix opposite corner in place and move sides
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
        RectF crop = mBounded.getInner();

        Matrix mc = getCropBoundDisplayedMatrix();

        RectF photo = getLocalPhotoBounds();
        Matrix mp = getPhotoBoundDisplayedMatrix();
        float[] photoCorners = CropMath.getCornersFromRect(photo);
        float[] photoCenter = {
                photo.centerX(), photo.centerY()
        };
        mp.mapPoints(photoCorners);
        mp.mapPoints(photoCenter);

        float minWidthHeight = mMinSideSize;

        if (movingEdges == MOVE_BLOCK) {
            mBounded.moveInner(-dX, -dY);
            RectF r = mBounded.getInner();
            setCropBounds(r);
            return;
        } else {
            float dx = 0;
            float dy = 0;

            if ((movingEdges & MOVE_LEFT) != 0) {
                dx = Math.min(crop.left + dX, crop.right - minWidthHeight) - crop.left;
            }
            if ((movingEdges & MOVE_TOP) != 0) {
                dy = Math.min(crop.top + dY, crop.bottom - minWidthHeight) - crop.top;
            }
            if ((movingEdges & MOVE_RIGHT) != 0) {
                dx = Math.max(crop.right + dX, crop.left + minWidthHeight)
                        - crop.right;
            }
            if ((movingEdges & MOVE_BOTTOM) != 0) {
                dy = Math.max(crop.bottom + dY, crop.top + minWidthHeight)
                        - crop.bottom;
            }

            if (mFixAspectRatio) {
                float[] l1 = {
                        crop.left, crop.bottom
                };
                float[] l2 = {
                        crop.right, crop.top
                };
                if (movingEdges == TOP_LEFT || movingEdges == BOTTOM_RIGHT) {
                    l1[1] = crop.top;
                    l2[1] = crop.bottom;
                }
                float[] b = {
                        l1[0] - l2[0], l1[1] - l2[1]
                };
                float[] disp = {
                        dx, dy
                };
                float[] bUnit = GeometryMath.normalize(b);
                float sp = GeometryMath.scalarProjection(disp, bUnit);
                dx = sp * bUnit[0];
                dy = sp * bUnit[1];
                RectF newCrop = fixedCornerResize(crop, movingEdges, dx, dy);

                mBounded.fixedAspectResizeInner(newCrop);
                newCrop = mBounded.getInner();
                setCropBounds(newCrop);
                return;
            } else {
                if ((movingEdges & MOVE_LEFT) != 0) {
                    crop.left += dx;
                }
                if ((movingEdges & MOVE_TOP) != 0) {
                    crop.top += dy;
                }
                if ((movingEdges & MOVE_RIGHT) != 0) {
                    crop.right += dx;
                }
                if ((movingEdges & MOVE_BOTTOM) != 0) {
                    crop.bottom += dy;
                }
            }
        }
        mBounded.resizeInner(crop);
        crop = mBounded.getInner();
        setCropBounds(crop);
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
        detectMovingEdges(x + mOffset[0], y + mOffset[1]);

    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        movingEdges = 0;
    }

    @Override
    protected void setActionMove(float x, float y) {

        if (movingEdges != 0) {
            moveEdges(x - mCurrentX, y - mCurrentY);
        }
        super.setActionMove(x, y);

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        setActionUp();
        cropSetup();
        invalidate();
    }

    private void cropSetup() {
        RectF crop = getLocalCropBounds();
        Matrix m = getCropBoundDisplayedMatrix();
        m.mapRect(crop);
        if (mFixAspectRatio) {
            CropMath.fixAspectRatio(crop, mAspectWidth, mAspectHeight);
        }
        float dCentX = getWidth() / 2;
        float dCentY = getHeight() / 2;

        BoundedRect r = getBoundedCrop(crop);
        crop = r.getInner();
        if (!setCropBounds(crop)) {
            float h = mMinSideSize / 2;
            float wScale = 1;
            float hScale = mAspectHeight / mAspectWidth;
            if (hScale < 1) {
                wScale = mAspectWidth / mAspectHeight;
                hScale = 1;
            }
            crop.set(dCentX - h * wScale, dCentY - h * hScale, dCentX + h * wScale, dCentY + h
                    * hScale);
            if (mFixAspectRatio) {
                CropMath.fixAspectRatio(crop, mAspectWidth, mAspectHeight);
            }
            r.setInner(crop);
            crop = r.getInner();
            if (!setCropBounds(crop)) {
                crop.set(dCentX - h, dCentY - h, dCentX + h, dCentY + h);
                r.setInner(crop);
                crop = r.getInner();
                setCropBounds(crop);
            }
        }
    }

    @Override
    public void imageLoaded() {
        super.imageLoaded();
        syncLocalToMasterGeometry();
        clear();
        invalidate();
    }

    @Override
    protected void gainedVisibility() {
        float rot = getLocalRotation();
        // if has changed orientation via rotate
        if (((int) ((rot - mLastRot) / 90)) % 2 != 0) {
            swapAspect();
        }
        cropSetup();
        mFirstDraw = true;
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
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
        gPaint.setAntiAlias(true);
        gPaint.setARGB(255, 255, 255, 255);

        if (mFirstDraw) {
            cropSetup();
            mFirstDraw = false;
        }

        RectF crop = drawTransformed(canvas, image, gPaint, mOffset);
        gPaint.setColor(mBorderColor);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);

        boolean doThirds = true;

        if (mFixAspectRatio) {
            float spotlightX = 0;
            float spotlightY = 0;
            if (mCropExtras != null) {
                spotlightX = mCropExtras.getSpotlightX();
                spotlightY = mCropExtras.getSpotlightY();
            }
            if (mDoingCropIntentAction && spotlightX > 0 && spotlightY > 0) {
                float sx = crop.width() * spotlightX;
                float sy = crop.height() * spotlightY;
                float cx = crop.centerX();
                float cy = crop.centerY();
                RectF r1 = new RectF(cx - sx / 2, cy - sy / 2, cx + sx / 2, cy + sy / 2);
                float temp = sx;
                sx = sy;
                sy = temp;
                RectF r2 = new RectF(cx - sx / 2, cy - sy / 2, cx + sx / 2, cy + sy / 2);
                canvas.drawRect(r1, gPaint);
                canvas.drawRect(r2, gPaint);
                doThirds = false;
            } else {
                float w = crop.width();
                float h = crop.height();
                float diag = (float) Math.sqrt(w * w + h * h);

                float dash_len = 20;
                int num_intervals = (int) (diag / dash_len);
                float[] tl = {
                        crop.left, crop.top
                };
                float centX = tl[0] + w / 2;
                float centY = tl[1] + h / 2 + 5;
                float[] br = {
                        crop.right, crop.bottom
                };
                float[] vec = GeometryMath.getUnitVectorFromPoints(tl, br);

                float[] counter = tl;
                for (int x = 0; x < num_intervals; x++) {
                    float tempX = counter[0] + vec[0] * dash_len;
                    float tempY = counter[1] + vec[1] * dash_len;
                    if ((x % 2) == 0 && Math.abs(x - num_intervals / 2) > 2) {
                        canvas.drawLine(counter[0], counter[1], tempX, tempY, gPaint);
                    }
                    counter[0] = tempX;
                    counter[1] = tempY;
                }

                gPaint.setTextAlign(Paint.Align.CENTER);
                gPaint.setTextSize(mAspectTextSize);
                canvas.drawText(mAspect, centX, centY, gPaint);
            }
        }

        if (doThirds) {
            drawRuleOfThird(canvas, crop, gPaint);

        }

        RectF scaledCrop = crop;
        boolean notMoving = (movingEdges == 0);
        if (mFixAspectRatio) {
            if ((movingEdges == TOP_LEFT) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.left, scaledCrop.top);
            }
            if ((movingEdges == TOP_RIGHT) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.right, scaledCrop.top);
            }
            if ((movingEdges == BOTTOM_LEFT) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.left, scaledCrop.bottom);
            }
            if ((movingEdges == BOTTOM_RIGHT) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.right, scaledCrop.bottom);
            }
        } else {
            if (((movingEdges & MOVE_TOP) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.centerX(), scaledCrop.top);
            }
            if (((movingEdges & MOVE_BOTTOM) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.centerX(), scaledCrop.bottom);
            }
            if (((movingEdges & MOVE_LEFT) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.left, scaledCrop.centerY());
            }
            if (((movingEdges & MOVE_RIGHT) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, scaledCrop.right, scaledCrop.centerY());
            }
        }
    }

    public void setAspectButton(int itemId) {
        switch (itemId) {
            case R.id.crop_menu_1to1: {
                String t = getActivity().getString(R.string.aspect1to1_effect);
                apply(1, 1);
                setAspectString(t);
                break;
            }
            case R.id.crop_menu_4to3: {
                String t = getActivity().getString(R.string.aspect4to3_effect);
                apply(4, 3);
                setAspectString(t);
                break;
            }
            case R.id.crop_menu_3to4: {
                String t = getActivity().getString(R.string.aspect3to4_effect);
                apply(3, 4);
                setAspectString(t);
                break;
            }
            case R.id.crop_menu_5to7: {
                String t = getActivity().getString(R.string.aspect5to7_effect);
                apply(5, 7);
                setAspectString(t);
                break;
            }
            case R.id.crop_menu_7to5: {
                String t = getActivity().getString(R.string.aspect7to5_effect);
                apply(7, 5);
                setAspectString(t);
                break;
            }
            case R.id.crop_menu_none: {
                String t = getActivity().getString(R.string.aspectNone_effect);
                applyClear();
                setAspectString(t);
                break;
            }
            case R.id.crop_menu_original: {
                String t = getActivity().getString(R.string.aspectOriginal_effect);
                applyOriginal();
                setAspectString(t);
                break;
            }
        }
        invalidate();
    }

    public void setFixedAspect(boolean fixedAspect) {
        mFixedAspect = fixedAspect;
    }

    @Override
    public boolean useUtilityPanel() {
        // Only shows the aspect ratio popup if we are not fixed
        return !mFixedAspect;
    }

    public void setEditor(EditorCrop editorCrop) {
        mEditorCrop = editorCrop;
    }

}
