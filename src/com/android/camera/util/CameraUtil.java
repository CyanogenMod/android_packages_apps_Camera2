/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.location.Location;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import com.android.camera.CameraActivity;
import com.android.camera.CameraDisabledException;
import com.android.camera.FatalErrorHandler;
import com.android.camera.debug.Log;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraCapabilities;
import com.android.ex.camera2.portability.CameraSettings;

import java.io.Closeable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Collection of utility functions used in this package.
 */
@Deprecated
public class CameraUtil {
    private static final Log.Tag TAG = new Log.Tag("CameraUtil");

    private static class Singleton {
        private static final CameraUtil INSTANCE = new CameraUtil(
              AndroidContext.instance().get());
    }

    /**
     * Thread safe CameraUtil instance.
     */
    public static CameraUtil instance() {
        return Singleton.INSTANCE;
    }

    // For calculate the best fps range for still image capture.
    private final static int MAX_PREVIEW_FPS_TIMES_1000 = 400000;
    private final static int PREFERRED_PREVIEW_FPS_TIMES_1000 = 30000;

    // For creating crop intents.
    public static final String KEY_RETURN_DATA = "return-data";
    public static final String KEY_SHOW_WHEN_LOCKED = "showWhenLocked";

    /** Orientation hysteresis amount used in rounding, in degrees. */
    public static final int ORIENTATION_HYSTERESIS = 5;

    public static final String REVIEW_ACTION = "com.android.camera.action.REVIEW";
    /** See android.hardware.Camera.ACTION_NEW_PICTURE. */
    public static final String ACTION_NEW_PICTURE = "android.hardware.action.NEW_PICTURE";
    /** See android.hardware.Camera.ACTION_NEW_VIDEO. */
    public static final String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";

    /**
     * Broadcast Action: The camera application has become active in
     * picture-taking mode.
     */
    public static final String ACTION_CAMERA_STARTED = "com.android.camera.action.CAMERA_STARTED";
    /**
     * Broadcast Action: The camera application is no longer in active
     * picture-taking mode.
     */
    public static final String ACTION_CAMERA_STOPPED = "com.android.camera.action.CAMERA_STOPPED";
    /**
     * When the camera application is active in picture-taking mode, it listens
     * for this intent, which upon receipt will trigger the shutter to capture a
     * new picture, as if the user had pressed the shutter button.
     */
    public static final String ACTION_CAMERA_SHUTTER_CLICK =
            "com.android.camera.action.SHUTTER_CLICK";

    // Fields for the show-on-maps-functionality
    private static final String MAPS_PACKAGE_NAME = "com.google.android.apps.maps";
    private static final String MAPS_CLASS_NAME = "com.google.android.maps.MapsActivity";

    /** Has to be in sync with the receiving MovieActivity. */
    public static final String KEY_TREAT_UP_AS_BACK = "treat-up-as-back";

    /** Private intent extras. Test only. */
    private static final String EXTRAS_CAMERA_FACING =
            "android.intent.extras.CAMERA_FACING";

    private final ImageFileNamer mImageFileNamer;

    private CameraUtil(Context context) {
        mImageFileNamer = new ImageFileNamer(
              context.getString(R.string.image_file_name_format));
    }

    /**
     * Rotates the bitmap by the specified degree. If a new bitmap is created,
     * the original bitmap is recycled.
     */
    public static Bitmap rotate(Bitmap b, int degrees) {
        return rotateAndMirror(b, degrees, false);
    }

    /**
     * Rotates and/or mirrors the bitmap. If a new bitmap is created, the
     * original bitmap is recycled.
     */
    public static Bitmap rotateAndMirror(Bitmap b, int degrees, boolean mirror) {
        if ((degrees != 0 || mirror) && b != null) {
            Matrix m = new Matrix();
            // Mirror first.
            // horizontal flip + rotation = -rotation + horizontal flip
            if (mirror) {
                m.postScale(-1, 1);
                degrees = (degrees + 360) % 360;
                if (degrees == 0 || degrees == 180) {
                    m.postTranslate(b.getWidth(), 0);
                } else if (degrees == 90 || degrees == 270) {
                    m.postTranslate(b.getHeight(), 0);
                } else {
                    throw new IllegalArgumentException("Invalid degrees=" + degrees);
                }
            }
            if (degrees != 0) {
                // clockwise
                m.postRotate(degrees,
                        (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            }

            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            }
        }
        return b;
    }

    /**
     * Compute the sample size as a function of minSideLength and
     * maxNumOfPixels. minSideLength is used to specify that minimal width or
     * height of a bitmap. maxNumOfPixels is used to specify the maximal size in
     * pixels that is tolerable in terms of memory usage. The function returns a
     * sample size based on the constraints.
     * <p>
     * Both size and minSideLength can be passed in as -1 which indicates no
     * care of the corresponding constraint. The functions prefers returning a
     * sample size that generates a smaller bitmap, unless minSideLength = -1.
     * <p>
     * Also, the function rounds up the sample size to a power of 2 or multiple
     * of 8 because BitmapFactory only honors sample size this way. For example,
     * BitmapFactory downsamples an image by 2 even though the request is 3. So
     * we round up the sample size to avoid OOM.
     */
    public static int computeSampleSize(BitmapFactory.Options options,
            int minSideLength, int maxNumOfPixels) {
        int initialSize = computeInitialSampleSize(options, minSideLength,
                maxNumOfPixels);

        int roundedSize;
        if (initialSize <= 8) {
            roundedSize = 1;
            while (roundedSize < initialSize) {
                roundedSize <<= 1;
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8;
        }

        return roundedSize;
    }

    private static int computeInitialSampleSize(BitmapFactory.Options options,
            int minSideLength, int maxNumOfPixels) {
        double w = options.outWidth;
        double h = options.outHeight;

        int lowerBound = (maxNumOfPixels < 0) ? 1 :
                (int) Math.ceil(Math.sqrt(w * h / maxNumOfPixels));
        int upperBound = (minSideLength < 0) ? 128 :
                (int) Math.min(Math.floor(w / minSideLength),
                        Math.floor(h / minSideLength));

        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound;
        }

        if (maxNumOfPixels < 0 && minSideLength < 0) {
            return 1;
        } else if (minSideLength < 0) {
            return lowerBound;
        } else {
            return upperBound;
        }
    }

    public static Bitmap makeBitmap(byte[] jpegData, int maxNumOfPixels) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length,
                    options);
            if (options.mCancel || options.outWidth == -1
                    || options.outHeight == -1) {
                return null;
            }
            options.inSampleSize = computeSampleSize(
                    options, -1, maxNumOfPixels);
            options.inJustDecodeBounds = false;

            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length,
                    options);
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "Got oom exception ", ex);
            return null;
        }
    }

    public static void closeSilently(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Throwable t) {
            // do nothing
        }
    }

    public static void Assert(boolean cond) {
        if (!cond) {
            throw new AssertionError();
        }
    }

    /**
     * Shows custom error dialog. Designed specifically
     * for the scenario where the camera cannot be attached.
     * @deprecated Use {@link FatalErrorHandler} instead.
     */
    @Deprecated
    public static void showError(final Activity activity, final int dialogMsgId, final int feedbackMsgId,
                                 final boolean finishActivity, final Exception ex) {
        final DialogInterface.OnClickListener buttonListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (finishActivity) {
                            activity.finish();
                        }
                    }
                };

        DialogInterface.OnClickListener reportButtonListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new GoogleHelpHelper(activity).sendGoogleFeedback(feedbackMsgId, ex);
                        if (finishActivity) {
                            activity.finish();
                        }
                    }
                };
        TypedValue out = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.alertDialogIcon, out, true);
        // Some crash reports indicate users leave app prior to this dialog
        // appearing, so check to ensure that the activity is not shutting down
        // before attempting to attach a dialog to the window manager.
        if (!activity.isFinishing()) {
            Log.e(TAG, "Show fatal error dialog");
            new AlertDialog.Builder(activity)
                    .setCancelable(false)
                    .setTitle(R.string.camera_error_title)
                    .setMessage(dialogMsgId)
                    .setNegativeButton(R.string.dialog_report, reportButtonListener)
                    .setPositiveButton(R.string.dialog_dismiss, buttonListener)
                    .setIcon(out.resourceId)
                    .show();
        }
    }

    public static <T> T checkNotNull(T object) {
        if (object == null) {
            throw new NullPointerException();
        }
        return object;
    }

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a == null ? false : a.equals(b));
    }

    public static int nextPowerOf2(int n) {
        // TODO: what happens if n is negative or already a power of 2?
        n -= 1;
        n |= n >>> 16;
        n |= n >>> 8;
        n |= n >>> 4;
        n |= n >>> 2;
        n |= n >>> 1;
        return n + 1;
    }

    public static float distance(float x, float y, float sx, float sy) {
        float dx = x - sx;
        float dy = y - sy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Clamps x to between min and max (inclusive on both ends, x = min --> min,
     * x = max --> max).
     */
    public static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    /**
     * Clamps x to between min and max (inclusive on both ends, x = min --> min,
     * x = max --> max).
     */
    public static float clamp(float x, float min, float max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    /**
     * Linear interpolation between a and b by the fraction t. t = 0 --> a, t =
     * 1 --> b.
     */
    public static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    /**
     * Given (nx, ny) \in [0, 1]^2, in the display's portrait coordinate system,
     * returns normalized sensor coordinates \in [0, 1]^2 depending on how the
     * sensor's orientation \in {0, 90, 180, 270}.
     * <p>
     * Returns null if sensorOrientation is not one of the above.
     * </p>
     */
    public static PointF normalizedSensorCoordsForNormalizedDisplayCoords(
            float nx, float ny, int sensorOrientation) {
        switch (sensorOrientation) {
            case 0:
                return new PointF(nx, ny);
            case 90:
                return new PointF(ny, 1.0f - nx);
            case 180:
                return new PointF(1.0f - nx, 1.0f - ny);
            case 270:
                return new PointF(1.0f - ny, nx);
            default:
                return null;
        }
    }

    /**
     * Given a size, return the largest size with the given aspectRatio that
     * maximally fits into the bounding rectangle of the original Size.
     *
     * @param size the original Size to crop
     * @param aspectRatio the target aspect ratio
     * @return the largest Size with the given aspect ratio that is smaller than
     *         or equal to the original Size.
     */
    public static Size constrainToAspectRatio(Size size, float aspectRatio) {
        float width = size.getWidth();
        float height = size.getHeight();

        float currentAspectRatio = width * 1.0f / height;

        if (currentAspectRatio > aspectRatio) {
            // chop longer side
            if (width > height) {
                width = height * aspectRatio;
            } else {
                height = width / aspectRatio;
            }
        } else if (currentAspectRatio < aspectRatio) {
            // chop shorter side
            if (width < height) {
                width = height * aspectRatio;
            } else {
                height = width / aspectRatio;
            }
        }

        return new Size((int) width, (int) height);
    }

    public static int getDisplayRotation() {
        WindowManager windowManager = AndroidServices.instance().provideWindowManager();
        int rotation = windowManager.getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
        }
        return 0;
    }

    private static Size getDefaultDisplaySize() {
        WindowManager windowManager = AndroidServices.instance().provideWindowManager();
        Point res = new Point();
        windowManager.getDefaultDisplay().getSize(res);
        return new Size(res);
    }

    public static Size getOptimalPreviewSize(List<Size> sizes, double targetRatio) {
        int optimalPickIndex = getOptimalPreviewSizeIndex(sizes, targetRatio);
        if (optimalPickIndex == -1) {
            return null;
        } else {
            return sizes.get(optimalPickIndex);
        }
    }

    /**
     * Returns the index into 'sizes' that is most optimal given the current
     * screen and target aspect ratio..
     * <p>
     * This is using a default aspect ratio tolerance. If the tolerance is to be
     * given you should call
     * {@link #getOptimalPreviewSizeIndex(List, double, Double)}
     *
     * @param sizes the available preview sizes
     * @param targetRatio the target aspect ratio, typically the aspect ratio of
     *            the picture size
     * @return The index into 'previewSizes' for the optimal size, or -1, if no
     *         matching size was found.
     */
    public static int getOptimalPreviewSizeIndex(List<Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match. HTC 4:3
        // ratios is over .01 from true 4:3, so this value must be above .01,
        // see b/18241645.
        final double aspectRatioTolerance = 0.02;

        return getOptimalPreviewSizeIndex(sizes, targetRatio, aspectRatioTolerance);
    }

    /**
     * Returns the index into 'sizes' that is most optimal given the current
     * screen, target aspect ratio and tolerance.
     *
     * @param previewSizes the available preview sizes
     * @param targetRatio the target aspect ratio, typically the aspect ratio of
     *            the picture size
     * @param aspectRatioTolerance the tolerance we allow between the selected
     *            preview size's aspect ratio and the target ratio. If this is
     *            set to 'null', the default value is used.
     * @return The index into 'previewSizes' for the optimal size, or -1, if no
     *         matching size was found.
     */
    public static int getOptimalPreviewSizeIndex(
            List<Size> previewSizes, double targetRatio, Double aspectRatioTolerance) {
        if (previewSizes == null) {
            return -1;
        }

        // If no particular aspect ratio tolerance is set, use the default
        // value.
        if (aspectRatioTolerance == null) {
            return getOptimalPreviewSizeIndex(previewSizes, targetRatio);
        }

        int optimalSizeIndex = -1;
        double minDiff = Double.MAX_VALUE;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of preview surface. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size.
        Size defaultDisplaySize = getDefaultDisplaySize();
        int targetHeight = Math.min(defaultDisplaySize.getWidth(), defaultDisplaySize.getHeight());
        // Try to find an size match aspect ratio and size
        for (int i = 0; i < previewSizes.size(); i++) {
            Size size = previewSizes.get(i);
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(ratio - targetRatio) > aspectRatioTolerance) {
                continue;
            }

            double heightDiff = Math.abs(size.getHeight() - targetHeight);
            if (heightDiff < minDiff) {
                optimalSizeIndex = i;
                minDiff = heightDiff;
            } else if (heightDiff == minDiff) {
                // Prefer resolutions smaller-than-display when an equally close
                // larger-than-display resolution is available
                if (size.getHeight() < targetHeight) {
                    optimalSizeIndex = i;
                    minDiff = heightDiff;
                }
            }
        }
        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSizeIndex == -1) {
            Log.w(TAG, "No preview size match the aspect ratio. available sizes: " + previewSizes);
            minDiff = Double.MAX_VALUE;
            for (int i = 0; i < previewSizes.size(); i++) {
                Size size = previewSizes.get(i);
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSizeIndex = i;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }

        return optimalSizeIndex;
    }

    /**
     * Returns the largest picture size which matches the given aspect ratio,
     * except for the special WYSIWYG case where the picture size exactly
     * matches the target size.
     *
     * @param sizes a list of candidate sizes, available for use
     * @param targetWidth the ideal width of the video snapshot
     * @param targetHeight the ideal height of the video snapshot
     * @return the Optimal Video Snapshot Picture Size
     */
    public static Size getOptimalVideoSnapshotPictureSize(
            List<Size> sizes, int targetWidth,
            int targetHeight) {

        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null) {
            return null;
        }

        Size optimalSize = null;

        // WYSIWYG Override
        // We assume that physical display constraints have already been
        // imposed on the variables sizes
        for (Size size : sizes) {
            if (size.height() == targetHeight && size.width() == targetWidth) {
                return size;
            }
        }

        // Try to find a size matches aspect ratio and has the largest width
        final double targetRatio = (double) targetWidth / targetHeight;
        for (Size size : sizes) {
            double ratio = (double) size.width() / size.height();
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
                continue;
            }
            if (optimalSize == null || size.width() > optimalSize.width()) {
                optimalSize = size;
            }
        }

        // Cannot find one that matches the aspect ratio. This should not
        // happen. Ignore the requirement.
        if (optimalSize == null) {
            Log.w(TAG, "No picture size match the aspect ratio");
            for (Size size : sizes) {
                if (optimalSize == null || size.width() > optimalSize.width()) {
                    optimalSize = size;
                }
            }
        }
        return optimalSize;
    }

    // This is for test only. Allow the camera to launch the specific camera.
    public static int getCameraFacingIntentExtras(Activity currentActivity) {
        int cameraId = -1;

        int intentCameraId =
                currentActivity.getIntent().getIntExtra(CameraUtil.EXTRAS_CAMERA_FACING, -1);

        if (isFrontCameraIntent(intentCameraId)) {
            // Check if the front camera exist
            int frontCameraId = ((CameraActivity) currentActivity).getCameraProvider()
                    .getFirstFrontCameraId();
            if (frontCameraId != -1) {
                cameraId = frontCameraId;
            }
        } else if (isBackCameraIntent(intentCameraId)) {
            // Check if the back camera exist
            int backCameraId = ((CameraActivity) currentActivity).getCameraProvider()
                    .getFirstBackCameraId();
            if (backCameraId != -1) {
                cameraId = backCameraId;
            }
        }
        return cameraId;
    }

    private static boolean isFrontCameraIntent(int intentCameraId) {
        return (intentCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    private static boolean isBackCameraIntent(int intentCameraId) {
        return (intentCameraId == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    private static int sLocation[] = new int[2];

    // This method is not thread-safe.
    public static boolean pointInView(float x, float y, View v) {
        v.getLocationInWindow(sLocation);
        return x >= sLocation[0] && x < (sLocation[0] + v.getWidth())
                && y >= sLocation[1] && y < (sLocation[1] + v.getHeight());
    }

    public static int[] getRelativeLocation(View reference, View view) {
        reference.getLocationInWindow(sLocation);
        int referenceX = sLocation[0];
        int referenceY = sLocation[1];
        view.getLocationInWindow(sLocation);
        sLocation[0] -= referenceX;
        sLocation[1] -= referenceY;
        return sLocation;
    }

    public static boolean isUriValid(Uri uri, ContentResolver resolver) {
        if (uri == null) {
            return false;
        }

        try {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
            if (pfd == null) {
                Log.e(TAG, "Fail to open URI. URI=" + uri);
                return false;
            }
            pfd.close();
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    public static void dumpRect(RectF rect, String msg) {
        Log.v(TAG, msg + "=(" + rect.left + "," + rect.top
                + "," + rect.right + "," + rect.bottom + ")");
    }

    public static void inlineRectToRectF(RectF rectF, Rect rect) {
        rect.left = Math.round(rectF.left);
        rect.top = Math.round(rectF.top);
        rect.right = Math.round(rectF.right);
        rect.bottom = Math.round(rectF.bottom);
    }

    public static Rect rectFToRect(RectF rectF) {
        Rect rect = new Rect();
        inlineRectToRectF(rectF, rect);
        return rect;
    }

    public static RectF rectToRectF(Rect r) {
        return new RectF(r.left, r.top, r.right, r.bottom);
    }

    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
            int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }

    public String createJpegName(long dateTaken) {
        synchronized (mImageFileNamer) {
            return mImageFileNamer.generateName(dateTaken);
        }
    }

    public static void broadcastNewPicture(Context context, Uri uri) {
        context.sendBroadcast(new Intent(ACTION_NEW_PICTURE, uri));
        // Keep compatibility
        context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));
    }

    public static void fadeIn(View view, float startAlpha, float endAlpha, long duration) {
        if (view.getVisibility() == View.VISIBLE) {
            return;
        }

        view.setVisibility(View.VISIBLE);
        Animation animation = new AlphaAnimation(startAlpha, endAlpha);
        animation.setDuration(duration);
        view.startAnimation(animation);
    }

    public static void setGpsParameters(CameraSettings settings, Location loc) {
        // Clear previous GPS location from the parameters.
        settings.clearGpsData();

        boolean hasLatLon = false;
        double lat;
        double lon;
        // Set GPS location.
        if (loc != null) {
            lat = loc.getLatitude();
            lon = loc.getLongitude();
            hasLatLon = (lat != 0.0d) || (lon != 0.0d);
        }

        if (!hasLatLon) {
            // We always encode GpsTimeStamp even if the GPS location is not
            // available.
            settings.setGpsData(
                    new CameraSettings.GpsData(0f, 0f, 0f, System.currentTimeMillis() / 1000, null)
                    );
        } else {
            Log.d(TAG, "Set gps location");
            // for NETWORK_PROVIDER location provider, we may have
            // no altitude information, but the driver needs it, so
            // we fake one.
            // Location.getTime() is UTC in milliseconds.
            // gps-timestamp is UTC in seconds.
            long utcTimeSeconds = loc.getTime() / 1000;
            settings.setGpsData(new CameraSettings.GpsData(loc.getLatitude(), loc.getLongitude(),
                    (loc.hasAltitude() ? loc.getAltitude() : 0),
                    (utcTimeSeconds != 0 ? utcTimeSeconds : System.currentTimeMillis()),
                    loc.getProvider().toUpperCase()));
        }
    }

    /**
     * For still image capture, we need to get the right fps range such that the
     * camera can slow down the framerate to allow for less-noisy/dark
     * viewfinder output in dark conditions.
     *
     * @param capabilities Camera's capabilities.
     * @return null if no appropiate fps range can't be found. Otherwise, return
     *         the right range.
     */
    public static int[] getPhotoPreviewFpsRange(CameraCapabilities capabilities) {
        return getPhotoPreviewFpsRange(capabilities.getSupportedPreviewFpsRange());
    }

    public static int[] getPhotoPreviewFpsRange(List<int[]> frameRates) {
        if (frameRates.size() == 0) {
            Log.e(TAG, "No suppoted frame rates returned!");
            return null;
        }

        // Find the lowest min rate in supported ranges who can cover 30fps.
        int lowestMinRate = MAX_PREVIEW_FPS_TIMES_1000;
        for (int[] rate : frameRates) {
            int minFps = rate[0];
            int maxFps = rate[1];
            if (maxFps >= PREFERRED_PREVIEW_FPS_TIMES_1000 &&
                    minFps <= PREFERRED_PREVIEW_FPS_TIMES_1000 &&
                    minFps < lowestMinRate) {
                lowestMinRate = minFps;
            }
        }

        // Find all the modes with the lowest min rate found above, the pick the
        // one with highest max rate.
        int resultIndex = -1;
        int highestMaxRate = 0;
        for (int i = 0; i < frameRates.size(); i++) {
            int[] rate = frameRates.get(i);
            int minFps = rate[0];
            int maxFps = rate[1];
            if (minFps == lowestMinRate && highestMaxRate < maxFps) {
                highestMaxRate = maxFps;
                resultIndex = i;
            }
        }

        if (resultIndex >= 0) {
            return frameRates.get(resultIndex);
        }
        Log.e(TAG, "Can't find an appropiate frame rate range!");
        return null;
    }

    public static int[] getMaxPreviewFpsRange(List<int[]> frameRates) {
        if (frameRates != null && frameRates.size() > 0) {
            // The list is sorted. Return the last element.
            return frameRates.get(frameRates.size() - 1);
        }
        return new int[0];
    }

    public static void throwIfCameraDisabled() throws CameraDisabledException {
        // Check if device policy has disabled the camera.
        DevicePolicyManager dpm = AndroidServices.instance().provideDevicePolicyManager();
        if (dpm.getCameraDisabled(null)) {
            throw new CameraDisabledException();
        }
    }

    /**
     * Generates a 1d Gaussian mask of the input array size, and store the mask
     * in the input array.
     *
     * @param mask empty array of size n, where n will be used as the size of
     *            the Gaussian mask, and the array will be populated with the
     *            values of the mask.
     */
    private static void getGaussianMask(float[] mask) {
        int len = mask.length;
        int mid = len / 2;
        float sigma = len;
        float sum = 0;
        for (int i = 0; i <= mid; i++) {
            float ex = (float) Math.exp(-(i - mid) * (i - mid) / (mid * mid))
                    / (2 * sigma * sigma);
            int symmetricIndex = len - 1 - i;
            mask[i] = ex;
            mask[symmetricIndex] = ex;
            sum += mask[i];
            if (i != symmetricIndex) {
                sum += mask[symmetricIndex];
            }
        }

        for (int i = 0; i < mask.length; i++) {
            mask[i] /= sum;
        }

    }

    /**
     * Add two pixels together where the second pixel will be applied with a
     * weight.
     *
     * @param pixel pixel color value of weight 1
     * @param newPixel second pixel color value where the weight will be applied
     * @param weight a float weight that will be applied to the second pixel
     *            color
     * @return the weighted addition of the two pixels
     */
    public static int addPixel(int pixel, int newPixel, float weight) {
        // TODO: scale weight to [0, 1024] to avoid casting to float and back to
        // int.
        int r = ((pixel & 0x00ff0000) + (int) ((newPixel & 0x00ff0000) * weight)) & 0x00ff0000;
        int g = ((pixel & 0x0000ff00) + (int) ((newPixel & 0x0000ff00) * weight)) & 0x0000ff00;
        int b = ((pixel & 0x000000ff) + (int) ((newPixel & 0x000000ff) * weight)) & 0x000000ff;
        return 0xff000000 | r | g | b;
    }

    /**
     * Apply blur to the input image represented in an array of colors and put
     * the output image, in the form of an array of colors, into the output
     * array.
     *
     * @param src source array of colors
     * @param out output array of colors after the blur
     * @param w width of the image
     * @param h height of the image
     * @param size size of the Gaussian blur mask
     */
    public static void blur(int[] src, int[] out, int w, int h, int size) {
        float[] k = new float[size];
        int off = size / 2;

        getGaussianMask(k);

        int[] tmp = new int[src.length];

        // Apply the 1d Gaussian mask horizontally to the image and put the
        // intermediat results in a temporary array.
        int rowPointer = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int i = 0; i < k.length; i++) {
                    int dx = x + i - off;
                    dx = clamp(dx, 0, w - 1);
                    sum = addPixel(sum, src[rowPointer + dx], k[i]);
                }
                tmp[x + rowPointer] = sum;
            }
            rowPointer += w;
        }

        // Apply the 1d Gaussian mask vertically to the intermediate array, and
        // the final results will be stored in the output array.
        for (int x = 0; x < w; x++) {
            rowPointer = 0;
            for (int y = 0; y < h; y++) {
                int sum = 0;
                for (int i = 0; i < k.length; i++) {
                    int dy = y + i - off;
                    dy = clamp(dy, 0, h - 1);
                    sum = addPixel(sum, tmp[dy * w + x], k[i]);
                }
                out[x + rowPointer] = sum;
                rowPointer += w;
            }
        }
    }

    /**
     * Calculates a new dimension to fill the bound with the original aspect
     * ratio preserved.
     *
     * @param imageWidth The original width.
     * @param imageHeight The original height.
     * @param imageRotation The clockwise rotation in degrees of the image which
     *            the original dimension comes from.
     * @param boundWidth The width of the bound.
     * @param boundHeight The height of the bound.
     * @returns The final width/height stored in Point.x/Point.y to fill the
     *          bounds and preserve image aspect ratio.
     */
    public static Point resizeToFill(int imageWidth, int imageHeight, int imageRotation,
            int boundWidth, int boundHeight) {
        if (imageRotation % 180 != 0) {
            // Swap width and height.
            int savedWidth = imageWidth;
            imageWidth = imageHeight;
            imageHeight = savedWidth;
        }

        Point p = new Point();
        p.x = boundWidth;
        p.y = boundHeight;

        // In some cases like automated testing, image height/width may not be
        // loaded, to avoid divide by zero fall back to provided bounds.
        if (imageWidth != 0 && imageHeight != 0) {
            if (imageWidth * boundHeight > boundWidth * imageHeight) {
                p.y = imageHeight * p.x / imageWidth;
            } else {
                p.x = imageWidth * p.y / imageHeight;
            }
        } else {
            Log.w(TAG, "zero width/height, falling back to bounds (w|h|bw|bh):"
                    + imageWidth + "|" + imageHeight + "|" + boundWidth + "|"
                    + boundHeight);
        }

        return p;
    }

    private static class ImageFileNamer {
        private final SimpleDateFormat mFormat;

        // The date (in milliseconds) used to generate the last name.
        private long mLastDate;

        // Number of names generated for the same second.
        private int mSameSecondCount;

        public ImageFileNamer(String format) {
            mFormat = new SimpleDateFormat(format);
        }

        public String generateName(long dateTaken) {
            Date date = new Date(dateTaken);
            String result = mFormat.format(date);

            // If the last name was generated for the same second,
            // we append _1, _2, etc to the name.
            if (dateTaken / 1000 == mLastDate / 1000) {
                mSameSecondCount++;
                result += "_" + mSameSecondCount;
            } else {
                mLastDate = dateTaken;
                mSameSecondCount = 0;
            }

            return result;
        }
    }

    public static void playVideo(CameraActivity activity, Uri uri, String title) {
        try {
            boolean isSecureCamera = activity.isSecureCamera();
            if (!isSecureCamera) {
                Intent intent = IntentHelper.getVideoPlayerIntent(uri)
                        .putExtra(Intent.EXTRA_TITLE, title)
                        .putExtra(KEY_TREAT_UP_AS_BACK, true);
                activity.launchActivityByIntent(intent);
            } else {
                // In order not to send out any intent to be intercepted and
                // show the lock screen immediately, we just let the secure
                // camera activity finish.
                activity.finish();
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.video_err),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Starts GMM with the given location shown. If this fails, and GMM could
     * not be found, we use a geo intent as a fallback.
     *
     * @param activity the activity to use for launching the Maps intent.
     * @param latLong a 2-element array containing {latitude/longitude}.
     */
    public static void showOnMap(Activity activity, double[] latLong) {
        try {
            // We don't use "geo:latitude,longitude" because it only centers
            // the MapView to the specified location, but we need a marker
            // for further operations (routing to/from).
            // The q=(lat, lng) syntax is suggested by geo-team.
            String uri = String.format(Locale.ENGLISH, "http://maps.google.com/maps?f=q&q=(%f,%f)",
                    latLong[0], latLong[1]);
            ComponentName compName = new ComponentName(MAPS_PACKAGE_NAME,
                    MAPS_CLASS_NAME);
            Intent mapsIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(uri)).setComponent(compName);
            mapsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            activity.startActivity(mapsIntent);
        } catch (ActivityNotFoundException e) {
            // Use the "geo intent" if no GMM is installed
            Log.e(TAG, "GMM activity not found!", e);
            String url = String.format(Locale.ENGLISH, "geo:%f,%f", latLong[0], latLong[1]);
            Intent mapsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(mapsIntent);
        }
    }

    /**
     * Dumps the stack trace.
     *
     * @param level How many levels of the stack are dumped. 0 means all.
     * @return A {@link java.lang.String} of all the output with newline between
     *         each.
     */
    public static String dumpStackTrace(int level) {
        StackTraceElement[] elems = Thread.currentThread().getStackTrace();
        // Ignore the first 3 elements.
        level = (level == 0 ? elems.length : Math.min(level + 3, elems.length));
        String ret = new String();
        for (int i = 3; i < level; i++) {
            ret = ret + "\t" + elems[i].toString() + '\n';
        }
        return ret;
    }

    /**
     * Gets the theme color of a specific mode.
     *
     * @param modeIndex index of the mode
     * @param context current context
     * @return theme color of the mode if input index is valid, otherwise 0
     */
    public static int getCameraThemeColorId(int modeIndex, Context context) {

        // Find the theme color using id from the color array
        TypedArray colorRes = context.getResources()
                .obtainTypedArray(R.array.camera_mode_theme_color);
        if (modeIndex >= colorRes.length() || modeIndex < 0) {
            // Mode index not found
            Log.e(TAG, "Invalid mode index: " + modeIndex);
            return 0;
        }
        return colorRes.getResourceId(modeIndex, 0);
    }

    /**
     * Gets the mode icon resource id of a specific mode.
     *
     * @param modeIndex index of the mode
     * @param context current context
     * @return icon resource id if the index is valid, otherwise 0
     */
    public static int getCameraModeIconResId(int modeIndex, Context context) {
        // Find the camera mode icon using id
        TypedArray cameraModesIcons = context.getResources()
                .obtainTypedArray(R.array.camera_mode_icon);
        if (modeIndex >= cameraModesIcons.length() || modeIndex < 0) {
            // Mode index not found
            Log.e(TAG, "Invalid mode index: " + modeIndex);
            return 0;
        }
        return cameraModesIcons.getResourceId(modeIndex, 0);
    }

    /**
     * Gets the mode text of a specific mode.
     *
     * @param modeIndex index of the mode
     * @param context current context
     * @return mode text if the index is valid, otherwise a new empty string
     */
    public static String getCameraModeText(int modeIndex, Context context) {
        // Find the camera mode icon using id
        String[] cameraModesText = context.getResources()
                .getStringArray(R.array.camera_mode_text);
        if (modeIndex < 0 || modeIndex >= cameraModesText.length) {
            Log.e(TAG, "Invalid mode index: " + modeIndex);
            return new String();
        }
        return cameraModesText[modeIndex];
    }

    /**
     * Gets the mode content description of a specific mode.
     *
     * @param modeIndex index of the mode
     * @param context current context
     * @return mode content description if the index is valid, otherwise a new
     *         empty string
     */
    public static String getCameraModeContentDescription(int modeIndex, Context context) {
        String[] cameraModesDesc = context.getResources()
                .getStringArray(R.array.camera_mode_content_description);
        if (modeIndex < 0 || modeIndex >= cameraModesDesc.length) {
            Log.e(TAG, "Invalid mode index: " + modeIndex);
            return new String();
        }
        return cameraModesDesc[modeIndex];
    }

    /**
     * Gets the shutter icon res id for a specific mode.
     *
     * @param modeIndex index of the mode
     * @param context current context
     * @return mode shutter icon id if the index is valid, otherwise 0.
     */
    public static int getCameraShutterIconId(int modeIndex, Context context) {
        // Find the camera mode icon using id
        TypedArray shutterIcons = context.getResources()
                .obtainTypedArray(R.array.camera_mode_shutter_icon);
        if (modeIndex < 0 || modeIndex >= shutterIcons.length()) {
            Log.e(TAG, "Invalid mode index: " + modeIndex);
            throw new IllegalStateException("Invalid mode index: " + modeIndex);
        }
        return shutterIcons.getResourceId(modeIndex, 0);
    }

    /**
     * Gets the parent mode that hosts a specific mode in nav drawer.
     *
     * @param modeIndex index of the mode
     * @param context current context
     * @return mode id if the index is valid, otherwise 0
     */
    public static int getCameraModeParentModeId(int modeIndex, Context context) {
        // Find the camera mode icon using id
        int[] cameraModeParent = context.getResources()
                .getIntArray(R.array.camera_mode_nested_in_nav_drawer);
        if (modeIndex < 0 || modeIndex >= cameraModeParent.length) {
            Log.e(TAG, "Invalid mode index: " + modeIndex);
            return 0;
        }
        return cameraModeParent[modeIndex];
    }

    /**
     * Gets the mode cover icon resource id of a specific mode.
     *
     * @param modeIndex index of the mode
     * @param context current context
     * @return icon resource id if the index is valid, otherwise 0
     */
    public static int getCameraModeCoverIconResId(int modeIndex, Context context) {
        // Find the camera mode icon using id
        TypedArray cameraModesIcons = context.getResources()
                .obtainTypedArray(R.array.camera_mode_cover_icon);
        if (modeIndex >= cameraModesIcons.length() || modeIndex < 0) {
            // Mode index not found
            Log.e(TAG, "Invalid mode index: " + modeIndex);
            return 0;
        }
        return cameraModesIcons.getResourceId(modeIndex, 0);
    }

    /**
     * Gets the number of cores available in this device, across all processors.
     * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
     * <p>
     * Source: http://stackoverflow.com/questions/7962155/
     *
     * @return The number of cores, or 1 if failed to get result
     */
    public static int getNumCpuCores() {
        // Private Class to display only CPU devices in the directory listing
        class CpuFilter implements java.io.FileFilter {
            @Override
            public boolean accept(java.io.File pathname) {
                // Check if filename is "cpu", followed by a single digit number
                if (java.util.regex.Pattern.matches("cpu[0-9]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            // Get directory containing CPU info
            java.io.File dir = new java.io.File("/sys/devices/system/cpu/");
            // Filter to only list the devices we care about
            java.io.File[] files = dir.listFiles(new CpuFilter());
            // Return the number of cores (virtual CPU devices)
            return files.length;
        } catch (Exception e) {
            // Default to return 1 core
            Log.e(TAG, "Failed to count number of cores, defaulting to 1", e);
            return 1;
        }
    }

    /**
     * Given the device orientation and Camera2 characteristics, this returns
     * the required JPEG rotation for this camera.
     *
     * @param deviceOrientationDegrees the clockwise angle of the device orientation from its
     *                                 natural orientation in degrees.
     * @return The angle to rotate image clockwise in degrees. It should be 0, 90, 180, or 270.
     */
    public static int getJpegRotation(int deviceOrientationDegrees,
                                      CameraCharacteristics characteristics) {
        if (deviceOrientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0;
        }
        boolean isFrontCamera = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraMetadata.LENS_FACING_FRONT;
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        return getImageRotation(sensorOrientation, deviceOrientationDegrees, isFrontCamera);
    }

    /**
     * Given the camera sensor orientation and device orientation, this returns a clockwise angle
     * which the final image needs to be rotated to be upright on the device screen.
     *
     * @param sensorOrientation Clockwise angle through which the output image needs to be rotated
     *                          to be upright on the device screen in its native orientation.
     * @param deviceOrientation Clockwise angle of the device orientation from its
     *                          native orientation when front camera faces user.
     * @param isFrontCamera True if the camera is front-facing.
     * @return The angle to rotate image clockwise in degrees. It should be 0, 90, 180, or 270.
     */
    public static int getImageRotation(int sensorOrientation,
                                       int deviceOrientation,
                                       boolean isFrontCamera) {
        // The sensor of front camera faces in the opposite direction from back camera.
        if (isFrontCamera) {
            deviceOrientation = (360 - deviceOrientation) % 360;
        }
        return (sensorOrientation + deviceOrientation) % 360;
    }
}
