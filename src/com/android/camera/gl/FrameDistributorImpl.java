/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.gl;

import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of {@link FrameDistributor}.
 * <p>
 * The typical way to use this is as follows:
 * <ol>
 * <li>Create a new instance and add all the {@link FrameConsumer} instances.
 * </li>
 * <li>Call {@link #start()} on the distributor.</li>
 * <li>Obtain the {@link SurfaceTexture} used by the distributor and hook it up
 * to your producer e.g. a Camera instance.</li>
 * <li>Your consumers now start receiving
 * {@link FrameConsumer#onNewFrameAvailable(FrameDistributor)} callbacks for new
 * frames.</li>
 * <li>Each consumer grabs the most current frame from its GL thread by calling
 * {@link #acquireNextFrame(int, float[])}.</li>
 * <li>After accessing the data, the consumer calls {@link #releaseFrame()}.
 * </li>
 * <li>When all processing is complete, call {@link #stop()}.</li>
 * </ol>
 */
public class FrameDistributorImpl implements FrameDistributor, AutoCloseable {

    private DistributionHandler mDistributionHandler;

    private HandlerThread mDistributionThread;

    private static class DistributionHandler extends Handler implements OnFrameAvailableListener {

        public static final int MSG_SETUP = 1;
        public static final int MSG_RELEASE = 2;
        public static final int MSG_UPDATE_SURFACE = 3;

        private static final int DEFAULT_SURFACE_BUFFER_WIDTH = 1440;
        private static final int DEFAULT_SURFACE_BUFFER_HEIGHT = 1080;

        private final FrameDistributorImpl mDistributor;

        private final AtomicBoolean mNewFrame = new AtomicBoolean(false);

        final ConditionVariable mCommandDoneCondition = new ConditionVariable(true);

        private final Lock mSurfaceTextureAccessLock = new ReentrantLock();

        private final List<FrameConsumer> mConsumers;

        private SurfaceTexture mSurfaceTexture;

        private long mTimestamp;

        private int mTexture;

        private RenderTarget mServerTarget;

        private boolean mIsSetup = false;

        public DistributionHandler(FrameDistributorImpl distributor, Looper looper,
                List<FrameConsumer> consumers) {
            super(looper);
            mDistributor = distributor;
            mConsumers = consumers;
        }

        @Override
        public void handleMessage(Message message) {
            try {
                switch (message.what) {
                    case MSG_SETUP:
                        setup();
                        break;
                    case MSG_UPDATE_SURFACE:
                        updateSurfaceTexture();
                        break;
                    case MSG_RELEASE:
                        release();
                        break;
                    default:
                        throw new IllegalStateException("Unknown message: " + message + "!");
                }
            } finally {
                mCommandDoneCondition.open();
            }
        }

        private synchronized void setup() {
            if (!mIsSetup) {
                mServerTarget = RenderTarget.newTarget(1, 1);
                mServerTarget.focus();
                mSurfaceTexture = createSurfaceTexture();
                informListenersOfStart();
                mIsSetup = true;
            }
        }

        private synchronized void release() {
            if (mIsSetup) {
                // Notify listeners
                informListenersOfStop();

                // Release our resources
                mServerTarget.close();
                mServerTarget = null;
                mSurfaceTexture.release();
                mSurfaceTexture = null;
                GLToolbox.deleteTexture(mTexture);

                // It is VERY important we unfocus the current EGL context, as
                // the SurfaceTextures
                // will not properly detach if this is not done.
                RenderTarget.focusNone();

                // Update internal state
                mNewFrame.set(false);
                mIsSetup = false;
            }
        }

        private void updateSurfaceTexture() {
            if (mIsSetup) {
                mSurfaceTextureAccessLock.lock();
                mSurfaceTexture.attachToGLContext(mTexture);
                mSurfaceTexture.updateTexImage();
                mSurfaceTexture.detachFromGLContext();
                mSurfaceTextureAccessLock.unlock();
                mTimestamp = mSurfaceTexture.getTimestamp();
                informListenersOfNewFrame(mTimestamp);
            }
        }

        public void postMessageType(int kind) {
            mCommandDoneCondition.close();
            sendMessage(Message.obtain(this, kind));
        }

        private void informListenersOfStart() {
            synchronized (mConsumers) {
                for (FrameConsumer consumer : mConsumers) {
                    consumer.onStart();
                }
            }
        }

        private void informListenersOfNewFrame(long timestamp) {
            synchronized (mConsumers) {
                for (FrameConsumer consumer : mConsumers) {
                    consumer.onNewFrameAvailable(mDistributor, timestamp);
                }
            }
        }

        private void informListenersOfStop() {
            synchronized (mConsumers) {
                for (FrameConsumer consumer : mConsumers) {
                    consumer.onStop();
                }
            }
        }

        public long acquireNextFrame(int textureName, float[] transform) {
            if (transform == null || transform.length != 16) {
                throw new
                IllegalArgumentException("acquireNextFrame: invalid transform array.");
            }
            mSurfaceTextureAccessLock.lock();
            mSurfaceTexture.attachToGLContext(textureName);
            mSurfaceTexture.getTransformMatrix(transform);

            return mTimestamp;
        }

        public void releaseFrame() {
            mSurfaceTexture.detachFromGLContext();
            mSurfaceTextureAccessLock.unlock();
        }

        public synchronized SurfaceTexture getSurfaceTexture() {
            return mSurfaceTexture;
        }

        public synchronized RenderTarget getRenderTarget() {
            return mServerTarget;
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            postMessageType(MSG_UPDATE_SURFACE);
        }

        private SurfaceTexture createSurfaceTexture() {
            mTexture = GLToolbox.generateTexture();
            SurfaceTexture surfaceTexture = new SurfaceTexture(mTexture);
            surfaceTexture.setDefaultBufferSize(DEFAULT_SURFACE_BUFFER_WIDTH,
                    DEFAULT_SURFACE_BUFFER_HEIGHT);
            surfaceTexture.setOnFrameAvailableListener(this);
            surfaceTexture.detachFromGLContext();
            return surfaceTexture;
        }

    }

    /**
     * Creates a new distributor with the specified consumers.
     *
     * @param consumers list of consumers that will process incoming frames.
     */
    public FrameDistributorImpl(List<FrameConsumer> consumers) {
        mDistributionThread = new HandlerThread("FrameDistributor");
        mDistributionThread.start();
        mDistributionHandler = new DistributionHandler(this, mDistributionThread.getLooper(),
                consumers);
    }

    /**
     * Start processing frames and sending them to consumers.
     */
    public void start() {
        mDistributionHandler.postMessageType(DistributionHandler.MSG_SETUP);
    }

    /**
     * Stop processing frames and release any resources required for doing so.
     */
    public void stop() {
        mDistributionHandler.postMessageType(DistributionHandler.MSG_RELEASE);
    }

    /**
     * Wait until the current start/stop command has finished executing.
     * <p>
     * Use this command if you need to make sure that the distributor has fully
     * started or stopped.
     */
    public void waitForCommand() {
        mDistributionHandler.mCommandDoneCondition.block();
    }

    /**
     * Close the current distributor and release its resources.
     * <p>
     * You must not use the distributor after calling this method.
     */
    @Override
    public void close() {
        stop();
        mDistributionThread.quitSafely();
    }

    @Override
    public long acquireNextFrame(int textureName, float[] transform) {
        return mDistributionHandler.acquireNextFrame(textureName, transform);
    }

    @Override
    public void releaseFrame() {
        mDistributionHandler.releaseFrame();
    }

    /**
     * Get the {@link SurfaceTexture} whose frames will be distributed.
     * <p>
     * You must call this after distribution has started with a call to
     * {@link #start()}.
     *
     * @return the input SurfaceTexture or null, if none is yet available.
     */
    public SurfaceTexture getInputSurfaceTexture() {
        return mDistributionHandler.getSurfaceTexture();
    }

    /**
     * Get the {@link RenderTarget} that the distributor uses for GL operations.
     * <p>
     * You should rarely need to use this method. It is used exclusively by
     * consumers that reuse the FrameDistributor's EGL context, and must be
     * handled with great care.
     *
     * @return the RenderTarget used by the FrameDistributor.
     */
    @Override
    public RenderTarget getRenderTarget() {
        return mDistributionHandler.getRenderTarget();
    }

}
