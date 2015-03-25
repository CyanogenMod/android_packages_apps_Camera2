/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.one.v2;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CaptureResult.Key;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.CameraProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.v4.util.Pools;
import android.view.Surface;

import com.android.camera.CaptureModuleUtil;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.ExifTag;
import com.android.camera.exif.Rational;
import com.android.camera.one.AbstractOneCamera;
import com.android.camera.one.CameraDirectionProvider;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.PhotoCaptureParameters.Flash;
import com.android.camera.one.Settings3A;
import com.android.camera.one.v2.ImageCaptureManager.ImageCaptureListener;
import com.android.camera.one.v2.ImageCaptureManager.MetadataChangeListener;
import com.android.camera.one.v2.camera2proxy.AndroidCaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.AndroidImageProxy;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.android.camera.processing.imagebackend.TaskImageContainer;
import com.android.camera.session.CaptureSession;
import com.android.camera.ui.focus.LensRangeCalculator;
import com.android.camera.ui.motion.LinearScale;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.ExifUtil;
import com.android.camera.util.JpegUtilNative;
import com.android.camera.util.ListenerCombiner;
import com.android.camera.util.Size;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@link OneCamera} implementation directly on top of the Camera2 API with zero
 * shutter lag.<br>
 * TODO: Determine what the maximum number of full YUV capture frames is.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@Deprecated
public class OneCameraZslImpl extends AbstractOneCamera {
    private static final Tag TAG = new Tag("OneCameraZslImpl2");

    /** Default JPEG encoding quality. */
    private static final int JPEG_QUALITY =
            CameraProfile.getJpegEncodingQualityParameter(CameraProfile.QUALITY_HIGH);
    /**
     * The maximum number of images to store in the full-size ZSL ring buffer.
     * <br>
     * TODO: Determine this number dynamically based on available memory and the
     * size of frames.
     */
    private static final int MAX_CAPTURE_IMAGES = 12;
    /**
     * True if zero-shutter-lag images should be captured. Some devices produce
     * lower-quality images for the high-frequency stream, so we may wish to
     * disable ZSL in that case.
     */
    private static final boolean ZSL_ENABLED = true;

    /**
     * Tags which may be used in CaptureRequests.
     */
    private static enum RequestTag {
        /**
         * Indicates that the request was explicitly sent for a single
         * high-quality still capture. Unlike other requests, such as the
         * repeating (ZSL) stream and AF/AE triggers, requests with this tag
         * should always be saved.
         */
        EXPLICIT_CAPTURE
    }

    /**
     * Set to ImageFormat.JPEG to use the hardware encoder, or
     * ImageFormat.YUV_420_888 to use the software encoder. No other image
     * formats are supported.
     */
    private static final int sCaptureImageFormat = ImageFormat.YUV_420_888;
    /**
     * Token for callbacks posted to {@link #mCameraHandler} to resume
     * continuous AF.
     */
    private static final String FOCUS_RESUME_CALLBACK_TOKEN = "RESUME_CONTINUOUS_AF";

    /** Zero weight 3A region, to reset regions per API. */
    /*package*/ MeteringRectangle[] ZERO_WEIGHT_3A_REGION = AutoFocusHelper.getZeroWeightRegion();

    /**
     * Thread on which high-priority camera operations, such as grabbing preview
     * frames for the viewfinder, are running.
     */
    private final HandlerThread mCameraThread;
    /** Handler of the {@link #mCameraThread}. */
    private final Handler mCameraHandler;

    /** Thread on which low-priority camera listeners are running. */
    private final HandlerThread mCameraListenerThread;
    private final Handler mCameraListenerHandler;

    /** The characteristics of this camera. */
    private final CameraCharacteristics mCharacteristics;
    /** Converts focus distance units into ratio values */
    private final LinearScale mLensRange;
    /** The underlying Camera2 API camera device. */
    private final CameraDevice mDevice;
    private final CameraDirectionProvider mDirection;

    /**
     * The aspect ratio (width/height) of the full resolution for this camera.
     * Usually the native aspect ratio of this camera.
     */
    private final float mFullSizeAspectRatio;
    /** The Camera2 API capture session currently active. */
    private CameraCaptureSession mCaptureSession;
    /** The surface onto which to render the preview. */
    private Surface mPreviewSurface;
    /** Whether closing of this device has been requested. */
    private volatile boolean mIsClosed = false;

    /** Receives the normal captured images. */
    private final ImageReader mCaptureImageReader;

    /**
     * Maintains a buffer of images and their associated {@link CaptureResult}s.
     */
    private ImageCaptureManager mCaptureManager;

    /**
     * The sensor timestamps (which may not be relative to the system time) of
     * the most recently captured images.
     */
    private final Set<Long> mCapturedImageTimestamps = Collections.synchronizedSet(
            new HashSet<Long>());

    /** Thread pool for performing slow jpeg encoding and saving tasks. */
    private final ThreadPoolExecutor mImageSaverThreadPool;

    /** Pool of native byte buffers on which to store jpeg-encoded images. */
    private final Pools.SynchronizedPool<ByteBuffer> mJpegByteBufferPool =
            new Pools.SynchronizedPool<ByteBuffer>(64);

    /** Current zoom value. 1.0 is no zoom. */
    private float mZoomValue = 1f;
    /** Current crop region: set from mZoomValue. */
    private Rect mCropRegion;
    /** Current AE, AF, and AWB regions */
    private MeteringRectangle[] mAFRegions = ZERO_WEIGHT_3A_REGION;
    private MeteringRectangle[] mAERegions = ZERO_WEIGHT_3A_REGION;

    private MediaActionSound mMediaActionSound = new MediaActionSound();

    /**
     * Ready state (typically displayed by the UI shutter-button) depends on two
     * things:<br>
     * <ol>
     * <li>{@link #mCaptureManager} must be ready.</li>
     * <li>We must not be in the process of capturing a single, high-quality,
     * image.</li>
     * </ol>
     * See {@link ListenerCombiner} and {@link #mReadyStateManager} for
     * details of how this is managed.
     */
    private static enum ReadyStateRequirement {
        CAPTURE_MANAGER_READY, CAPTURE_NOT_IN_PROGRESS
    }

    /**
     * Handles the thread-safe logic of dispatching whenever the logical AND of
     * these constraints changes.
     */
    private final ListenerCombiner<ReadyStateRequirement>
            mReadyStateManager = new ListenerCombiner<ReadyStateRequirement>(
                    ReadyStateRequirement.class, new ListenerCombiner.StateChangeListener() {
                            @Override
                        public void onStateChange(boolean state) {
                            broadcastReadyState(state);
                        }
                    });

    /**
     * An {@link ImageCaptureListener} which will compress and save an image to
     * disk.
     */
    private class ImageCaptureTask implements ImageCaptureListener {
        private final PhotoCaptureParameters mParams;
        private final CaptureSession mSession;

        public ImageCaptureTask(PhotoCaptureParameters parameters, CaptureSession session) {
            mParams = parameters;
            mSession = session;
        }

        @Override
        public void onImageCaptured(Image image, TotalCaptureResult captureResult) {
            long timestamp = captureResult.get(CaptureResult.SENSOR_TIMESTAMP);

            // We should only capture the image if it hasn't been captured
            // before. Synchronization is necessary since
            // mCapturedImageTimestamps is read & modified elsewhere.
            synchronized (mCapturedImageTimestamps) {
                if (!mCapturedImageTimestamps.contains(timestamp)) {
                    mCapturedImageTimestamps.add(timestamp);
                } else {
                    // There was a more recent (or identical) image which has
                    // begun being saved, so abort.
                    return;
                }

                // Clear out old timestamps from the set.
                // We must keep old timestamps in the set a little longer (a
                // factor of 2 seems adequate) to ensure they are cleared out of
                // the ring buffer before their timestamp is removed from the
                // set.
                long maxTimestamps = MAX_CAPTURE_IMAGES * 2;
                if (mCapturedImageTimestamps.size() > maxTimestamps) {
                    ArrayList<Long> timestamps = new ArrayList<Long>(mCapturedImageTimestamps);
                    Collections.sort(timestamps);
                    for (int i = 0; i < timestamps.size()
                            && mCapturedImageTimestamps.size() > maxTimestamps; i++) {
                        mCapturedImageTimestamps.remove(timestamps.get(i));
                    }
                }
            }

            mReadyStateManager.setInput(ReadyStateRequirement.CAPTURE_NOT_IN_PROGRESS, true);

            savePicture(image, mParams, mSession, captureResult);
            mParams.callback.onPictureTaken(mSession);
            Log.v(TAG, "Image saved.  Frame number = " + captureResult.getFrameNumber());
        }
    }

    /**
     * Instantiates a new camera based on Camera 2 API.
     *
     * @param device The underlying Camera 2 device.
     * @param characteristics The device's characteristics.
     * @param pictureSize the size of the final image to be taken.
     */
    OneCameraZslImpl(CameraDevice device, CameraCharacteristics characteristics, Size pictureSize) {
        Log.v(TAG, "Creating new OneCameraZslImpl");

        mDevice = device;
        mCharacteristics = characteristics;
        mLensRange = LensRangeCalculator
              .getDiopterToRatioCalculator(characteristics);
        mDirection = new CameraDirectionProvider(mCharacteristics);
        mFullSizeAspectRatio = calculateFullSizeAspectRatio(characteristics);

        mCameraThread = new HandlerThread("OneCamera2");
        // If this thread stalls, it will delay viewfinder frames.
        mCameraThread.setPriority(Thread.MAX_PRIORITY);
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCameraListenerThread = new HandlerThread("OneCamera2-Listener");
        mCameraListenerThread.start();
        mCameraListenerHandler = new Handler(mCameraListenerThread.getLooper());

        // TODO: Encoding on multiple cores results in preview jank due to
        // excessive GC.
        int numEncodingCores = CameraUtil.getNumCpuCores();
        mImageSaverThreadPool = new ThreadPoolExecutor(numEncodingCores, numEncodingCores, 10,
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

        mCaptureManager =
                new ImageCaptureManager(MAX_CAPTURE_IMAGES, mCameraListenerHandler,
                        mImageSaverThreadPool);
        mCaptureManager.setCaptureReadyListener(new ImageCaptureManager.CaptureReadyListener() {
                @Override
            public void onReadyStateChange(boolean capturePossible) {
                mReadyStateManager.setInput(ReadyStateRequirement.CAPTURE_MANAGER_READY,
                        capturePossible);
            }
        });

        // Listen for changes to auto focus state and dispatch to
        // mFocusStateListener.
        mCaptureManager.addMetadataChangeListener(CaptureResult.CONTROL_AF_STATE,
                new ImageCaptureManager.MetadataChangeListener() {
                @Override
                    public void onImageMetadataChange(Key<?> key, Object oldValue, Object newValue,
                            CaptureResult result) {
                        FocusStateListener listener = mFocusStateListener;
                        if (listener != null) {
                            listener.onFocusStatusUpdate(
                                    AutoFocusHelper.stateFromCamera2State(
                                            result.get(CaptureResult.CONTROL_AF_STATE)),
                                result.getFrameNumber());
                        }
                    }
                });

        // Allocate the image reader to store all images received from the
        // camera.
        if (pictureSize == null) {
            // TODO The default should be selected by the caller, and
            // pictureSize should never be null.
            pictureSize = getDefaultPictureSize();
        }
        mCaptureImageReader = ImageReader.newInstance(pictureSize.getWidth(),
                pictureSize.getHeight(),
                sCaptureImageFormat, MAX_CAPTURE_IMAGES);

        mCaptureImageReader.setOnImageAvailableListener(mCaptureManager, mCameraHandler);
        mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
    }

    @Override
    public void setFocusDistanceListener(FocusDistanceListener focusDistanceListener) {
        if(mFocusDistanceListener == null) {
            mCaptureManager.addMetadataChangeListener(CaptureResult.LENS_FOCUS_DISTANCE,
                  new ImageCaptureManager.MetadataChangeListener() {
                      @Override
                      public void onImageMetadataChange(Key<?> key, Object oldValue,
                            Object newValue,
                            CaptureResult result) {
                          Integer state = result.get(CaptureResult.LENS_STATE);

                          // Forward changes if we have a new value and the camera
                          // A) Doesn't support lens state or B) lens state is
                          // reported and it is reported as moving.
                          if (newValue != null &&
                                (state == null || state == CameraMetadata.LENS_STATE_MOVING)) {
                              mFocusDistanceListener.onFocusDistance((float) newValue, mLensRange);
                          }
                      }
                  });
        }
        mFocusDistanceListener = focusDistanceListener;
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
        for (int i = 0; i < supportedSizes.length; i++) {
            long numPixels = supportedSizes[i].getWidth() * supportedSizes[i].getHeight();
            if (numPixels > largestSupportedSizePixels) {
                largestSupportedSize = supportedSizes[i];
                largestSupportedSizePixels = numPixels;
            }
        }

        return new Size(largestSupportedSize.getWidth(), largestSupportedSize.getHeight());
    }

    private void onShutterInvokeUI(final PhotoCaptureParameters params) {
        // Tell CaptureModule shutter has occurred so it can flash the screen.
        params.callback.onQuickExpose();
        // Play shutter click sound.
        mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
    }

    /**
     * Take a picture.
     */
    @Override
    public void takePicture(final PhotoCaptureParameters params, final CaptureSession session) {
        mReadyStateManager.setInput(ReadyStateRequirement.CAPTURE_NOT_IN_PROGRESS, false);

        boolean useZSL = ZSL_ENABLED;

        // We will only capture images from the zsl ring-buffer which satisfy
        // this constraint.
        ArrayList<ImageCaptureManager.CapturedImageConstraint> zslConstraints =
                new ArrayList<ImageCaptureManager.CapturedImageConstraint>();
        zslConstraints.add(new ImageCaptureManager.CapturedImageConstraint() {
                @Override
            public boolean satisfiesConstraint(TotalCaptureResult captureResult) {
                Long timestamp = captureResult.get(CaptureResult.SENSOR_TIMESTAMP);
                Integer lensState = captureResult.get(CaptureResult.LENS_STATE);
                Integer flashState = captureResult.get(CaptureResult.FLASH_STATE);
                Integer flashMode = captureResult.get(CaptureResult.FLASH_MODE);
                Integer aeState = captureResult.get(CaptureResult.CONTROL_AE_STATE);
                Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                Integer awbState = captureResult.get(CaptureResult.CONTROL_AWB_STATE);

                if (lensState == null) {
                    lensState = CaptureResult.LENS_STATE_STATIONARY;
                }
                if (flashState == null) {
                    flashState = CaptureResult.FLASH_STATE_UNAVAILABLE;
                }
                if (flashMode == null) {
                    flashMode = CaptureResult.FLASH_MODE_OFF;
                }
                if (aeState == null) {
                    aeState = CaptureResult.CONTROL_AE_STATE_INACTIVE;
                }
                if (afState == null) {
                    afState = CaptureResult.CONTROL_AF_STATE_INACTIVE;
                }
                if (awbState == null) {
                    awbState = CaptureResult.CONTROL_AWB_STATE_INACTIVE;
                }

                synchronized (mCapturedImageTimestamps) {
                    if (mCapturedImageTimestamps.contains(timestamp)) {
                        // Don't save frames which we've already saved.
                        return false;
                    }
                }

                if (lensState == CaptureResult.LENS_STATE_MOVING) {
                    // If we know the lens was moving, don't use this image.
                    return false;
                }

                if (aeState == CaptureResult.CONTROL_AE_STATE_SEARCHING
                        || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                    return false;
                }

                if (afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN
                        || afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN) {
                    return false;
                }

                if (awbState == CaptureResult.CONTROL_AWB_STATE_SEARCHING) {
                    return false;
                }

                return true;
            }
        });
        // This constraint lets us capture images which have been explicitly
        // requested. See {@link RequestTag.EXPLICIT_CAPTURE}.
        ArrayList<ImageCaptureManager.CapturedImageConstraint> singleCaptureConstraint =
                new ArrayList<ImageCaptureManager.CapturedImageConstraint>();
        singleCaptureConstraint.add(new ImageCaptureManager.CapturedImageConstraint() {
                @Override
            public boolean satisfiesConstraint(TotalCaptureResult captureResult) {
                Object tag = captureResult.getRequest().getTag();
                return tag == RequestTag.EXPLICIT_CAPTURE;
            }
        });

        // If we can use ZSL, try to save a previously-captured frame, if an
        // acceptable one exists in the buffer.
        if (useZSL) {
            boolean capturedPreviousFrame = mCaptureManager.tryCaptureExistingImage(
                    new ImageCaptureTask(params, session), zslConstraints);
            if (capturedPreviousFrame) {
                Log.v(TAG, "Saving previous frame");
                onShutterInvokeUI(params);
            } else {
                Log.v(TAG, "No good image Available.  Capturing next available good image.");
                // If there was no good frame available in the ring buffer
                // already, capture the next good image.
                // TODO Disable the shutter button until this image is captured.

                Flash flashMode = Flash.OFF;

                if (flashMode == Flash.ON || flashMode == Flash.AUTO) {
                    // We must issue a request for a single capture using the
                    // flash, including an AE precapture trigger.

                    // The following sets up a sequence of events which will
                    // occur in reverse order to the associated method
                    // calls:
                    // 1. Send a request to trigger the Auto Exposure Precapture
                    // 2. Wait for the AE_STATE to leave the PRECAPTURE state,
                    // and then send a request for a single image, with the
                    // appropriate flash settings.
                    // 3. Capture the next appropriate image, which should be
                    // the one we requested in (2).

                    mCaptureManager.captureNextImage(new ImageCaptureTask(params, session),
                            singleCaptureConstraint);

                    mCaptureManager.addMetadataChangeListener(CaptureResult.CONTROL_AE_STATE,
                            new MetadataChangeListener() {
                            @Override
                                public void onImageMetadataChange(Key<?> key, Object oldValue,
                                        Object newValue,
                                        CaptureResult result) {
                                    Log.v(TAG, "AE State Changed");
                                    if (oldValue.equals(Integer.valueOf(
                                            CaptureResult.CONTROL_AE_STATE_PRECAPTURE))) {
                                        mCaptureManager.removeMetadataChangeListener(key, this);
                                        sendSingleRequest(params);
                                        // TODO: Delay this until
                                        // onCaptureStarted().
                                        onShutterInvokeUI(params);
                                    }
                                }
                            });

                    sendAutoExposureTriggerRequest(flashMode);
                } else {
                    // We may get here if, for example, the auto focus is in the
                    // middle of a scan.
                    // If the flash is off, we should just wait for the next
                    // image that arrives. This will have minimal delay since we
                    // do not need to send a new capture request.
                    mCaptureManager.captureNextImage(new ImageCaptureTask(params, session),
                            zslConstraints);
                }
            }
        } else {
            // TODO If we can't save a previous frame, create a new capture
            // request to do what we need (e.g. flash) and call
            // captureNextImage().
            throw new UnsupportedOperationException("Non-ZSL capture not yet supported");
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
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not abort captures in progress.");
        }
        mIsClosed = true;
        mCameraThread.quitSafely();
        mDevice.close();
        mCaptureManager.close();
        mCaptureImageReader.close();
    }

    public Size[] getSupportedPreviewSizes() {
        StreamConfigurationMap config =
                mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return Size.convert(config.getOutputSizes(sCaptureImageFormat));
    }

    public float getFullSizeAspectRatio() {
        return mFullSizeAspectRatio;
    }

    @Override
    public Facing getDirection() {
        return mDirection.getDirection();
   }


    private void savePicture(Image image, final PhotoCaptureParameters captureParams,
            CaptureSession session, CaptureResult result) {
        int heading = captureParams.heading;
        int degrees = CameraUtil.getJpegRotation(captureParams.orientation, mCharacteristics);

        ExifInterface exif = new ExifInterface();
        // TODO: Add more exif tags here.

        Size size = getImageSizeForOrientation(image.getWidth(), image.getHeight(),
                degrees);

        exif.setTag(exif.buildTag(ExifInterface.TAG_PIXEL_X_DIMENSION, size.getWidth()));
        exif.setTag(exif.buildTag(ExifInterface.TAG_PIXEL_Y_DIMENSION, size.getHeight()));

        exif.setTag(
                exif.buildTag(ExifInterface.TAG_ORIENTATION, ExifInterface.Orientation.TOP_LEFT));

        // Set GPS heading direction based on sensor, if location is on.
        if (heading >= 0) {
            ExifTag directionRefTag = exif.buildTag(ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                    ExifInterface.GpsTrackRef.MAGNETIC_DIRECTION);
            ExifTag directionTag =
                    exif.buildTag(ExifInterface.TAG_GPS_IMG_DIRECTION, new Rational(heading, 1));
            exif.setTag(directionRefTag);
            exif.setTag(directionTag);
        }
        new ExifUtil(exif).populateExif(Optional.<TaskImageContainer.TaskImage>absent(),
                Optional.of((CaptureResultProxy) new AndroidCaptureResultProxy(result)),
                Optional.<Location>absent());
        ListenableFuture<Optional<Uri>> futureUri = session.saveAndFinish(
                acquireJpegBytes(image, degrees),
                size.getWidth(), size.getHeight(), 0, exif);
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
                    boolean success = sendRepeatingCaptureRequest();
                    if (success) {
                        mReadyStateManager.setInput(ReadyStateRequirement.CAPTURE_NOT_IN_PROGRESS,
                                true);
                        mReadyStateManager.notifyListeners();
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

    private void addRegionsToCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, mAERegions);
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, mAFRegions);
        builder.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion);
    }

    private void addFlashToCaptureRequestBuilder(CaptureRequest.Builder builder, Flash flashMode) {
        switch (flashMode) {
            case ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                break;
            case OFF:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case AUTO:
                builder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
        }
    }

    /**
     * Request a stream of images.
     *
     * @return true if successful, false if there was an error submitting the
     *         capture request.
     */
    private boolean sendRepeatingCaptureRequest() {
        Log.v(TAG, "sendRepeatingCaptureRequest()");
        try {
            CaptureRequest.Builder builder;
            if (ZSL_ENABLED) {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            } else {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            builder.addTarget(mPreviewSurface);

            if (ZSL_ENABLED) {
                builder.addTarget(mCaptureImageReader.getSurface());
            }

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);

            addRegionsToCaptureRequestBuilder(builder);

            mCaptureSession.setRepeatingRequest(builder.build(), mCaptureManager, mCameraHandler);
            return true;
        } catch (CameraAccessException e) {
            if (ZSL_ENABLED) {
                Log.v(TAG, "Could not execute zero-shutter-lag repeating request.", e);
            } else {
                Log.v(TAG, "Could not execute preview request.", e);
            }
            return false;
        }
    }

    /**
     * Request a single image.
     *
     * @return true if successful, false if there was an error submitting the
     *         capture request.
     */
    private boolean sendSingleRequest(OneCamera.PhotoCaptureParameters params) {
        Log.v(TAG, "sendSingleRequest()");
        try {
            CaptureRequest.Builder builder;
            builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            builder.addTarget(mPreviewSurface);

            // Always add this surface for single image capture requests.
            builder.addTarget(mCaptureImageReader.getSurface());

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            Flash flashMode = Flash.OFF;
            addFlashToCaptureRequestBuilder(builder, flashMode);
            addRegionsToCaptureRequestBuilder(builder);

            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

            // Tag this as a special request which should be saved.
            builder.setTag(RequestTag.EXPLICIT_CAPTURE);

            if (sCaptureImageFormat == ImageFormat.JPEG) {
                builder.set(CaptureRequest.JPEG_QUALITY, (byte) (JPEG_QUALITY));
                builder.set(CaptureRequest.JPEG_ORIENTATION,
                        CameraUtil.getJpegRotation(params.orientation, mCharacteristics));
            }

            mCaptureSession.capture(builder.build(), mCaptureManager, mCameraHandler);
            return true;
        } catch (CameraAccessException e) {
            Log.v(TAG, "Could not execute single still capture request.", e);
            return false;
        }
    }

    private boolean sendRepeatingBurstCaptureRequest() {
        Log.v(TAG, "sendRepeatingBurstCaptureRequest()");
        try {
            CaptureRequest.Builder builder;
            builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            builder.addTarget(mPreviewSurface);

            if (ZSL_ENABLED) {
                builder.addTarget(mCaptureImageReader.getSurface());
            }

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);

            addRegionsToCaptureRequestBuilder(builder);

            mCaptureSession.setRepeatingRequest(builder.build(), mCaptureManager, mCameraHandler);
            return true;
        } catch (CameraAccessException e) {
            Log.v(TAG, "Could not send repeating burst capture request.", e);
            return false;
        }
    }

    private boolean sendAutoExposureTriggerRequest(Flash flashMode) {
        Log.v(TAG, "sendAutoExposureTriggerRequest()");
        try {
            CaptureRequest.Builder builder;
            if (ZSL_ENABLED) {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            } else {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            builder.addTarget(mPreviewSurface);

            if (ZSL_ENABLED) {
                builder.addTarget(mCaptureImageReader.getSurface());
            }

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            addRegionsToCaptureRequestBuilder(builder);
            addFlashToCaptureRequestBuilder(builder, flashMode);

            mCaptureSession.capture(builder.build(), mCaptureManager, mCameraHandler);

            return true;
        } catch (CameraAccessException e) {
            Log.v(TAG, "Could not execute auto exposure trigger request.", e);
            return false;
        }
    }

    /**
     */
    private boolean sendAutoFocusTriggerRequest() {
        Log.v(TAG, "sendAutoFocusTriggerRequest()");
        try {
            CaptureRequest.Builder builder;
            if (ZSL_ENABLED) {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            } else {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            builder.addTarget(mPreviewSurface);

            if (ZSL_ENABLED) {
                builder.addTarget(mCaptureImageReader.getSurface());
            }

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            addRegionsToCaptureRequestBuilder(builder);

            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

            mCaptureSession.capture(builder.build(), mCaptureManager, mCameraHandler);

            return true;
        } catch (CameraAccessException e) {
            Log.v(TAG, "Could not execute auto focus trigger request.", e);
            return false;
        }
    }

    /**
     * Like {@link #sendRepeatingCaptureRequest()}, but with the focus held
     * constant.
     *
     * @return true if successful, false if there was an error submitting the
     *         capture request.
     */
    private boolean sendAutoFocusHoldRequest() {
        Log.v(TAG, "sendAutoFocusHoldRequest()");
        try {
            CaptureRequest.Builder builder;
            if (ZSL_ENABLED) {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            } else {
                builder = mDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }

            builder.addTarget(mPreviewSurface);

            if (ZSL_ENABLED) {
                builder.addTarget(mCaptureImageReader.getSurface());
            }

            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

            addRegionsToCaptureRequestBuilder(builder);
            // TODO: This should fire the torch, if appropriate.

            mCaptureSession.setRepeatingRequest(builder.build(), mCaptureManager, mCameraHandler);

            return true;
        } catch (CameraAccessException e) {
            Log.v(TAG, "Could not execute auto focus hold request.", e);
            return false;
        }
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
        return ((float) activeArraySize.width()) / activeArraySize.height();
    }

    /**
     * @param originalWidth the width of the original image captured from the
     *            camera
     * @param originalHeight the height of the original image captured from the
     *            camera
     * @param orientation the rotation to apply, in degrees.
     * @return The size of the final rotated image
     */
    private Size getImageSizeForOrientation(int originalWidth, int originalHeight,
            int orientation) {
        if (orientation == 0 || orientation == 180) {
            return new Size(originalWidth, originalHeight);
        } else if (orientation == 90 || orientation == 270) {
            return new Size(originalHeight, originalWidth);
        } else {
            throw new InvalidParameterException("Orientation not supported.");
        }
    }

    /**
     * Given an image reader, extracts the JPEG image bytes and then closes the
     * reader.
     *
     * @param img the image from which to extract jpeg bytes or compress to
     *            jpeg.
     * @param degrees the angle to rotate the image clockwise, in degrees. Rotation is
     *            only applied to YUV images.
     * @return The bytes of the JPEG image. Newly allocated.
     */
    private byte[] acquireJpegBytes(Image img, int degrees) {
        ByteBuffer buffer;

        if (img.getFormat() == ImageFormat.JPEG) {
            Image.Plane plane0 = img.getPlanes()[0];
            buffer = plane0.getBuffer();

            byte[] imageBytes = new byte[buffer.remaining()];
            buffer.get(imageBytes);
            buffer.rewind();
            return imageBytes;
        } else if (img.getFormat() == ImageFormat.YUV_420_888) {
            buffer = mJpegByteBufferPool.acquire();
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(img.getWidth() * img.getHeight() * 3);
            }

            int numBytes = JpegUtilNative.compressJpegFromYUV420Image(
                    new AndroidImageProxy(img), buffer, JPEG_QUALITY,
                    degrees);

            if (numBytes < 0) {
                throw new RuntimeException("Error compressing jpeg.");
            }

            buffer.limit(numBytes);

            byte[] imageBytes = new byte[buffer.remaining()];
            buffer.get(imageBytes);

            buffer.clear();
            mJpegByteBufferPool.release(buffer);

            return imageBytes;
        } else {
            throw new RuntimeException("Unsupported image format.");
        }
    }

    private void startAFCycle() {
        // Clean up any existing AF cycle's pending callbacks.
        mCameraHandler.removeCallbacksAndMessages(FOCUS_RESUME_CALLBACK_TOKEN);

        // Send a single CONTROL_AF_TRIGGER_START capture request.
        sendAutoFocusTriggerRequest();

        // Immediately send a request for a regular preview stream, but with
        // CONTROL_AF_MODE_AUTO set so that the focus remains constant after the
        // AF cycle completes.
        sendAutoFocusHoldRequest();

        // Waits Settings3A.getFocusHoldMillis() milliseconds before sending
        // a request for a regular preview stream to resume.
        mCameraHandler.postAtTime(new Runnable() {
                @Override
            public void run() {
                mAERegions = ZERO_WEIGHT_3A_REGION;
                mAFRegions = ZERO_WEIGHT_3A_REGION;
                sendRepeatingCaptureRequest();
            }
        }, FOCUS_RESUME_CALLBACK_TOKEN,
                SystemClock.uptimeMillis() + Settings3A.getFocusHoldMillis());
    }

    /**
     * @see com.android.camera.one.OneCamera#triggerFocusAndMeterAtPoint(float,
     *      float)
     */
    @Override
    public void triggerFocusAndMeterAtPoint(float nx, float ny) {
        int sensorOrientation = mCharacteristics.get(
            CameraCharacteristics.SENSOR_ORIENTATION);
        mAERegions = AutoFocusHelper.aeRegionsForNormalizedCoord(nx, ny, mCropRegion, sensorOrientation);
        mAFRegions = AutoFocusHelper.afRegionsForNormalizedCoord(nx, ny, mCropRegion, sensorOrientation);

        startAFCycle();
    }

    @Override
    public Size pickPreviewSize(Size pictureSize, Context context) {
        if (pictureSize == null) {
            // TODO The default should be selected by the caller, and
            // pictureSize should never be null.
            pictureSize = getDefaultPictureSize();
        }
        float pictureAspectRatio = pictureSize.getWidth() / (float) pictureSize.getHeight();
        return CaptureModuleUtil.getOptimalPreviewSize(getSupportedPreviewSizes(),
              pictureAspectRatio);
    }

    @Override
    public float getMaxZoom() {
        return mCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    @Override
    public void setZoom(float zoom) {
        mZoomValue = zoom;
        mCropRegion = cropRegionForZoom(zoom);
        sendRepeatingCaptureRequest();
    }

    private Rect cropRegionForZoom(float zoom) {
        return AutoFocusHelper.cropRegionForZoom(mCharacteristics, zoom);
    }
}
