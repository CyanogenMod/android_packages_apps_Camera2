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

package com.android.gallery3d.photoeditor;

import android.content.Context;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renders and displays photo in the surface view.
 */
public class PhotoView extends GLSurfaceView {

    private final PhotoRenderer renderer;

    public PhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        renderer = new PhotoRenderer();
        setEGLContextClientVersion(2);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public RectF getPhotoBounds() {
        RectF photoBounds;
        synchronized (renderer.photoBounds) {
            photoBounds = new RectF(renderer.photoBounds);
        }
        return photoBounds;
    }

    /**
     * Queues a runnable and renders a frame after execution. Queued runnables could be later
     * removed by remove() or flush().
     */
    public void queue(Runnable r) {
        renderer.queue.add(r);
        requestRender();
    }

    /**
     * Removes the specified queued runnable.
     */
    public void remove(Runnable runnable) {
        renderer.queue.remove(runnable);
    }

    /**
     * Flushes all queued runnables to cancel their execution.
     */
    public void flush() {
        renderer.queue.clear();
    }

    /**
     * Sets photo for display; this method must be queued for GL thread.
     */
    public void setPhoto(Photo photo, boolean clearTransform) {
        renderer.setPhoto(photo, clearTransform);
    }

    /**
     * Rotates displayed photo; this method must be queued for GL thread.
     */
    public void rotatePhoto(float degrees) {
        renderer.rotatePhoto(degrees);
    }

    /**
     * Flips displayed photo; this method must be queued for GL thread.
     */
    public void flipPhoto(float horizontalDegrees, float verticalDegrees) {
        renderer.flipPhoto(horizontalDegrees, verticalDegrees);
    }

    /**
     * Renderer that renders the GL surface-view and only be called from the GL thread.
     */
    private class PhotoRenderer implements GLSurfaceView.Renderer {

        final Vector<Runnable> queue = new Vector<Runnable>();
        final RectF photoBounds = new RectF();
        RendererUtils.RenderContext renderContext;
        Photo photo;
        int viewWidth;
        int viewHeight;
        float rotatedDegrees;
        float flippedHorizontalDegrees;
        float flippedVerticalDegrees;

        void setPhoto(Photo photo, boolean clearTransform) {
            int width = (photo != null) ? photo.width() : 0;
            int height = (photo != null) ? photo.height() : 0;
            boolean changed;
            synchronized (photoBounds) {
                changed = (photoBounds.width() != width) || (photoBounds.height() != height);
                if (changed) {
                    photoBounds.set(0, 0, width, height);
                }
            }
            this.photo = photo;
            updateSurface(clearTransform, changed);
        }

        void updateSurface(boolean clearTransform, boolean sizeChanged) {
            boolean flipped = (flippedHorizontalDegrees != 0) || (flippedVerticalDegrees != 0);
            boolean transformed = (rotatedDegrees != 0) || flipped;
            if ((clearTransform && transformed) || (sizeChanged && !transformed)) {
                // Fit photo when clearing existing transforms or changing surface/photo sizes.
                if (photo != null) {
                    RendererUtils.setRenderToFit(renderContext, photo.width(), photo.height(),
                            viewWidth, viewHeight);
                    rotatedDegrees = 0;
                    flippedHorizontalDegrees = 0;
                    flippedVerticalDegrees = 0;
                }
            } else {
                // Restore existing transformations for orientation changes or awaking from sleep.
                if (rotatedDegrees != 0) {
                    rotatePhoto(rotatedDegrees);
                } else if (flipped) {
                    flipPhoto(flippedHorizontalDegrees, flippedVerticalDegrees);
                }
            }
        }

        void rotatePhoto(float degrees) {
            if (photo != null) {
                RendererUtils.setRenderToRotate(renderContext, photo.width(), photo.height(),
                        viewWidth, viewHeight, degrees);
                rotatedDegrees = degrees;
            }
        }

        void flipPhoto(float horizontalDegrees, float verticalDegrees) {
            if (photo != null) {
                RendererUtils.setRenderToFlip(renderContext, photo.width(), photo.height(),
                        viewWidth, viewHeight, horizontalDegrees, verticalDegrees);
                flippedHorizontalDegrees = horizontalDegrees;
                flippedVerticalDegrees = verticalDegrees;
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            Runnable r = null;
            synchronized (queue) {
                if (!queue.isEmpty()) {
                    r = queue.remove(0);
                }
            }
            if (r != null) {
                r.run();
            }
            if (!queue.isEmpty()) {
                requestRender();
            }
            RendererUtils.renderBackground();
            if (photo != null) {
                RendererUtils.renderTexture(renderContext, photo.texture(), viewWidth, viewHeight);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            viewWidth = width;
            viewHeight = height;
            updateSurface(false, true);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            renderContext = RendererUtils.createProgram();
        }
    }
}
