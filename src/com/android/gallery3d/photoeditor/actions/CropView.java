/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.photoeditor.actions;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.gallery3d.R;

/**
 * A view that tracks touch motions and adjusts crop bounds accordingly.
 */
class CropView extends FullscreenToolView {

    /**
     * Listener of crop bounds.
     */
    public interface OnCropChangeListener {

        void onCropChanged(RectF cropBounds, boolean fromUser);
    }

    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;

    private static final int MIN_CROP_WIDTH_HEIGHT = 2;
    private static final int TOUCH_TOLERANCE = 25;
    private static final int SHADOW_ALPHA = 160;

    private final Paint borderPaint;
    private final Drawable cropIndicator;
    private final int indicatorSize;
    private final RectF cropBounds = new RectF(0, 0, 1, 1);

    private float lastX;
    private float lastY;
    private int movingEdges;
    private OnCropChangeListener listener;

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources resources = context.getResources();
        cropIndicator = resources.getDrawable(R.drawable.camera_crop_holo);
        indicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
        int borderColor = resources.getColor(R.color.opaque_cyan);

        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(borderColor);
        borderPaint.setStrokeWidth(2f);
    }

    public void setOnCropChangeListener(OnCropChangeListener listener) {
        this.listener = listener;
    }

    private void refreshByCropChange(boolean fromUser) {
        if (listener != null) {
            listener.onCropChanged(new RectF(cropBounds), fromUser);
        }
        invalidate();
    }

    /**
     * Sets cropped bounds; modifies the bounds if it's smaller than the allowed dimensions.
     */
    public void setCropBounds(RectF bounds) {
        // Avoid cropping smaller than minimum width or height.
        if (bounds.width() * getPhotoWidth() < MIN_CROP_WIDTH_HEIGHT) {
            bounds.set(0, bounds.top, 1, bounds.bottom);
        }
        if (bounds.height() * getPhotoHeight() < MIN_CROP_WIDTH_HEIGHT) {
            bounds.set(bounds.left, 0, bounds.right, 1);
        }
        cropBounds.set(bounds);
        refreshByCropChange(false);
    }

    private RectF getCropBoundsDisplayed() {
        float width = displayBounds.width();
        float height = displayBounds.height();
        RectF cropped = new RectF(cropBounds.left * width, cropBounds.top * height,
                cropBounds.right * width, cropBounds.bottom * height);
        cropped.offset(displayBounds.left, displayBounds.top);
        return cropped;
    }

    private void detectMovingEdges(float x, float y) {
        RectF cropped = getCropBoundsDisplayed();
        movingEdges = 0;

        // Check left or right.
        float left = Math.abs(x - cropped.left);
        float right = Math.abs(x - cropped.right);
        if ((left <= TOUCH_TOLERANCE) && (left < right)) {
            movingEdges |= MOVE_LEFT;
        }
        else if (right <= TOUCH_TOLERANCE) {
            movingEdges |= MOVE_RIGHT;
        }

        // Check top or bottom.
        float top = Math.abs(y - cropped.top);
        float bottom = Math.abs(y - cropped.bottom);
        if ((top <= TOUCH_TOLERANCE) & (top < bottom)) {
            movingEdges |= MOVE_TOP;
        }
        else if (bottom <= TOUCH_TOLERANCE) {
            movingEdges |= MOVE_BOTTOM;
        }

        // Check inside block.
        if (cropped.contains(x, y) && (movingEdges == 0)) {
            movingEdges = MOVE_BLOCK;
        }
        invalidate();
    }

    private void moveEdges(float deltaX, float deltaY) {
        RectF cropped = getCropBoundsDisplayed();
        if (movingEdges == MOVE_BLOCK) {
            // Move the whole cropped bounds within the photo display bounds.
            deltaX = (deltaX > 0) ? Math.min(displayBounds.right - cropped.right, deltaX)
                    : Math.max(displayBounds.left - cropped.left, deltaX);
            deltaY = (deltaY > 0) ? Math.min(displayBounds.bottom - cropped.bottom, deltaY)
                    : Math.max(displayBounds.top - cropped.top, deltaY);
            cropped.offset(deltaX, deltaY);
        } else {
            // Adjust cropped bound dimensions within the photo display bounds.
            float minWidth = MIN_CROP_WIDTH_HEIGHT * displayBounds.width() / getPhotoWidth();
            float minHeight = MIN_CROP_WIDTH_HEIGHT * displayBounds.height() / getPhotoHeight();
            if ((movingEdges & MOVE_LEFT) != 0) {
                cropped.left = Math.min(cropped.left + deltaX, cropped.right - minWidth);
            }
            if ((movingEdges & MOVE_TOP) != 0) {
                cropped.top = Math.min(cropped.top + deltaY, cropped.bottom - minHeight);
            }
            if ((movingEdges & MOVE_RIGHT) != 0) {
                cropped.right = Math.max(cropped.right + deltaX, cropped.left + minWidth);
            }
            if ((movingEdges & MOVE_BOTTOM) != 0) {
                cropped.bottom = Math.max(cropped.bottom + deltaY, cropped.top + minHeight);
            }
            cropped.intersect(displayBounds);
        }
        mapPhotoRect(cropped, cropBounds);
        refreshByCropChange(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        if (isEnabled()) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    detectMovingEdges(x, y);
                    lastX = x;
                    lastY = y;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (movingEdges != 0) {
                        moveEdges(x - lastX, y - lastY);
                    }
                    lastX = x;
                    lastY = y;
                    break;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    movingEdges = 0;
                    invalidate();
                    break;
            }
        }
        return true;
    }

    private void drawIndicator(Canvas canvas, Drawable indicator, float centerX, float centerY) {
        int left = (int) centerX - indicatorSize / 2;
        int top = (int) centerY - indicatorSize / 2;
        indicator.setBounds(left, top, left + indicatorSize, top + indicatorSize);
        indicator.draw(canvas);
    }

    private void drawShadow(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawARGB(SHADOW_ALPHA, 0, 0, 0);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw shadow on non-cropped bounds and the border around cropped bounds.
        RectF cropped = getCropBoundsDisplayed();
        drawShadow(canvas, displayBounds.left, displayBounds.top, displayBounds.right, cropped.top);
        drawShadow(canvas, displayBounds.left, cropped.top, cropped.left, displayBounds.bottom);
        drawShadow(canvas, cropped.right, cropped.top, displayBounds.right, displayBounds.bottom);
        drawShadow(canvas, cropped.left, cropped.bottom, cropped.right, displayBounds.bottom);
        canvas.drawRect(cropped, borderPaint);

        boolean notMoving = movingEdges == 0;
        if (((movingEdges & MOVE_TOP) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, cropped.centerX(), cropped.top);
        }
        if (((movingEdges & MOVE_BOTTOM) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, cropped.centerX(), cropped.bottom);
        }
        if (((movingEdges & MOVE_LEFT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, cropped.left, cropped.centerY());
        }
        if (((movingEdges & MOVE_RIGHT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, cropped.right, cropped.centerY());
        }
    }
}
