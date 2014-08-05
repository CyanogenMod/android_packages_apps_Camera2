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

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.OrientationEventListener;
import android.view.Surface;

import com.android.camera.Exif;
import com.android.camera.app.MediaSaver.OnMediaSavedListener;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.ExifTag;
import com.android.camera.exif.Rational;
import com.android.camera.one.AbstractOneCamera;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.Size;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link OneCamera} implementation directly on top of the Camera2 API.
 */
public class OneCameraImpl extends AbstractOneCamera {

    /** Captures that are requested but haven't completed yet. */
    private static class InFlightCapture {
        final PhotoCaptureParameters parameters;
        final CaptureSession session;

        public InFlightCapture(PhotoCaptureParameters parameters,
                CaptureSession session) {
            this.parameters = parameters;
            this.session = session;
        }
    }

    private static final Tag TAG = new Tag("OneCameraImpl2");
    /** Default JPEG encoding quality. */
    private static final Byte JPEG_QUALITY = 90;

    /** Thread on which the camera operations are running. */
    private final HandlerThread mCameraThread;
    /** Handler of the {@link #mCameraThread}. */
    private final Handler mCameraHandler;
    /** The characteristics of this camera. */
    private final CameraCharacteristics mCharacteristics;
    /** The underlying Camera2 API camera device. */
    private final CameraDevice mDevice;

    /**
     * The aspect ratio (width/height) of the full resolution for this camera.
     * Usually the native aspect ratio of this camera.
     */
    private final double mFullSizeAspectRatio;
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
    /** A callback that is called when the device is fully closed. */
    private CloseCallback mCloseCallback = null;

    /** Receives the normal JPEG captured images. */
    private final ImageReader mJpegImageReader;
    ImageReader.OnImageAvailableListener mJpegImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    InFlightCapture capture = mCaptureQueue.remove();

                    // Since this is not an HDR+ session, we will just save the result.
                    capture.session.startEmpty();
                    byte[] imageBytes = acquireJpegBytesAndClose(reader);
                    savePicture(imageBytes, capture.parameters, capture.session);
                    capture.parameters.callback.onPictureTaken(capture.session);
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
        mFullSizeAspectRatio = calculateFullSizeAspectRatio(characteristics);

        mCameraThread = new HandlerThread("OneCamera2");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mJpegImageReader = ImageReader.newInstance(pictureSize.getWidth(), pictureSize.getHeight(),
                ImageFormat.JPEG, 2);
        mJpegImageReader.setOnImageAvailableListener(mJpegImageListener, mCameraHandler);
        Log.d(TAG, "New Camera2 based OneCameraImpl created.");
    }

    @Override
    public void takePicture(PhotoCaptureParameters params, CaptureSession session) {
        // This will throw a RuntimeException, if parameters are not sane.
        params.checkSanity();
        try {
            // JPEG capture
            CaptureRequest.Builder builder = mDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY);
            builder.set(CaptureRequest.JPEG_ORIENTATION, getJpegRotation(params.orientation));
            builder.addTarget(mPreviewSurface);
            builder.addTarget(mJpegImageReader.getSurface());
            CaptureRequest c1 = builder.build();
            mCaptureSession.capture(c1, null, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera for JPEG capture.");
            params.callback.onPictureTakenFailed();
            return;
        }
        mCaptureQueue.add(new InFlightCapture(params, session));
    }

    @Override
    public void startPreview(Surface previewSurface, CaptureReadyCallback listener) {
        mPreviewSurface = previewSurface;
        setupAsync(mPreviewSurface, listener);
    }

    @Override
    public void setViewFinderSize(int width, int height) {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public boolean isFlashSupported(boolean enhanced) {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public boolean isSupportingEnhancedMode() {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public void close(CloseCallback closeCallback) {
        if (mIsClosed) {
            Log.w(TAG, "Camera is already closed.");
            return;
        }
        mIsClosed = true;
        mCloseCallback = closeCallback;
        mCameraThread.quitSafely();
        mDevice.close();
    }

    @Override
    public Size[] getSupportedSizes() {
        StreamConfigurationMap config = mCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return Size.convert(config.getOutputSizes(ImageFormat.JPEG));
    }

    @Override
    public double getFullSizeAspectRatio() {
        return mFullSizeAspectRatio;
    }

    @Override
    public boolean isFrontFacing() {
        return mCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraMetadata.LENS_FACING_FRONT;
    }

    @Override
    public boolean isBackFacing() {
        return mCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraMetadata.LENS_FACING_BACK;
    }

    private void savePicture(byte[] jpegData, final PhotoCaptureParameters captureParams,
            CaptureSession session) {
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
        } catch (IOException e) {
            Log.w(TAG, "Could not read exif from gcam jpeg", e);
            exif = null;
        }
        session.saveAndFinish(jpegData, width, height, rotation, exif, new OnMediaSavedListener() {
            @Override
            public void onMediaSaved(Uri uri) {
                captureParams.callback.onPictureSaved(uri);
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
            outputSurfaces.add(mJpegImageReader.getSurface());

            mDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateListener() {

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    listener.onSetupFailed();
                }

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCaptureSession = session;
                    startPreviewInternal(listener);
                }

                @Override
                public void onClosed(CameraCaptureSession session) {
                    super.onClosed(session);
                    if (mCloseCallback != null) {
                        mCloseCallback.onCameraClosed();
                    }
                }
            }, mCameraHandler);
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not set up capture session", ex);
            listener.onSetupFailed();
        }
    }

    /**
     * Configures and creates the request for starting the preview.
     *
     * @param listener called when request was build and sent, or if setting up
     *            the request failed.
     */
    private void startPreviewInternal(CaptureReadyCallback listener) {
        Log.v(TAG, "issuePreviewCaptureRequest.");
        try {
            CaptureRequest.Builder builder = mDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.addTarget(mPreviewSurface);
            mCaptureSession.setRepeatingRequest(builder.build(), null, mCameraHandler);
            listener.onReadyForCapture();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera setting up preview.");
            listener.onSetupFailed();
        }
    }

    /**
     * Calculate the aspect ratio of the full size capture on this device.
     *
     * @param characteristics the characteristics of the camera device.
     * @return The aspect ration, in terms of width/height of the full capture
     *         size.
     */
    private static double calculateFullSizeAspectRatio(CameraCharacteristics characteristics) {
        Rect activeArraySize =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        return (double) activeArraySize.width() / activeArraySize.height();
    }

    /**
     * Given an image reader, extracts the JPEG image bytes and then closes the
     * reader.
     *
     * @param reader the reader to read the JPEG data from.
     * @return The bytes of the JPEG image. Newly allocated.
     */
    private static byte[] acquireJpegBytesAndClose(ImageReader reader) {
        Image img = reader.acquireLatestImage();
        Image.Plane plane0 = img.getPlanes()[0];
        ByteBuffer buffer = plane0.getBuffer();

        byte[] imageBytes = new byte[buffer.remaining()];
        buffer.get(imageBytes);
        buffer.rewind();
        img.close();
        return imageBytes;
    }

    /**
     * Given the device orientation, this returns the required JPEG rotation for
     * this camera.
     *
     * @param deviceOrientationDegrees the device orientation in degrees.
     * @return The JPEG orientation in degrees.
     */
    private int getJpegRotation(int deviceOrientationDegrees) {
        if (deviceOrientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0;
        }
        int facing = mCharacteristics.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = mCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (facing == CameraMetadata.LENS_FACING_FRONT) {
            return (sensorOrientation - deviceOrientationDegrees + 360) % 360;
        } else {
            return (sensorOrientation + deviceOrientationDegrees) % 360;
        }
    }
}
