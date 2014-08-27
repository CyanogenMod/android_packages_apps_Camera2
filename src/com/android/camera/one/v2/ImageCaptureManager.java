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

package com.android.camera.one.v2;

import android.annotation.TargetApi;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Pair;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.util.ConcurrentSharedRingBuffer;
import com.android.camera.util.ConcurrentSharedRingBuffer.PinStateListener;
import com.android.camera.util.ConcurrentSharedRingBuffer.Selector;
import com.android.camera.util.ConcurrentSharedRingBuffer.SwapTask;
import com.android.camera.util.Task;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements {@link ImageReader.OnImageAvailableListener} and
 * {@link CameraCapturesession.CaptureListener} to store the results of capture
 * requests (both {@link Image}s and {@link TotalCaptureResult}s in a
 * ring-buffer from which they may be saved.<br>
 * This also manages the lifecycle of {@link Image}s within the application as
 * they are passed in from the lower-level camera2 API.
 */
@TargetApi(Build.VERSION_CODES.L)
public class ImageCaptureManager extends CameraCaptureSession.CaptureListener implements
        ImageReader.OnImageAvailableListener {
    /**
     * Callback to listen for changes to the ability to capture an existing
     * image from the internal ring-buffer.
     */
    public interface CaptureReadyListener {
        /**
         * Called whenever the ability to capture an existing image from the
         * ring-buffer changes. Calls to {@link #tryCaptureExistingImage} are
         * more likely to succeed or fail depending on the value passed in to
         * this function.
         *
         * @param capturePossible true if capture is more-likely to be possible,
         *            false if capture is less-likely to be possible.
         */
        public void onReadyStateChange(boolean capturePossible);
    }

    /**
     * Callback for saving an image.
     */
    public interface ImageCaptureListener {
        /**
         * Called with the {@link Image} and associated
         * {@link TotalCaptureResult}. A typical implementation would save this
         * to disk.
         * <p>
         * Note: Implementations must not close the image.
         * </p>
         */
        public void onImageCaptured(Image image, TotalCaptureResult captureResult);
    }

    /**
     * Callback for placing constraints on which images to capture. See
     * {@link #tryCaptureExistingImage} and {@link captureNextImage}.
     */
    public static interface CapturedImageConstraint {
        /**
         * Implementations should return true if the provided
         * TotalCaptureResults satisfies constraints necessary for the intended
         * image capture. For example, a constraint may return false if
         * {@captureResult} indicates that the lens was moving during image
         * capture.
         *
         * @param captureResult The metadata associated with the image.
         * @return true if this image satisfies the constraint and can be
         *         captured, false otherwise.
         */
        boolean satisfiesConstraint(TotalCaptureResult captureResult);
    }

    /**
     * Holds an {@link Image} and {@link TotalCaptureResult} pair which may be
     * added asynchronously.
     */
    private class CapturedImage {
        /**
         * The Image and TotalCaptureResult may be received at different times
         * (via the onImageAvailableListener and onCaptureProgressed callbacks,
         * respectively).
         */
        private Image mImage = null;
        private TotalCaptureResult mMetadata = null;

        /**
         * Resets the object, closing and removing any existing image and
         * metadata.
         */
        public void reset() {
            if (mImage != null) {
                mImage.close();
                int numOpenImages = mNumOpenImages.decrementAndGet();
                if (DEBUG_PRINT_OPEN_IMAGE_COUNT) {
                    Log.v(TAG, "Closed an image. Number of open images = " + numOpenImages);
                }
            }

            mImage = null;

            mMetadata = null;
        }

        /**
         * @return true if both the image and metadata are present, false
         *         otherwise.
         */
        public boolean isComplete() {
            return mImage != null && mMetadata != null;
        }

        /**
         * Adds the image. Note that this can only be called once before a
         * {@link #reset()} is necessary.
         *
         * @param image the {@Link Image} to add.
         */
        public void addImage(Image image) {
            if (mImage != null) {
                throw new IllegalArgumentException(
                        "Unable to add an Image when one already exists.");
            }
            mImage = image;
        }

        /**
         * Retrieves the {@link Image} if it has been added, returns null if it
         * is not available yet.
         */
        public Image tryGetImage() {
            return mImage;
        }

        /**
         * Adds the metadata. Note that this can only be called once before a
         * {@link #reset()} is necessary.
         *
         * @param metadata the {@Link TotalCaptureResult} to add.
         */
        public void addMetadata(TotalCaptureResult metadata) {
            if (mMetadata != null) {
                throw new IllegalArgumentException(
                        "Unable to add a TotalCaptureResult when one already exists.");
            }
            mMetadata = metadata;
        }

        /**
         * Retrieves the {@link TotalCaptureResult} if it has been added,
         * returns null if it is not available yet.
         */
        public TotalCaptureResult tryGetMetadata() {
            return mMetadata;
        }
    }

    private static final Tag TAG = new Tag("ZSLImageListener");

    /**
     * If true, the number of open images will be printed to LogCat every time
     * an image is opened or closed.
     */
    private static final boolean DEBUG_PRINT_OPEN_IMAGE_COUNT = false;

    /**
     * The maximum duration for an onImageAvailable() callback before debugging
     * output is printed. This is a little under 1/30th of a second to enable
     * detecting jank in the preview stream caused by {@link #onImageAvailable}
     * taking too long to return.
     */
    private static final long DEBUG_MAX_IMAGE_CALLBACK_DUR = 25;

    /**
     * Stores the ring-buffer of captured images.<br>
     * Note that this takes care of thread-safe reference counting of images to
     * ensure that they are never leaked by the app.
     */
    private final ConcurrentSharedRingBuffer<CapturedImage> mCapturedImageBuffer;

    /** Track the number of open images for debugging purposes. */
    private final AtomicInteger mNumOpenImages = new AtomicInteger(0);

    /** The executor on which to invoke {@link ImageCaptureListener} listeners. */
    private final Executor mCaptureExecutor;

    private ImageCaptureManager.ImageCaptureListener mPendingImageCaptureCallback;
    private List<ImageCaptureManager.CapturedImageConstraint> mPendingImageCaptureConstraints;

    /**
     * @param maxImages the maximum number of images provided by the
     *            {@link ImageReader}. This must be greater than 2.
     */
    ImageCaptureManager(int maxImages, Executor captureExecutor) {
        // Ensure that there are always 2 images available for the framework to
        // continue processing frames.
        // TODO Could we make this tighter?
        mCapturedImageBuffer = new ConcurrentSharedRingBuffer<ImageCaptureManager.CapturedImage>(
                maxImages - 2);

        mCaptureExecutor = captureExecutor;
    }

    public void setListener(Handler handler, final CaptureReadyListener listener) {
        mCapturedImageBuffer.setListener(handler,
                new PinStateListener() {
                @Override
                    public void onPinStateChange(boolean pinsAvailable) {
                        listener.onReadyStateChange(pinsAvailable);
                    }
                });
    }

    @Override
    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
            final TotalCaptureResult result) {
        final long timestamp = result.get(TotalCaptureResult.SENSOR_TIMESTAMP);

        // Find the CapturedImage in the ring-buffer and attach the
        // TotalCaptureResult to it.
        // See documentation for swapLeast() for details.
        boolean swapSuccess = mCapturedImageBuffer.swapLeast(timestamp,
                new SwapTask<CapturedImage>() {
                @Override
                    public CapturedImage create() {
                        CapturedImage image = new CapturedImage();
                        image.addMetadata(result);
                        return image;
                    }

                @Override
                    public CapturedImage swap(CapturedImage oldElement) {
                        oldElement.reset();
                        oldElement.addMetadata(result);
                        return oldElement;
                    }

                @Override
                    public void update(CapturedImage existingElement) {
                        existingElement.addMetadata(result);
                    }
                });

        if (!swapSuccess) {
            // Do nothing on failure to swap in.
            Log.v(TAG, "Unable to add new image metadata to ring-buffer.");
        }

        tryExecutePendingCaptureRequest(timestamp);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        long startTime = SystemClock.currentThreadTimeMillis();

        final Image img = reader.acquireLatestImage();

        if (img != null) {
            int numOpenImages = mNumOpenImages.incrementAndGet();
            if (DEBUG_PRINT_OPEN_IMAGE_COUNT) {
                Log.v(TAG, "Acquired an image. Number of open images = " + numOpenImages);
            }

            // Try to place the newly-acquired image into the ring buffer.
            boolean swapSuccess = mCapturedImageBuffer.swapLeast(
                    img.getTimestamp(), new SwapTask<CapturedImage>() {
                            @Override
                        public CapturedImage create() {
                            CapturedImage image = new CapturedImage();
                            image.addImage(img);
                            return image;
                        }

                            @Override
                        public CapturedImage swap(CapturedImage oldElement) {
                            oldElement.reset();
                            oldElement.addImage(img);
                            return oldElement;
                        }

                            @Override
                        public void update(CapturedImage existingElement) {
                            existingElement.addImage(img);
                        }
                    });

            if (!swapSuccess) {
                // If we were unable to save the image to the ring buffer, we
                // must close it now.
                // We should only get here if the ring buffer is closed.
                img.close();
                numOpenImages = mNumOpenImages.decrementAndGet();
                if (DEBUG_PRINT_OPEN_IMAGE_COUNT) {
                    Log.v(TAG, "Closed an image. Number of open images = " + numOpenImages);
                }
            }

            tryExecutePendingCaptureRequest(img.getTimestamp());

            long endTime = SystemClock.currentThreadTimeMillis();
            long totTime = endTime - startTime;
            if (totTime > DEBUG_MAX_IMAGE_CALLBACK_DUR) {
                // If it takes too long to swap elements, we will start skipping
                // preview frames,
                // resulting in visible jank
                Log.v(TAG, "onImageAvailable() took " + totTime + "ms");
            }
        }
    }

    /**
     * Closes the listener, eventually freeing all currently-held {@link Image}
     * s.
     */
    public void close() {
        try {
            mCapturedImageBuffer.close(new Task<CapturedImage>() {
                    @Override
                public void run(CapturedImage e) {
                    e.reset();
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the pending image capture request, overriding any previous calls to
     * {@link #captureNextImage} which have not yet been resolved. When the next
     * available image which satisfies the given constraints can be captured,
     * onImageCaptured will be invoked.
     *
     * @param onImageCaptured the callback which will be invoked with the
     *            captured image.
     * @param constraints the set of constraints which must be satisfied in
     *            order for the image to be captured.
     */
    public void captureNextImage(final ImageCaptureListener onImageCaptured,
            final List<CapturedImageConstraint> constraints) {
        mPendingImageCaptureCallback = onImageCaptured;
        mPendingImageCaptureConstraints = constraints;
    }

    /**
     * Tries to resolve any pending image capture requests.
     *
     * @param newImageTimestamp the timestamp of a newly-acquired image which
     *            should be captured if appropriate and possible.
     */
    private void tryExecutePendingCaptureRequest(long newImageTimestamp) {
        if (mPendingImageCaptureCallback != null) {
            final Pair<Long, CapturedImage> pinnedImage = mCapturedImageBuffer.tryPin(
                    newImageTimestamp);
            if (pinnedImage != null) {
                CapturedImage image = pinnedImage.second;

                if (!image.isComplete()) {
                    mCapturedImageBuffer.release(pinnedImage.first);
                    return;
                }

                // Check to see if the image satisfies all constraints.
                TotalCaptureResult captureResult = image.tryGetMetadata();

                if (mPendingImageCaptureConstraints != null) {
                    for (CapturedImageConstraint constraint : mPendingImageCaptureConstraints) {
                        if (!constraint.satisfiesConstraint(captureResult)) {
                            mCapturedImageBuffer.release(pinnedImage.first);
                            return;
                        }
                    }
                }

                // If we get here, the image satisfies all the necessary
                // constraints.

                if (tryExecuteCaptureOrRelease(pinnedImage, mPendingImageCaptureCallback)) {
                    // If we successfully handed the image off to the callback,
                    // remove the pending
                    // capture request.
                    mPendingImageCaptureCallback = null;
                    mPendingImageCaptureConstraints = null;
                }
            }
        }
    }

    /**
     * Tries to capture an existing image from the ring-buffer, if one exists
     * that satisfies the given constraint and can be pinned.
     *
     * @return true if the image could be captured, false otherwise.
     */
    public boolean tryCaptureExistingImage(final ImageCaptureListener onImageCaptured,
            final List<CapturedImageConstraint> constraints) {
        // The selector to use in choosing the image to capture.
        Selector<ImageCaptureManager.CapturedImage> selector;

        if (constraints == null || constraints.isEmpty()) {
            // If there are no constraints, use a trivial Selector.
            selector = new Selector<ImageCaptureManager.CapturedImage>() {
                    @Override
                public boolean select(CapturedImage image) {
                    return true;
                }
            };
        } else {
            // If there are constraints, create a Selector which will return
            // true if all constraints
            // are satisfied.
            selector = new Selector<ImageCaptureManager.CapturedImage>() {
                    @Override
                public boolean select(CapturedImage e) {
                    // If this image already has metadata associated with it,
                    // then use it.
                    // Otherwise, we can't block until it's available, so assume
                    // it doesn't
                    // satisfy the required constraints.
                    TotalCaptureResult captureResult = e.tryGetMetadata();

                    if (captureResult == null || e.tryGetImage() == null) {
                        return false;
                    }

                    for (CapturedImageConstraint constraint : constraints) {
                        if (!constraint.satisfiesConstraint(captureResult)) {
                            return false;
                        }
                    }
                    return true;
                }
            };
        }

        // Acquire a lock (pin) on the most recent (greatest-timestamp) image in
        // the ring buffer
        // which satisfies our constraints.
        // Note that this must be released as soon as we are done with it.
        final Pair<Long, CapturedImage> toCapture = mCapturedImageBuffer.tryPinGreatestSelected(
                selector);

        return tryExecuteCaptureOrRelease(toCapture, onImageCaptured);
    }

    /**
     * Tries to execute the image capture callback with the pinned CapturedImage
     * provided.
     *
     * @param toCapture The pinned CapturedImage to pass to the callback, or
     *            release on failure.
     * @param callback The callback to execute.
     * @return true upon success, false upon failure and the release of the
     *         pinned image.
     */
    private boolean tryExecuteCaptureOrRelease(final Pair<Long, CapturedImage> toCapture,
            final ImageCaptureListener callback) {
        if (toCapture == null) {
            return false;
        } else {
            try {
                mCaptureExecutor.execute(new Runnable() {
                        @Override
                    public void run() {
                        try {
                            CapturedImage img = toCapture.second;
                            callback.onImageCaptured(img.tryGetImage(),
                                    img.tryGetMetadata());
                        } finally {
                            mCapturedImageBuffer.release(toCapture.first);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                // We may get here if the thread pool has been closed.
                mCapturedImageBuffer.release(toCapture.first);
                return false;
            }

            return true;
        }
    }
}
