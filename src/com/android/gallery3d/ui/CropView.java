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

package com.android.gallery3d.ui;

import com.android.gallery3d.R;
import com.android.gallery3d.anim.Animation;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import java.util.ArrayList;
import javax.microedition.khronos.opengles.GL11;

/**
 * The activity can crop specific region of interest from an image.
 */
public class CropView extends GLView {
    private static final String TAG = "CropView";

    private static final int FACE_PIXEL_COUNT = 120000; // around 400x300

    private static final int COLOR_OUTLINE = 0xFF008AFF;
    private static final int COLOR_FACE_OUTLINE = 0xFF000000;

    private static final float OUTLINE_WIDTH = 3f;

    private static final int SIZE_UNKNOWN = -1;
    private static final int TOUCH_TOLERANCE = 30;

    private static final float MIN_SELECTION_LENGTH = 16f;
    public static final float UNSPECIFIED = -1f;

    private static final int MAX_FACE_COUNT = 3;
    private static final float FACE_EYE_RATIO = 2f;

    private static final int ANIMATION_DURATION = 1250;

    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;

    private static final float MAX_SELECTION_RATIO = 0.8f;
    private static final float MIN_SELECTION_RATIO = 0.4f;
    private static final float SELECTION_RATIO = 0.60f;
    private static final int ANIMATION_TRIGGER = 64;

    private static final int MSG_UPDATE_FACES = 1;

    private float mAspectRatio = UNSPECIFIED;
    private float mSpotlightRatioX = 0;
    private float mSpotlightRatioY = 0;

    private Handler mMainHandler;

    private FaceHighlightView mFaceDetectionView;
    private HighlightRectangle mHighlightRectangle;
    private TileImageView mImageView;
    private AnimationController mAnimation = new AnimationController();

    private int mImageWidth = SIZE_UNKNOWN;
    private int mImageHeight = SIZE_UNKNOWN;

    private GalleryActivity mActivity;

    private GLPaint mPaint = new GLPaint();
    private GLPaint mFacePaint = new GLPaint();

    private int mImageRotation;

    public CropView(GalleryActivity activity) {
        mActivity = activity;
        mImageView = new TileImageView(activity);
        mFaceDetectionView = new FaceHighlightView();
        mHighlightRectangle = new HighlightRectangle();

        addComponent(mImageView);
        addComponent(mFaceDetectionView);
        addComponent(mHighlightRectangle);

        mHighlightRectangle.setVisibility(GLView.INVISIBLE);

        mPaint.setColor(COLOR_OUTLINE);
        mPaint.setLineWidth(OUTLINE_WIDTH);

        mFacePaint.setColor(COLOR_FACE_OUTLINE);
        mFacePaint.setLineWidth(OUTLINE_WIDTH);

        mMainHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                Utils.assertTrue(message.what == MSG_UPDATE_FACES);
                ((DetectFaceTask) message.obj).updateFaces();
            }
        };
    }

    public void setAspectRatio(float ratio) {
        mAspectRatio = ratio;
    }

    public void setSpotlightRatio(float ratioX, float ratioY) {
        mSpotlightRatioX = ratioX;
        mSpotlightRatioY = ratioY;
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        mFaceDetectionView.layout(0, 0, width, height);
        mHighlightRectangle.layout(0, 0, width, height);
        mImageView.layout(0, 0, width, height);
        if (mImageHeight != SIZE_UNKNOWN) {
            mAnimation.initialize();
            if (mHighlightRectangle.getVisibility() == GLView.VISIBLE) {
                mAnimation.parkNow(
                        mHighlightRectangle.mHighlightRect);
            }
        }
    }

    private boolean setImageViewPosition(int centerX, int centerY, float scale) {
        int inverseX = mImageWidth - centerX;
        int inverseY = mImageHeight - centerY;
        TileImageView t = mImageView;
        int rotation = mImageRotation;
        switch (rotation) {
            case 0: return t.setPosition(centerX, centerY, scale, 0);
            case 90: return t.setPosition(centerY, inverseX, scale, 90);
            case 180: return t.setPosition(inverseX, inverseY, scale, 180);
            case 270: return t.setPosition(inverseY, centerX, scale, 270);
            default: throw new IllegalArgumentException(String.valueOf(rotation));
        }
    }

    @Override
    public void render(GLCanvas canvas) {
        AnimationController a = mAnimation;
        if (a.calculate(canvas.currentAnimationTimeMillis())) invalidate();
        setImageViewPosition(a.getCenterX(), a.getCenterY(), a.getScale());
        super.render(canvas);
    }

    @Override
    public void renderBackground(GLCanvas canvas) {
        canvas.clearBuffer();
    }

    public RectF getCropRectangle() {
        if (mHighlightRectangle.getVisibility() == GLView.INVISIBLE) return null;
        RectF rect = mHighlightRectangle.mHighlightRect;
        RectF result = new RectF(rect.left * mImageWidth, rect.top * mImageHeight,
                rect.right * mImageWidth, rect.bottom * mImageHeight);
        return result;
    }

    public int getImageWidth() {
        return mImageWidth;
    }

    public int getImageHeight() {
        return mImageHeight;
    }

    private class FaceHighlightView extends GLView {
        private static final int INDEX_NONE = -1;
        private ArrayList<RectF> mFaces = new ArrayList<RectF>();
        private RectF mRect = new RectF();
        private int mPressedFaceIndex = INDEX_NONE;

        public void addFace(RectF faceRect) {
            mFaces.add(faceRect);
            invalidate();
        }

        private void renderFace(GLCanvas canvas, RectF face, boolean pressed) {
            GL11 gl = canvas.getGLInstance();
            if (pressed) {
                gl.glEnable(GL11.GL_STENCIL_TEST);
                gl.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                gl.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
            }

            RectF r = mAnimation.mapRect(face, mRect);
            canvas.fillRect(r.left, r.top, r.width(), r.height(), Color.TRANSPARENT);
            canvas.drawRect(r.left, r.top, r.width(), r.height(), mFacePaint);

            if (pressed) {
                gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            }
        }

        @Override
        protected void renderBackground(GLCanvas canvas) {
            ArrayList<RectF> faces = mFaces;
            for (int i = 0, n = faces.size(); i < n; ++i) {
                renderFace(canvas, faces.get(i), i == mPressedFaceIndex);
            }

            GL11 gl = canvas.getGLInstance();
            if (mPressedFaceIndex != INDEX_NONE) {
                gl.glStencilFunc(GL11.GL_NOTEQUAL, 1, 1);
                canvas.fillRect(0, 0, getWidth(), getHeight(), 0x66000000);
                gl.glDisable(GL11.GL_STENCIL_TEST);
            }
        }

        private void setPressedFace(int index) {
            if (mPressedFaceIndex == index) return;
            mPressedFaceIndex = index;
            invalidate();
        }

        private int getFaceIndexByPosition(float x, float y) {
            ArrayList<RectF> faces = mFaces;
            for (int i = 0, n = faces.size(); i < n; ++i) {
                RectF r = mAnimation.mapRect(faces.get(i), mRect);
                if (r.contains(x, y)) return i;
            }
            return INDEX_NONE;
        }

        @Override
        protected boolean onTouch(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    setPressedFace(getFaceIndexByPosition(x, y));
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    int index = mPressedFaceIndex;
                    setPressedFace(INDEX_NONE);
                    if (index != INDEX_NONE) {
                        mHighlightRectangle.setRectangle(mFaces.get(index));
                        mHighlightRectangle.setVisibility(GLView.VISIBLE);
                        setVisibility(GLView.INVISIBLE);
                    }
                }
            }
            return true;
        }
    }

    private class AnimationController extends Animation {
        private int mCurrentX;
        private int mCurrentY;
        private float mCurrentScale;
        private int mStartX;
        private int mStartY;
        private float mStartScale;
        private int mTargetX;
        private int mTargetY;
        private float mTargetScale;

        public AnimationController() {
            setDuration(ANIMATION_DURATION);
            setInterpolator(new DecelerateInterpolator(4));
        }

        public void initialize() {
            mCurrentX = mImageWidth / 2;
            mCurrentY = mImageHeight / 2;
            mCurrentScale = Math.min(2, Math.min(
                    (float) getWidth() / mImageWidth,
                    (float) getHeight() / mImageHeight));
        }

        public void startParkingAnimation(RectF highlight) {
            RectF r = mAnimation.mapRect(highlight, new RectF());
            int width = getWidth();
            int height = getHeight();

            float wr = r.width() / width;
            float hr = r.height() / height;
            final int d = ANIMATION_TRIGGER;
            if (wr >= MIN_SELECTION_RATIO && wr < MAX_SELECTION_RATIO
                    && hr >= MIN_SELECTION_RATIO && hr < MAX_SELECTION_RATIO
                    && r.left >= d && r.right < width - d
                    && r.top >= d && r.bottom < height - d) return;

            mStartX = mCurrentX;
            mStartY = mCurrentY;
            mStartScale = mCurrentScale;
            calculateTarget(highlight);
            start();
        }

        public void parkNow(RectF highlight) {
            calculateTarget(highlight);
            forceStop();
            mStartX = mCurrentX = mTargetX;
            mStartY = mCurrentY = mTargetY;
            mStartScale = mCurrentScale = mTargetScale;
        }

        public void inverseMapPoint(PointF point) {
            float s = mCurrentScale;
            point.x = Utils.clamp(((point.x - getWidth() * 0.5f) / s
                    + mCurrentX) / mImageWidth, 0, 1);
            point.y = Utils.clamp(((point.y - getHeight() * 0.5f) / s
                    + mCurrentY) / mImageHeight, 0, 1);
        }

        public RectF mapRect(RectF input, RectF output) {
            float offsetX = getWidth() * 0.5f;
            float offsetY = getHeight() * 0.5f;
            int x = mCurrentX;
            int y = mCurrentY;
            float s = mCurrentScale;
            output.set(
                    offsetX + (input.left * mImageWidth - x) * s,
                    offsetY + (input.top * mImageHeight - y) * s,
                    offsetX + (input.right * mImageWidth - x) * s,
                    offsetY + (input.bottom * mImageHeight - y) * s);
            return output;
        }

        @Override
        protected void onCalculate(float progress) {
            mCurrentX = Math.round(mStartX + (mTargetX - mStartX) * progress);
            mCurrentY = Math.round(mStartY + (mTargetY - mStartY) * progress);
            mCurrentScale = mStartScale + (mTargetScale - mStartScale) * progress;

            if (mCurrentX == mTargetX && mCurrentY == mTargetY
                    && mCurrentScale == mTargetScale) forceStop();
        }

        public int getCenterX() {
            return mCurrentX;
        }

        public int getCenterY() {
            return mCurrentY;
        }

        public float getScale() {
            return mCurrentScale;
        }

        private void calculateTarget(RectF highlight) {
            float width = getWidth();
            float height = getHeight();

            if (mImageWidth != SIZE_UNKNOWN) {
                float minScale = Math.min(width / mImageWidth, height / mImageHeight);
                float scale = Utils.clamp(SELECTION_RATIO * Math.min(
                        width / (highlight.width() * mImageWidth),
                        height / (highlight.height() * mImageHeight)), minScale, 2f);
                int centerX = Math.round(
                        mImageWidth * (highlight.left + highlight.right) * 0.5f);
                int centerY = Math.round(
                        mImageHeight * (highlight.top + highlight.bottom) * 0.5f);

                if (Math.round(mImageWidth * scale) > width) {
                    int limitX = Math.round(width * 0.5f / scale);
                    centerX = Math.round(
                            (highlight.left + highlight.right) * mImageWidth / 2);
                    centerX = Utils.clamp(centerX, limitX, mImageWidth - limitX);
                } else {
                    centerX = mImageWidth / 2;
                }
                if (Math.round(mImageHeight * scale) > height) {
                    int limitY = Math.round(height * 0.5f / scale);
                    centerY = Math.round(
                            (highlight.top + highlight.bottom) * mImageHeight / 2);
                    centerY = Utils.clamp(centerY, limitY, mImageHeight - limitY);
                } else {
                    centerY = mImageHeight / 2;
                }
                mTargetX = centerX;
                mTargetY = centerY;
                mTargetScale = scale;
            }
        }

    }

    private class HighlightRectangle extends GLView {
        private RectF mHighlightRect = new RectF(0.25f, 0.25f, 0.75f, 0.75f);
        private RectF mTempRect = new RectF();
        private PointF mTempPoint = new PointF();

        private ResourceTexture mArrow;

        private int mMovingEdges = 0;
        private float mReferenceX;
        private float mReferenceY;

        public HighlightRectangle() {
            mArrow = new ResourceTexture(mActivity.getAndroidContext(),
                    R.drawable.camera_crop_holo);
        }

        public void setInitRectangle() {
            float targetRatio = mAspectRatio == UNSPECIFIED
                    ? 1f
                    : mAspectRatio * mImageHeight / mImageWidth;
            float w = SELECTION_RATIO / 2f;
            float h = SELECTION_RATIO / 2f;
            if (targetRatio > 1) {
                h = w / targetRatio;
            } else {
                w = h * targetRatio;
            }
            mHighlightRect.set(0.5f - w, 0.5f - h, 0.5f + w, 0.5f + h);
        }

        public void setRectangle(RectF faceRect) {
            mHighlightRect.set(faceRect);
            mAnimation.startParkingAnimation(faceRect);
            invalidate();
        }

        private void moveEdges(MotionEvent event) {
            float scale = mAnimation.getScale();
            float dx = (event.getX() - mReferenceX) / scale / mImageWidth;
            float dy = (event.getY() - mReferenceY) / scale / mImageHeight;
            mReferenceX = event.getX();
            mReferenceY = event.getY();
            RectF r = mHighlightRect;

            if ((mMovingEdges & MOVE_BLOCK) != 0) {
                dx = Utils.clamp(dx, -r.left,  1 - r.right);
                dy = Utils.clamp(dy, -r.top , 1 - r.bottom);
                r.top += dy;
                r.bottom += dy;
                r.left += dx;
                r.right += dx;
            } else {
                PointF point = mTempPoint;
                point.set(mReferenceX, mReferenceY);
                mAnimation.inverseMapPoint(point);
                float left = r.left + MIN_SELECTION_LENGTH / mImageWidth;
                float right = r.right - MIN_SELECTION_LENGTH / mImageWidth;
                float top = r.top + MIN_SELECTION_LENGTH / mImageHeight;
                float bottom = r.bottom - MIN_SELECTION_LENGTH / mImageHeight;
                if ((mMovingEdges & MOVE_RIGHT) != 0) {
                    r.right = Utils.clamp(point.x, left, 1f);
                }
                if ((mMovingEdges & MOVE_LEFT) != 0) {
                    r.left = Utils.clamp(point.x, 0, right);
                }
                if ((mMovingEdges & MOVE_TOP) != 0) {
                    r.top = Utils.clamp(point.y, 0, bottom);
                }
                if ((mMovingEdges & MOVE_BOTTOM) != 0) {
                    r.bottom = Utils.clamp(point.y, top, 1f);
                }
                if (mAspectRatio != UNSPECIFIED) {
                    float targetRatio = mAspectRatio * mImageHeight / mImageWidth;
                    if (r.width() / r.height() > targetRatio) {
                        float height = r.width() / targetRatio;
                        if ((mMovingEdges & MOVE_BOTTOM) != 0) {
                            r.bottom = Utils.clamp(r.top + height, top, 1f);
                        } else {
                            r.top = Utils.clamp(r.bottom - height, 0, bottom);
                        }
                    } else {
                        float width = r.height() * targetRatio;
                        if ((mMovingEdges & MOVE_LEFT) != 0) {
                            r.left = Utils.clamp(r.right - width, 0, right);
                        } else {
                            r.right = Utils.clamp(r.left + width, left, 1f);
                        }
                    }
                    if (r.width() / r.height() > targetRatio) {
                        float width = r.height() * targetRatio;
                        if ((mMovingEdges & MOVE_LEFT) != 0) {
                            r.left = Utils.clamp(r.right - width, 0, right);
                        } else {
                            r.right = Utils.clamp(r.left + width, left, 1f);
                        }
                    } else {
                        float height = r.width() / targetRatio;
                        if ((mMovingEdges & MOVE_BOTTOM) != 0) {
                            r.bottom = Utils.clamp(r.top + height, top, 1f);
                        } else {
                            r.top = Utils.clamp(r.bottom - height, 0, bottom);
                        }
                    }
                }
            }
            invalidate();
        }

        private void setMovingEdges(MotionEvent event) {
            RectF r = mAnimation.mapRect(mHighlightRect, mTempRect);
            float x = event.getX();
            float y = event.getY();

            if (x > r.left + TOUCH_TOLERANCE && x < r.right - TOUCH_TOLERANCE
                    && y > r.top + TOUCH_TOLERANCE && y < r.bottom - TOUCH_TOLERANCE) {
                mMovingEdges = MOVE_BLOCK;
                return;
            }

            boolean inVerticalRange = (r.top - TOUCH_TOLERANCE) <= y
                    && y <= (r.bottom + TOUCH_TOLERANCE);
            boolean inHorizontalRange = (r.left - TOUCH_TOLERANCE) <= x
                    && x <= (r.right + TOUCH_TOLERANCE);

            if (inVerticalRange) {
                boolean left = Math.abs(x - r.left) <= TOUCH_TOLERANCE;
                boolean right = Math.abs(x - r.right) <= TOUCH_TOLERANCE;
                if (left && right) {
                    left = Math.abs(x - r.left) < Math.abs(x - r.right);
                    right = !left;
                }
                if (left) mMovingEdges |= MOVE_LEFT;
                if (right) mMovingEdges |= MOVE_RIGHT;
                if (mAspectRatio != UNSPECIFIED && inHorizontalRange) {
                    mMovingEdges |= (y >
                            (r.top + r.bottom) / 2) ? MOVE_BOTTOM : MOVE_TOP;
                }
            }
            if (inHorizontalRange) {
                boolean top = Math.abs(y - r.top) <= TOUCH_TOLERANCE;
                boolean bottom = Math.abs(y - r.bottom) <= TOUCH_TOLERANCE;
                if (top && bottom) {
                    top = Math.abs(y - r.top) < Math.abs(y - r.bottom);
                    bottom = !top;
                }
                if (top) mMovingEdges |= MOVE_TOP;
                if (bottom) mMovingEdges |= MOVE_BOTTOM;
                if (mAspectRatio != UNSPECIFIED && inVerticalRange) {
                    mMovingEdges |= (x >
                            (r.left + r.right) / 2) ? MOVE_RIGHT : MOVE_LEFT;
                }
            }
        }

        @Override
        protected boolean onTouch(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    mReferenceX = event.getX();
                    mReferenceY = event.getY();
                    setMovingEdges(event);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    moveEdges(event);
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    mMovingEdges = 0;
                    mAnimation.startParkingAnimation(mHighlightRect);
                    invalidate();
                    return true;
                }
            }
            return true;
        }

        @Override
        protected void renderBackground(GLCanvas canvas) {
            RectF r = mAnimation.mapRect(mHighlightRect, mTempRect);
            drawHighlightRectangle(canvas, r);

            float centerY = (r.top + r.bottom) / 2;
            float centerX = (r.left + r.right) / 2;
            boolean notMoving = mMovingEdges == 0;
            if ((mMovingEdges & MOVE_RIGHT) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(r.right - mArrow.getWidth() / 2),
                        Math.round(centerY - mArrow.getHeight() / 2));
            }
            if ((mMovingEdges & MOVE_LEFT) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(r.left - mArrow.getWidth() / 2),
                        Math.round(centerY - mArrow.getHeight() / 2));
            }
            if ((mMovingEdges & MOVE_TOP) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(centerX - mArrow.getWidth() / 2),
                        Math.round(r.top - mArrow.getHeight() / 2));
            }
            if ((mMovingEdges & MOVE_BOTTOM) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(centerX - mArrow.getWidth() / 2),
                        Math.round(r.bottom - mArrow.getHeight() / 2));
            }
        }

        private void drawHighlightRectangle(GLCanvas canvas, RectF r) {
            GL11 gl = canvas.getGLInstance();
            gl.glLineWidth(3.0f);
            gl.glEnable(GL11.GL_LINE_SMOOTH);

            gl.glEnable(GL11.GL_STENCIL_TEST);
            gl.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            gl.glStencilFunc(GL11.GL_ALWAYS, 1, 1);

            if (mSpotlightRatioX == 0 || mSpotlightRatioY == 0) {
                canvas.fillRect(r.left, r.top, r.width(), r.height(), Color.TRANSPARENT);
                canvas.drawRect(r.left, r.top, r.width(), r.height(), mPaint);
            } else {
                float sx = r.width() * mSpotlightRatioX;
                float sy = r.height() * mSpotlightRatioY;
                float cx = r.centerX();
                float cy = r.centerY();

                canvas.fillRect(cx - sx / 2, cy - sy / 2, sx, sy, Color.TRANSPARENT);
                canvas.drawRect(cx - sx / 2, cy - sy / 2, sx, sy, mPaint);
                canvas.drawRect(r.left, r.top, r.width(), r.height(), mPaint);

                gl.glStencilFunc(GL11.GL_NOTEQUAL, 1, 1);
                gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

                canvas.drawRect(cx - sy / 2, cy - sx / 2, sy, sx, mPaint);
                canvas.fillRect(cx - sy / 2, cy - sx / 2, sy, sx, Color.TRANSPARENT);
                canvas.fillRect(r.left, r.top, r.width(), r.height(), 0x80000000);
            }

            gl.glStencilFunc(GL11.GL_NOTEQUAL, 1, 1);
            gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            canvas.fillRect(0, 0, getWidth(), getHeight(), 0xA0000000);

            gl.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    private class DetectFaceTask extends Thread {
        private final FaceDetector.Face[] mFaces = new FaceDetector.Face[MAX_FACE_COUNT];
        private final Bitmap mFaceBitmap;
        private int mFaceCount;

        public DetectFaceTask(Bitmap bitmap) {
            mFaceBitmap = bitmap;
            setName("face-detect");
        }

        @Override
        public void run() {
            Bitmap bitmap = mFaceBitmap;
            FaceDetector detector = new FaceDetector(
                    bitmap.getWidth(), bitmap.getHeight(), MAX_FACE_COUNT);
            mFaceCount = detector.findFaces(bitmap, mFaces);
            mMainHandler.sendMessage(
                    mMainHandler.obtainMessage(MSG_UPDATE_FACES, this));
        }

        private RectF getFaceRect(FaceDetector.Face face) {
            PointF point = new PointF();
            face.getMidPoint(point);

            int width = mFaceBitmap.getWidth();
            int height = mFaceBitmap.getHeight();
            float rx = face.eyesDistance() * FACE_EYE_RATIO;
            float ry = rx;
            float aspect = mAspectRatio;
            if (aspect != UNSPECIFIED) {
                if (aspect > 1) {
                    rx = ry * aspect;
                } else {
                    ry = rx / aspect;
                }
            }

            RectF r = new RectF(
                    point.x - rx, point.y - ry, point.x + rx, point.y + ry);
            r.intersect(0, 0, width, height);

            if (aspect != UNSPECIFIED) {
                if (r.width() / r.height() > aspect) {
                    float w = r.height() * aspect;
                    r.left = (r.left + r.right - w) * 0.5f;
                    r.right = r.left + w;
                } else {
                    float h = r.width() / aspect;
                    r.top =  (r.top + r.bottom - h) * 0.5f;
                    r.bottom = r.top + h;
                }
            }

            r.left /= width;
            r.right /= width;
            r.top /= height;
            r.bottom /= height;
            return r;
        }

        public void updateFaces() {
            if (mFaceCount > 1) {
                for (int i = 0, n = mFaceCount; i < n; ++i) {
                    mFaceDetectionView.addFace(getFaceRect(mFaces[i]));
                }
                mFaceDetectionView.setVisibility(GLView.VISIBLE);
                Toast.makeText(mActivity.getAndroidContext(),
                        R.string.multiface_crop_help, Toast.LENGTH_SHORT).show();
            } else if (mFaceCount == 1) {
                mFaceDetectionView.setVisibility(GLView.INVISIBLE);
                mHighlightRectangle.setRectangle(getFaceRect(mFaces[0]));
                mHighlightRectangle.setVisibility(GLView.VISIBLE);
            } else /*mFaceCount == 0*/ {
                mHighlightRectangle.setInitRectangle();
                mHighlightRectangle.setVisibility(GLView.VISIBLE);
            }
        }
    }

    public void setDataModel(TileImageView.Model dataModel, int rotation) {
        if (((rotation / 90) & 0x01) != 0) {
            mImageWidth = dataModel.getImageHeight();
            mImageHeight = dataModel.getImageWidth();
        } else {
            mImageWidth = dataModel.getImageWidth();
            mImageHeight = dataModel.getImageHeight();
        }

        mImageRotation = rotation;

        mImageView.setModel(dataModel);
        mAnimation.initialize();
    }

    public void detectFaces(Bitmap bitmap) {
        int rotation = mImageRotation;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = (float) Math.sqrt(
                (double) FACE_PIXEL_COUNT / (width * height));

        // faceBitmap is a correctly rotated bitmap, as viewed by a user.
        Bitmap faceBitmap;
        if (((rotation / 90) & 1) == 0) {
            int w = (Math.round(width * scale) & ~1); // must be even
            int h = Math.round(height * scale);
            faceBitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
            Canvas canvas = new Canvas(faceBitmap);
            canvas.rotate(rotation, w / 2, h / 2);
            canvas.scale((float) w / width, (float) h / height);
            canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            int w = (Math.round(height * scale) & ~1); // must be even
            int h = Math.round(width * scale);
            faceBitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
            Canvas canvas = new Canvas(faceBitmap);
            canvas.translate(w / 2, h / 2);
            canvas.rotate(rotation);
            canvas.translate(-h / 2, -w / 2);
            canvas.scale((float) w / height, (float) h / width);
            canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        new DetectFaceTask(faceBitmap).start();
    }

    public void initializeHighlightRectangle() {
        mHighlightRectangle.setInitRectangle();
        mHighlightRectangle.setVisibility(GLView.VISIBLE);
    }

    public void resume() {
        mImageView.prepareTextures();
    }

    public void pause() {
        mImageView.freeTextures();
    }
}

