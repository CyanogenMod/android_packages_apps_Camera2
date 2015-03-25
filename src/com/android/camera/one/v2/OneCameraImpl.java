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
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;

import com.android.camera.CaptureModuleUtil;
import com.android.camera.Exif;
import com.android.camera.Storage;
import com.android.camera.debug.DebugPropertyHelper;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.ExifTag;
import com.android.camera.exif.Rational;
import com.android.camera.one.AbstractOneCamera;
import com.android.camera.one.CameraDirectionProvider;
import com.android.camera.one.OneCamera;
import com.android.camera.one.Settings3A;
import com.android.camera.one.v2.camera2proxy.AndroidCaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.AndroidImageProxy;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.android.camera.processing.imagebackend.TaskImageContainer;
import com.android.camera.session.CaptureSession;
import com.android.camera.ui.focus.LensRangeCalculator;
import com.android.camera.ui.motion.LinearScale;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.CaptureDataSerializer;
import com.android.camera.util.ExifUtil;
import com.android.camera.util.JpegUtilNative;
import com.android.camera.util.Size;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link OneCamera} implementation directly on top of the Camera2 API for
 * cameras without API 2 FULL support (limited or legacy).
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class OneCameraImpl extends AbstractOneCamera {
    /** Captures that are requested but haven't completed yet. */
    private static class InFlightCapture {
        final PhotoCaptureParameters parameters;
        final CaptureSession session;
        Image image;
        TotalCaptureResult totalCaptureResult;

        public InFlightCapture(PhotoCaptureParameters parameters,
                CaptureSession session) {
            this.parameters = parameters;
            this.session = session;
        }

        /** Set the image once it's been received. */
        public InFlightCapture setImage(Image capturedImage) {
            image = capturedImage;
            return this;
        }

        /** Set the total capture result once it's been received. */
        public InFlightCapture setCaptureResult(TotalCaptureResult result) {
            totalCaptureResult = result;
            return this;
        }

        /**
         * Returns whether the capture is complete (which is the case once the
         * image and capture result are both present.
         */
        boolean isCaptureComplete() {
            return image != null && totalCaptureResult != null;
        }
    }

    private static final Tag TAG = new Tag("OneCameraImpl2");

    /** If true, will write data about each capture request to disk. */
    private static final boolean DEBUG_WRITE_CAPTURE_DATA = DebugPropertyHelper.writeCaptureData();
    /** If true, will log per-frame AF info. */
    private static final boolean DEBUG_FOCUS_LOG = DebugPropertyHelper.showFrameDebugLog();

    /** Default JPEG encoding quality. */
    private static final Byte JPEG_QUALITY = 90;

    /**
     * Set to ImageFormat.JPEG, to use the hardware encoder, or
     * ImageFormat.YUV_420_888 to use the software encoder. You can also try
     * RAW_SENSOR experimentally.
     */
    private static final int sCaptureImageFormat = DebugPropertyHelper.isCaptureDngEnabled() ?
            ImageFormat.RAW_SENSOR : ImageFormat.JPEG;

    /** Duration to hold after manual focus tap. */
    private static final int FOCUS_HOLD_MILLIS = Settings3A.getFocusHoldMillis();
    /** Zero weight 3A region, to reset regions per API. */
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = AutoFocusHelper
            .getZeroWeightRegion();

    /**
     * CaptureRequest tags.
     * <ul>
     * <li>{@link #PRESHOT_TRIGGERED_AF}</li>
     * <li>{@link #CAPTURE}</li>
     * </ul>
     */
    public static enum RequestTag {
        /** Request that is part of a pre shot trigger. */
        PRESHOT_TRIGGERED_AF,
        /** Capture request (purely for logging). */
        CAPTURE,
        /** Tap to focus (purely for logging). */
        TAP_TO_FOCUS
    }

    /** Directory to store raw DNG files in. */
    private static final File RAW_DIRECTORY = new File(Storage.DIRECTORY, "DNG");

    /** Current CONTROL_AF_MODE request to Camera2 API. */
    private int mControlAFMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    /** Last OneCamera.AutoFocusState reported. */
    private AutoFocusState mLastResultAFState = AutoFocusState.INACTIVE;
    /** Flag to take a picture when the lens is stopped. */
    private boolean mTakePictureWhenLensIsStopped = false;
    /** Takes a (delayed) picture with appropriate parameters. */
    private Runnable mTakePictureRunnable;
    /** Keep PictureCallback for last requested capture. */
    private PictureCallback mLastPictureCallback = null;
    /** Last time takePicture() was called in uptimeMillis. */
    private long mTakePictureStartMillis;
    /** Runnable that returns to CONTROL_AF_MODE = AF_CONTINUOUS_PICTURE. */
    private final Runnable mReturnToContinuousAFRunnable = new Runnable() {
        @Override
        public void run() {
            mAFRegions = ZERO_WEIGHT_3A_REGION;
            mAERegions = ZERO_WEIGHT_3A_REGION;
            mControlAFMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
            repeatingPreview(null);
        }
    };

    /** Current zoom value. 1.0 is no zoom. */
    private float mZoomValue = 1f;
    /** Current crop region: set from mZoomValue. */
    private Rect mCropRegion;
    /** Current AF and AE regions */
    private MeteringRectangle[] mAFRegions = ZERO_WEIGHT_3A_REGION;
    private MeteringRectangle[] mAERegions = ZERO_WEIGHT_3A_REGION;
    /** Last frame for which CONTROL_AF_STATE was received. */
    private long mLastControlAfStateFrameNumber = 0;

    /**
     * Common listener for preview frame metadata.
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session,
                        CaptureRequest request, long timestamp,
                        long frameNumber) {
                    if (request.getTag() == RequestTag.CAPTURE
                            && mLastPictureCallback != null) {
                        mLastPictureCallback.onQuickExpose();
                    }
                }

                // AF state information is sometimes available 1 frame before
                // onCaptureCompleted(), so we take advantage of that.
                @Override
                public void onCaptureProgressed(CameraCaptureSession session,
                        CaptureRequest request, CaptureResult partialResult) {
                    autofocusStateChangeDispatcher(partialResult);
                    super.onCaptureProgressed(session, request, partialResult);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                        CaptureRequest request, TotalCaptureResult result) {
                    autofocusStateChangeDispatcher(result);
                    // This checks for a HAL implementation error where
                    // TotalCaptureResult
                    // is missing CONTROL_AF_STATE. This should not happen.
                    if (result.get(CaptureResult.CONTROL_AF_STATE) == null) {
                        AutoFocusHelper.checkControlAfState(result);
                    }
                    if (DEBUG_FOCUS_LOG) {
                        AutoFocusHelper.logExtraFocusInfo(result);
                    }

                    Float diopter = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                    if (diopter != null && mFocusDistanceListener != null) {
                        mFocusDistanceListener.onFocusDistance(diopter, mLensRange);
                    }

                    if (request.getTag() == RequestTag.CAPTURE) {
                        // Add the capture result to the latest in-flight
                        // capture. If all the data for that capture is
                        // complete, store the image on disk.
                        InFlightCapture capture = null;
                        synchronized (mCaptureQueue) {
                            if (mCaptureQueue.getFirst().setCaptureResult(result)
                                    .isCaptureComplete()) {
                                capture = mCaptureQueue.removeFirst();
                            }
                        }
                        if (capture != null) {
                            OneCameraImpl.this.onCaptureCompleted(capture);
                        }
                    }
                    super.onCaptureCompleted(session, request, result);
                }
            };
    /** Thread on which the camera operations are running. */
    private final HandlerThread mCameraThread;
    /** Handler of the {@link #mCameraThread}. */
    private final Handler mCameraHandler;
    /** The characteristics of this camera. */
    private final CameraCharacteristics mCharacteristics;
    private final LinearScale mLensRange;
    /** The underlying Camera2 API camera device. */
    private final CameraDevice mDevice;
    private final CameraDirectionProvider mDirectionProvider;

    /**
     * The aspect ratio (width/height) of the full resolution for this camera.
     * Usually the native aspect ratio of this camera.
     */
    private final float mFullSizeAspectRatio;
    /** The Camera2 API capture session currently active. */
    private CameraCaptureSession mCaptureSession;
    /** The surface onto which to render the preview. */
    private Surface mPreviewSurface;
    /**
     * A queue of capture requests that have been requested but are not done
     * yet.
     */
    private final LinkedList<InFlightCapture> mCaptureQueue =
            new LinkedList<InFlightCapture>();
    /** Whether closing of this device has been requested. */
    private volatile boolean mIsClosed = false;

    /** Receives the normal captured images. */
    private final ImageReader mCaptureImageReader;
    ImageReader.OnImageAvailableListener mCaptureImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // Add the image data to the latest in-flight capture.
                    // If all the data for that capture is complete, store the
                    // image data.
                    InFlightCapture capture = null;
                    synchronized (mCaptureQueue) {
                        if (mCaptureQueue.getFirst().setImage(reader.acquireLatestImage())
                                .isCaptureComplete()) {
                            capture = mCaptureQueue.removeFirst();
                        }
                    }
                    if (capture != null) {
                        onCaptureCompleted(capture);
                    }
                }
            };

    /**
     * Instantiates a new camera based on Camera 2 API.
     *
     * @param device The underlying Camera 2 device.
     * @param characteristics The device's characteristics.
     * @param pictureSize the size of the final image to be taken.
     */
    OneCameraImpl(CameraDevice device, CameraCharacteristics characteristics, Size pictureSize) {
        mDevice = device;
        mCharacteristics = characteristics;
        mLensRange = LensRangeCalculator.getDiopterToRatioCalculator(characteristics);
        mDirectionProvider = new CameraDirectionProvider(characteristics);
        mFullSizeAspectRatio = calculateFullSizeAspectRatio(characteristics);

        // Override pictureSize for RAW (our picture size settings don't include
        // RAW, which typically only supports one size (sensor size). This also
        // typically differs from the larges JPEG or YUV size.
        // TODO: If we ever want to support RAW properly, it should be one entry
        // in the picture quality list, which should then lead to the right
        // pictureSize being passes into here.
        if (sCaptureImageFormat == ImageFormat.RAW_SENSOR) {
            pictureSize = getDefaultPictureSize();
        }

        mCameraThread = new HandlerThread("OneCamera2");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCaptureImageReader = ImageReader.newInstance(pictureSize.getWidth(),
                pictureSize.getHeight(),
                sCaptureImageFormat, 2);
        mCaptureImageReader.setOnImageAvailableListener(mCaptureImageListener, mCameraHandler);
        Log.d(TAG, "New Camera2 based OneCameraImpl created.");
    }

    /**
     * Take picture, initiating an auto focus scan if needed.
     */
    @Override
    public void takePicture(final PhotoCaptureParameters params, final CaptureSession session) {
        // Do not do anything when a picture is already requested.
        if (mTakePictureWhenLensIsStopped) {
            return;
        }

        // Not ready until the picture comes back.
        broadcastReadyState(false);

        mTakePictureRunnable = new Runnable() {
            @Override
            public void run() {
                takePictureNow(params, session);
            }
        };
        mLastPictureCallback = params.callback;
        mTakePictureStartMillis = SystemClock.uptimeMillis();

        // This class implements a very simple version of AF, which
        // only delays capture if the lens is scanning.
        if (mLastResultAFState == AutoFocusState.ACTIVE_SCAN) {
            Log.v(TAG, "Waiting until scan is done before taking shot.");
            mTakePictureWhenLensIsStopped = true;
        } else {
            // We could do CONTROL_AF_TRIGGER_START and wait until lens locks,
            // but this would slow down the capture.
            takePictureNow(params, session);
        }
    }

    /**
     * Take picture immediately. Parameters passed through from takePicture().
     */
    public void takePictureNow(PhotoCaptureParameters params, CaptureSession session) {
        long dt = SystemClock.uptimeMillis() - mTakePictureStartMillis;
        Log.v(TAG, "Taking shot with extra AF delay of " + dt + " ms.");
        try {
            // JPEG capture.
            CaptureRequest.Builder builder = mDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.setTag(RequestTag.CAPTURE);
            addBaselineCaptureKeysToRequest(builder);

            // Enable lens-shading correction for even better DNGs.
            if (sCaptureImageFormat == ImageFormat.RAW_SENSOR) {
                builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                        CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            } else if (sCaptureImageFormat == ImageFormat.JPEG) {
                builder.set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY);
                builder.set(CaptureRequest.JPEG_ORIENTATION,
                        CameraUtil.getJpegRotation(params.orientation, mCharacteristics));
            }

            builder.addTarget(mPreviewSurface);
            builder.addTarget(mCaptureImageReader.getSurface());
            CaptureRequest request = builder.build();

            if (DEBUG_WRITE_CAPTURE_DATA) {
                final String debugDataDir = makeDebugDir(params.debugDataFolder,
                        "normal_capture_debug");
                Log.i(TAG, "Writing capture data to: " + debugDataDir);
                CaptureDataSerializer.toFile("Normal Capture", request, new File(debugDataDir,
                        "capture.txt"));
            }

            mCaptureSession.capture(request, mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera for still image capture.");
            broadcastReadyState(true);
            params.callback.onPictureTakingFailed();
            return;
        }
        synchronized (mCaptureQueue) {
            mCaptureQueue.add(new InFlightCapture(params, session));
        }
    }

    @Override
    public void startPreview(Surface previewSurface, CaptureReadyCallback listener) {
        mPreviewSurface = previewSurface;
        setupAsync(mPreviewSurface, listener);
    }

    @Override
    public void close() {
        if (mIsClosed) {
            Log.w(TAG, "Camera is already closed.");
            return;
        }
        try {
            if (mCaptureSession != null) {
                mCaptureSession.abortCaptures();
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not abort captures in progress.");
        }
        mIsClosed = true;
        mCameraThread.quitSafely();
        mDevice.close();
    }

    public Size[] getSupportedPreviewSizes() {
        StreamConfigurationMap config = mCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return Size.convert(config.getOutputSizes(SurfaceTexture.class));
    }

    public float getFullSizeAspectRatio() {
        return mFullSizeAspectRatio;
    }

    @Override
    public Facing getDirection() {
        return mDirectionProvider.getDirection();
    }

    private void saveJpegPicture(byte[] jpegData, final PhotoCaptureParameters captureParams,
            CaptureSession session, CaptureResult result) {
        int heading = captureParams.heading;
        int width = 0;
        int height = 0;
        int rotation = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface();
            exif.readExif(jpegData);

            Integer w = exif.getTagIntValue(ExifInterface.TAG_PIXEL_X_DIMENSION);
            width = (w == null) ? width : w;
            Integer h = exif.getTagIntValue(ExifInterface.TAG_PIXEL_Y_DIMENSION);
            height = (h == null) ? height : h;

            // Get image rotation from EXIF.
            rotation = Exif.getOrientation(exif);

            // Set GPS heading direction based on sensor, if location is on.
            if (heading >= 0) {
                ExifTag directionRefTag = exif.buildTag(
                        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                        ExifInterface.GpsTrackRef.MAGNETIC_DIRECTION);
                ExifTag directionTag = exif.buildTag(
                        ExifInterface.TAG_GPS_IMG_DIRECTION,
                        new Rational(heading, 1));
                exif.setTag(directionRefTag);
                exif.setTag(directionTag);
            }
            new ExifUtil(exif).populateExif(Optional.<TaskImageContainer.TaskImage> absent(),
                    Optional.of((CaptureResultProxy) new AndroidCaptureResultProxy(result)),
                    Optional.<Location> absent());
        } catch (IOException e) {
            Log.w(TAG, "Could not read exif from gcam jpeg", e);
            exif = null;
        }
        ListenableFuture<Optional<Uri>> futureUri = session.saveAndFinish(jpegData, width, height,
                rotation, exif);
        Futures.addCallback(futureUri, new FutureCallback<Optional<Uri>>() {
            @Override
            public void onSuccess(Optional<Uri> uriOptional) {
                captureParams.callback.onPictureSaved(uriOptional.orNull());
            }

            @Override
            public void onFailure(Throwable throwable) {
                captureParams.callback.onPictureSaved(null);
            }
        });
    }

    /**
     * Asynchronously sets up the capture session.
     *
     * @param previewSurface the surface onto which the preview should be
     *            rendered.
     * @param listener called when setup is completed.
     */
    private void setupAsync(final Surface previewSurface, final CaptureReadyCallback listener) {
        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {
                setup(previewSurface, listener);
            }
        });
    }

    /**
     * Configures and attempts to create a capture session.
     *
     * @param previewSurface the surface onto which the preview should be
     *            rendered.
     * @param listener called when the setup is completed.
     */
    private void setup(Surface previewSurface, final CaptureReadyCallback listener) {
        try {
            if (mCaptureSession != null) {
                mCaptureSession.abortCaptures();
                mCaptureSession = null;
            }
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(previewSurface);
            outputSurfaces.add(mCaptureImageReader.getSurface());

            mDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    listener.onSetupFailed();
                }

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCaptureSession = session;
                    mAFRegions = ZERO_WEIGHT_3A_REGION;
                    mAERegions = ZERO_WEIGHT_3A_REGION;
                    mZoomValue = 1f;
                    mCropRegion = cropRegionForZoom(mZoomValue);
                    boolean success = repeatingPreview(null);
                    if (success) {
                        listener.onReadyForCapture();
                    } else {
                        listener.onSetupFailed();
                    }
                }

                @Override
                public void onClosed(CameraCaptureSession session) {
                    super.onClosed(session);
                }
            }, mCameraHandler);
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not set up capture session", ex);
            listener.onSetupFailed();
        }
    }

    /**
     * Adds current regions to CaptureRequest and base AF mode +
     * AF_TRIGGER_IDLE.
     *
     * @param builder Build for the CaptureRequest
     */
    private void addBaselineCaptureKeysToRequest(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, mAFRegions);
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, mAERegions);
        builder.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion);
        builder.set(CaptureRequest.CONTROL_AF_MODE, mControlAFMode);
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        // Enable face detection
        builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL);
        builder.set(CaptureRequest.CONTROL_SCENE_MODE,
                CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);
    }

    /**
     * Request preview capture stream with AF_MODE_CONTINUOUS_PICTURE.
     *
     * @return true if request was build and sent successfully.
     * @param tag
     */
    private boolean repeatingPreview(Object tag) {
        try {
            CaptureRequest.Builder builder = mDevice.
                    createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mPreviewSurface);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            addBaselineCaptureKeysToRequest(builder);
            mCaptureSession.setRepeatingRequest(builder.build(), mCaptureCallback,
                    mCameraHandler);
            Log.v(TAG, String.format("Sent repeating Preview request, zoom = %.2f", mZoomValue));
            return true;
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not access camera setting up preview.", ex);
            return false;
        }
    }

    /**
     * Request preview capture stream with auto focus trigger cycle.
     */
    private void sendAutoFocusTriggerCaptureRequest(Object tag) {
        try {
            // Step 1: Request single frame CONTROL_AF_TRIGGER_START.
            CaptureRequest.Builder builder;
            builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mPreviewSurface);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mControlAFMode = CameraMetadata.CONTROL_AF_MODE_AUTO;
            addBaselineCaptureKeysToRequest(builder);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            builder.setTag(tag);
            mCaptureSession.capture(builder.build(), mCaptureCallback, mCameraHandler);

            // Step 2: Call repeatingPreview to update mControlAFMode.
            repeatingPreview(tag);
            resumeContinuousAFAfterDelay(FOCUS_HOLD_MILLIS);
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not execute preview request.", ex);
        }
    }

    /**
     * Resume AF_MODE_CONTINUOUS_PICTURE after FOCUS_HOLD_MILLIS.
     */
    private void resumeContinuousAFAfterDelay(int millis) {
        mCameraHandler.removeCallbacks(mReturnToContinuousAFRunnable);
        mCameraHandler.postDelayed(mReturnToContinuousAFRunnable, millis);
    }

    /**
     * This method takes appropriate action if camera2 AF state changes.
     * <ol>
     * <li>Reports changes in camera2 AF state to OneCamera.FocusStateListener.</li>
     * <li>Take picture after AF scan if mTakePictureWhenLensIsStopped true.</li>
     * </ol>
     */
    private void autofocusStateChangeDispatcher(CaptureResult result) {
        if (result.getFrameNumber() < mLastControlAfStateFrameNumber ||
                result.get(CaptureResult.CONTROL_AF_STATE) == null) {
            return;
        }
        mLastControlAfStateFrameNumber = result.getFrameNumber();

        // Convert to OneCamera mode and state.
        AutoFocusState resultAFState = AutoFocusHelper.
                stateFromCamera2State(result.get(CaptureResult.CONTROL_AF_STATE));

        // TODO: Consider using LENS_STATE.
        boolean lensIsStopped = resultAFState == AutoFocusState.ACTIVE_FOCUSED ||
                resultAFState == AutoFocusState.ACTIVE_UNFOCUSED ||
                resultAFState == AutoFocusState.PASSIVE_FOCUSED ||
                resultAFState == AutoFocusState.PASSIVE_UNFOCUSED;

        if (mTakePictureWhenLensIsStopped && lensIsStopped) {
            // Take the shot.
            mCameraHandler.post(mTakePictureRunnable);
            mTakePictureWhenLensIsStopped = false;
        }

        // Report state change when AF state has changed.
        if (resultAFState != mLastResultAFState && mFocusStateListener != null) {
            mFocusStateListener.onFocusStatusUpdate(resultAFState, result.getFrameNumber());
        }
        mLastResultAFState = resultAFState;
    }

    @Override
    public void triggerFocusAndMeterAtPoint(float nx, float ny) {
        int sensorOrientation = mCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);
        mAERegions = AutoFocusHelper.aeRegionsForNormalizedCoord(nx, ny, mCropRegion,
                sensorOrientation);
        mAFRegions = AutoFocusHelper.afRegionsForNormalizedCoord(nx, ny, mCropRegion,
                sensorOrientation);

        sendAutoFocusTriggerCaptureRequest(RequestTag.TAP_TO_FOCUS);
    }

    @Override
    public float getMaxZoom() {
        return mCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    @Override
    public void setZoom(float zoom) {
        mZoomValue = zoom;
        mCropRegion = cropRegionForZoom(zoom);
        repeatingPreview(null);
    }

    @Override
    public Size pickPreviewSize(Size pictureSize, Context context) {
        if (pictureSize == null) {
            // TODO The default should be selected by the caller, and
            // pictureSize should never be null.
            pictureSize = getDefaultPictureSize();
        }
        float pictureAspectRatio = pictureSize.getWidth() / (float) pictureSize.getHeight();
        Size[] supportedSizes = getSupportedPreviewSizes();

        // Since devices only have one raw resolution we need to be more
        // flexible for selecting a matching preview resolution.
        Double aspectRatioTolerance = sCaptureImageFormat == ImageFormat.RAW_SENSOR ? 10d : null;
        Size size = CaptureModuleUtil.getOptimalPreviewSize(supportedSizes,
                pictureAspectRatio, aspectRatioTolerance);
        Log.d(TAG, "Selected preview size: " + size);
        return size;
    }

    private Rect cropRegionForZoom(float zoom) {
        return AutoFocusHelper.cropRegionForZoom(mCharacteristics, zoom);
    }

    /**
     * Calculate the aspect ratio of the full size capture on this device.
     *
     * @param characteristics the characteristics of the camera device.
     * @return The aspect ration, in terms of width/height of the full capture
     *         size.
     */
    private static float calculateFullSizeAspectRatio(CameraCharacteristics characteristics) {
        Rect activeArraySize =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        return ((float) (activeArraySize.width())) / activeArraySize.height();
    }

    /*
     * Called when a capture that is in flight is completed.
     * @param capture the in-flight capture which needs to contain the received
     * image and capture data
     */
    private void onCaptureCompleted(InFlightCapture capture) {

        // Experimental support for writing RAW. We do not have a usable JPEG
        // here, so we don't use the usual capture session mechanism and instead
        // just store the RAW file in its own directory.
        // TODO: If we make this a real feature we should probably put the DNGs
        // into the Camera directly.
        if (sCaptureImageFormat == ImageFormat.RAW_SENSOR) {
            if (!RAW_DIRECTORY.exists()) {
                if (!RAW_DIRECTORY.mkdirs()) {
                    throw new RuntimeException("Could not create RAW directory.");
                }
            }
            File dngFile = new File(RAW_DIRECTORY, capture.session.getTitle() + ".dng");
            writeDngBytesAndClose(capture.image, capture.totalCaptureResult,
                    mCharacteristics, dngFile);
        } else {
            // Since this is not an HDR+ session, we will just save the
            // result.
            byte[] imageBytes = acquireJpegBytesAndClose(capture.image);
            saveJpegPicture(imageBytes, capture.parameters, capture.session,
                    capture.totalCaptureResult);
        }
        broadcastReadyState(true);
        capture.parameters.callback.onPictureTaken(capture.session);
    }

    /**
     * Take the given RAW image and capture result, convert it to a DNG and
     * write it to disk.
     *
     * @param image the image containing the 16-bit RAW data (RAW_SENSOR)
     * @param captureResult the capture result for the image
     * @param characteristics the camera characteristics of the camera that took
     *            the RAW image
     * @param dngFile the destination to where the resulting DNG data is written
     *            to
     */
    private static void writeDngBytesAndClose(Image image, TotalCaptureResult captureResult,
            CameraCharacteristics characteristics, File dngFile) {
        try (DngCreator dngCreator = new DngCreator(characteristics, captureResult);
                FileOutputStream outputStream = new FileOutputStream(dngFile)) {
            // TODO: Add DngCreator#setThumbnail and add the DNG to the normal
            // filmstrip.
            dngCreator.writeImage(outputStream, image);
            outputStream.close();
            image.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not store DNG file", e);
            return;
        }
        Log.i(TAG, "Successfully stored DNG file: " + dngFile.getAbsolutePath());
    }

    /**
     * Given an image reader, this extracts the final image. If the image in the
     * reader is JPEG, we extract and return it as is. If the image is YUV, we
     * convert it to JPEG and return the result.
     *
     * @param image the image we got from the image reader.
     * @return A valid JPEG image.
     */
    private static byte[] acquireJpegBytesAndClose(Image image) {
        ByteBuffer buffer;
        if (image.getFormat() == ImageFormat.JPEG) {
            Image.Plane plane0 = image.getPlanes()[0];
            buffer = plane0.getBuffer();
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {
            buffer = ByteBuffer.allocateDirect(image.getWidth() * image.getHeight() * 3);

            Log.v(TAG, "Compressing JPEG with software encoder.");
            int numBytes = JpegUtilNative.compressJpegFromYUV420Image(
                    new AndroidImageProxy(image), buffer, JPEG_QUALITY);

            if (numBytes < 0) {
                throw new RuntimeException("Error compressing jpeg.");
            }
            buffer.limit(numBytes);
        } else {
            throw new RuntimeException("Unsupported image format.");
        }

        byte[] imageBytes = new byte[buffer.remaining()];
        buffer.get(imageBytes);
        buffer.rewind();
        image.close();
        return imageBytes;
    }

    /**
     * @return The largest supported picture size.
     */
    public Size getDefaultPictureSize() {
        StreamConfigurationMap configs =
                mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        android.util.Size[] supportedSizes = configs.getOutputSizes(sCaptureImageFormat);

        // Find the largest supported size.
        android.util.Size largestSupportedSize = supportedSizes[0];
        long largestSupportedSizePixels =
                largestSupportedSize.getWidth() * largestSupportedSize.getHeight();
        for (int i = 1; i < supportedSizes.length; i++) {
            long numPixels = supportedSizes[i].getWidth() * supportedSizes[i].getHeight();
            if (numPixels > largestSupportedSizePixels) {
                largestSupportedSize = supportedSizes[i];
                largestSupportedSizePixels = numPixels;
            }
        }
        return new Size(largestSupportedSize.getWidth(), largestSupportedSize.getHeight());
    }
}
