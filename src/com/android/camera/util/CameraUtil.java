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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import com.android.camera.CameraActivity;
import com.android.camera.CameraDisabledException;
import com.android.camera.CameraHolder;
import com.android.camera.CameraManager;
import com.android.camera.CameraSettings;
import com.android.camera.util.IntentHelper;
import com.android.camera2.R;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * Collection of utility functions used in this package.
 */
public class CameraUtil {
    private static final String TAG = "Util";

    // For calculate the best fps range for still image capture.
    private final static int MAX_PREVIEW_FPS_TIMES_1000 = 400000;
    private final static int PREFERRED_PREVIEW_FPS_TIMES_1000 = 30000;

    // For creating crop intents.
    public static final String KEY_RETURN_DATA = "return-data";
    public static final String KEY_SHOW_WHEN_LOCKED = "showWhenLocked";

    // Orientation hysteresis amount used in rounding, in degrees
    public static final int ORIENTATION_HYSTERESIS = 5;

    public static final String REVIEW_ACTION = "com.android.camera.action.REVIEW";
    // See android.hardware.Camera.ACTION_NEW_PICTURE.
    public static final String ACTION_NEW_PICTURE = "android.hardware.action.NEW_PICTURE";
    // See android.hardware.Camera.ACTION_NEW_VIDEO.
    public static final String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";

    // Broadcast Action: The camera application has become active in picture-taking mode.
    public static final String ACTION_CAMERA_STARTED = "com.android.camera.action.CAMERA_STARTED";
    // Broadcast Action: The camera application is no longer in active picture-taking mode.
    public static final String ACTION_CAMERA_STOPPED = "com.android.camera.action.CAMERA_STOPPED";
    // When the camera application is active in picture-taking mode, it listens for this intent,
    // which upon receipt will trigger the shutter to capture a new picture, as if the user had
    // pressed the shutter button.
    public static final String ACTION_CAMERA_SHUTTER_CLICK =
            "com.android.camera.action.SHUTTER_CLICK";

    // Fields from android.hardware.Camera.Parameters
    public static final String FOCUS_MODE_CONTINUOUS_PICTURE = "continuous-picture";
    public static final String RECORDING_HINT = "recording-hint";
    private static final String AUTO_EXPOSURE_LOCK_SUPPORTED = "auto-exposure-lock-supported";
    private static final String AUTO_WHITE_BALANCE_LOCK_SUPPORTED = "auto-whitebalance-lock-supported";
    private static final String VIDEO_SNAPSHOT_SUPPORTED = "video-snapshot-supported";
    public static final String SCENE_MODE_HDR = "hdr";
    public static final String SCENE_MODE_ASD = "asd";
    public static final String TRUE = "true";
    public static final String FALSE = "false";

    // Hardware camera key mask
    private static final int KEY_MASK_CAMERA = 0x20;

    private static boolean sEnableZSL;

    // Do not change the focus mode when TTF is used
    private static boolean sNoFocusModeChangeForTouch;

    private static boolean sCancelAutoFocusOnPreviewStopped;

    // Fields for the show-on-maps-functionality
    private static final String MAPS_PACKAGE_NAME = "com.google.android.apps.maps";
    private static final String MAPS_CLASS_NAME = "com.google.android.maps.MapsActivity";

    /** Has to be in sync with the receiving MovieActivity. */
    public static final String KEY_TREAT_UP_AS_BACK = "treat-up-as-back";

    public static boolean isZSLEnabled() {
        return sEnableZSL;
    }

    public static boolean cancelAutoFocusOnPreviewStopped() {
        return sCancelAutoFocusOnPreviewStopped;
    }

    public static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    public static boolean isAutoExposureLockSupported(Parameters params) {
        return TRUE.equals(params.get(AUTO_EXPOSURE_LOCK_SUPPORTED));
    }

    public static boolean isAutoWhiteBalanceLockSupported(Parameters params) {
        return TRUE.equals(params.get(AUTO_WHITE_BALANCE_LOCK_SUPPORTED));
    }

    public static boolean isVideoSnapshotSupported(Parameters params) {
        return TRUE.equals(params.get(VIDEO_SNAPSHOT_SUPPORTED));
    }

    public static boolean isCameraHdrSupported(Parameters params) {
        List<String> supported = params.getSupportedSceneModes();
        return (supported != null) && supported.contains(SCENE_MODE_HDR);
    }

    public static boolean isAutoSceneDetectionSupported(Parameters params) {
        List<String> supported = params.getSupportedSceneModes();
        return (supported != null) && supported.contains(SCENE_MODE_ASD) && (params.get("asd-mode") != null);
    }

    public static boolean hasCameraKey() {
        return (sDeviceKeysPresent & KEY_MASK_CAMERA) != 0;
    }

    public static boolean isMeteringAreaSupported(Parameters params) {
        return params.getMaxNumMeteringAreas() > 0;
    }

    public static boolean isFocusAreaSupported(Parameters params) {
        return (params.getMaxNumFocusAreas() > 0
                && isSupported(Parameters.FOCUS_MODE_AUTO,
                        params.getSupportedFocusModes()));
    }

    public static boolean isSupported(Parameters params, String key) {
        return (params.get(key) != null && !"null".equals(params.get(key)));
    }

    public static int getNumSnapsPerShutter(Parameters params) {
        String numJpegs = params.get("num-jpegs-per-shutter");
        if (!TextUtils.isEmpty(numJpegs)) {
            return Integer.valueOf(numJpegs);
        }
        String numSnaps = params.get("num-snaps-per-shutter");
        if (!TextUtils.isEmpty(numSnaps)) {
            return Integer.valueOf(numSnaps);
        }
        return 1;
    }

    // Private intent extras. Test only.
    private static final String EXTRAS_CAMERA_FACING =
            "android.intent.extras.CAMERA_FACING";

    private static float sPixelDensity = 1;
    private static ImageFileNamer sImageFileNamer;
    // Use samsung HDR format
    private static boolean sSamsungHDRFormat;

    // Get available hardware keys
    private static int sDeviceKeysPresent;

    // Samsung camcorder mode
    private static boolean sSamsungCamMode;

    // HTC camcorder mode
    private static boolean sHTCCamMode;

    private CameraUtil() {
    }

    public static void initialize(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        sPixelDensity = metrics.density;
        sImageFileNamer = new ImageFileNamer(
                context.getString(R.string.image_file_name_format));
        sEnableZSL = context.getResources().getBoolean(R.bool.enableZSL);
        sNoFocusModeChangeForTouch = context.getResources().getBoolean(
                R.bool.useContinuosFocusForTouch);
        sCancelAutoFocusOnPreviewStopped =
                context.getResources().getBoolean(R.bool.cancelAutoFocusOnPreviewStopped);
        sSamsungHDRFormat = context.getResources().getBoolean(R.bool.needsSamsungHDRFormat);
        sDeviceKeysPresent = context.getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);
        sSamsungCamMode = context.getResources().getBoolean(R.bool.needsSamsungCamMode);
        sHTCCamMode = context.getResources().getBoolean(R.bool.needsHTCCamMode);
    }

    public static int dpToPixel(int dp) {
        return Math.round(sPixelDensity * dp);
    }

    public static boolean needSamsungHDRFormat() {
        return sSamsungHDRFormat;
    }

    public static boolean noFocusModeChangeForTouch() {
        return sNoFocusModeChangeForTouch;
    }

    public static boolean useHTCCamMode() {
        return sHTCCamMode;
    }

    public static boolean useSamsungCamMode() {
        return sSamsungCamMode;
    }

    // Rotates the bitmap by the specified degree.
    // If a new bitmap is created, the original bitmap is recycled.
    public static Bitmap rotate(Bitmap b, int degrees) {
        return rotateAndMirror(b, degrees, false);
    }

    // Rotates and/or mirrors the bitmap. If a new bitmap is created, the
    // original bitmap is recycled.
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

    /*
     * Compute the sample size as a function of minSideLength
     * and maxNumOfPixels.
     * minSideLength is used to specify that minimal width or height of a
     * bitmap.
     * maxNumOfPixels is used to specify the maximal size in pixels that is
     * tolerable in terms of memory usage.
     *
     * The function returns a sample size based on the constraints.
     * Both size and minSideLength can be passed in as -1
     * which indicates no care of the corresponding constraint.
     * The functions prefers returning a sample size that
     * generates a smaller bitmap, unless minSideLength = -1.
     *
     * Also, the function rounds up the sample size to a power of 2 or multiple
     * of 8 because BitmapFactory only honors sample size this way.
     * For example, BitmapFactory downsamples an image by 2 even though the
     * request is 3. So we round up the sample size to avoid OOM.
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

    public static Bitmap decodeYUV422P(byte[] yuv422p, int width, int height)
                        throws NullPointerException, IllegalArgumentException {
        final int frameSize = width * height;
        int[] rgb = new int[frameSize];
        for (int j = 0, yp = 0; j < height; j++) {
            int up = frameSize + (j * (width/2)), u = 0, v = 0;
            int vp = ((int)(frameSize*1.5) + (j*(width/2)));
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv422p[yp])) - 16;
                if (y < 0)
                    y = 0;
                if ((i & 1) == 0) {
                    u = (0xff & yuv422p[up++]) - 128;
                    v = (0xff & yuv422p[vp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0)
                    r = 0;
                else if (r > 262143)
                    r = 262143;
                if (g < 0)
                    g = 0;
                else if (g > 262143)
                    g = 262143;
                if (b < 0)
                    b = 0;
                else if (b > 262143)
                    b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888);
    }

    public static void closeSilently(Closeable c) {
        if (c == null) return;
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

    private static void throwIfCameraDisabled(Activity activity) throws CameraDisabledException {
        // Check if device policy has disabled the camera.
        DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        if (dpm.getCameraDisabled(null)) {
            throw new CameraDisabledException();
        }
    }

    public static CameraManager.CameraProxy openCamera(
            Activity activity, final int cameraId,
            Handler handler, final CameraManager.CameraOpenErrorCallback cb) {
        try {
            throwIfCameraDisabled(activity);
            return CameraHolder.instance().open(handler, cameraId, cb);
        } catch (CameraDisabledException ex) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onCameraDisabled(cameraId);
                }
            });
        }
        return null;
    }

    public static void showErrorAndFinish(final Activity activity, int msgId) {
        DialogInterface.OnClickListener buttonListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                activity.finish();
            }
        };
        TypedValue out = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.alertDialogIcon, out, true);
        new AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle(R.string.camera_error_title)
                .setMessage(msgId)
                .setNeutralButton(R.string.dialog_ok, buttonListener)
                .setIcon(out.resourceId)
                .show();
    }

    public static <T> T checkNotNull(T object) {
        if (object == null) throw new NullPointerException();
        return object;
    }

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a == null ? false : a.equals(b));
    }

    public static int nextPowerOf2(int n) {
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

    public static int clamp(int x, int min, int max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }

    public static float clamp(float x, float min, float max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }

    public static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
        }
        return 0;
    }

    public static boolean isScreenRotated(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        return rotation != Surface.ROTATION_0 && rotation != Surface.ROTATION_180;
    }

    /**
     * Calculate the default orientation of the device based on the width and
     * height of the display when rotation = 0 (i.e. natural width and height)
     * @param activity the activity context
     * @return whether the default orientation of the device is portrait
     */
    public static boolean isDefaultToPortrait(Activity activity) {
        Display currentDisplay = activity.getWindowManager().getDefaultDisplay();
        Point displaySize = new Point();
        currentDisplay.getSize(displaySize);
        int orientation = currentDisplay.getRotation();
        int naturalWidth, naturalHeight;
        if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
            naturalWidth = displaySize.x;
            naturalHeight = displaySize.y;
        } else {
            naturalWidth = displaySize.y;
            naturalHeight = displaySize.x;
        }
        return naturalWidth < naturalHeight;
    }

    public static int getDisplayOrientation(int degrees, int cameraId) {
        // See android.hardware.Camera.setDisplayOrientation for
        // documentation.
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    public static int getCameraOrientation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        return info.orientation;
    }

    public static int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation = false;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min( dist, 360 - dist );
            changeOrientation = ( dist >= 45 + ORIENTATION_HYSTERESIS );
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }

    private static Point getDefaultDisplaySize(Activity activity, Point size) {
        activity.getWindowManager().getDefaultDisplay().getSize(size);
        return size;
    }

    public static Size getOptimalPreviewSize(Activity currentActivity,
            List<Size> sizes, double targetRatio) {

        Point[] points = new Point[sizes.size()];

        int index = 0;
        for (Size s : sizes) {
            points[index++] = new Point(s.width, s.height);
        }

        int optimalPickIndex = getOptimalPreviewSize(currentActivity, points, targetRatio);
        return (optimalPickIndex == -1) ? null : sizes.get(optimalPickIndex);
    }

    public static int getOptimalPreviewSize(Activity currentActivity,
            Point[] sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.01;
        if (sizes == null) return -1;

        int optimalSizeIndex = -1;
        double minDiff = Double.MAX_VALUE;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of preview surface. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size.
        Point point = getDefaultDisplaySize(currentActivity, new Point());
        int targetHeight = Math.min(point.x, point.y);
        // Try to find an size match aspect ratio and size
        for (int i = 0; i < sizes.length; i++) {
            Point size = sizes[i];
            double ratio = (double) size.x / size.y;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.y - targetHeight) < minDiff) {
                optimalSizeIndex = i;
                minDiff = Math.abs(size.y - targetHeight);
            }
        }
        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSizeIndex == -1) {
            Log.w(TAG, "No preview size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            for (int i = 0; i < sizes.length; i++) {
                Point size = sizes[i];
                if (Math.abs(size.y - targetHeight) < minDiff) {
                    optimalSizeIndex = i;
                    minDiff = Math.abs(size.y - targetHeight);
                }
            }
        }
        return optimalSizeIndex;
    }

    // Returns the largest picture size which matches the given aspect ratio.
    public static Size getOptimalVideoSnapshotPictureSize(
            List<Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null) return null;

        Size optimalSize = null;

        // Try to find a size matches aspect ratio and has the largest width
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (optimalSize == null || size.width > optimalSize.width) {
                optimalSize = size;
            }
        }

        // Cannot find one that matches the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            Log.w(TAG, "No picture size match the aspect ratio");
            for (Size size : sizes) {
                if (optimalSize == null || size.width > optimalSize.width) {
                    optimalSize = size;
                }
            }
        }
        return optimalSize;
    }

    public static void dumpParameters(Parameters params) {
        Set<String> sortedParams = new TreeSet<String>();
        sortedParams.addAll(Arrays.asList(params.flatten().split(";")));
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        Iterator<String> i = sortedParams.iterator();
        while (i.hasNext()) {
            String nextParam = i.next();
            if ((sb.length() + nextParam.length()) > 2044) {
                Log.d(TAG, "Parameters: " + sb.toString());
                sb = new StringBuilder();
            }
            sb.append(nextParam);
            if (i.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append("]");
        Log.d(TAG, "Parameters: " + sb.toString());
    }

    /**
     * Returns whether the device is voice-capable (meaning, it can do MMS).
     */
    public static boolean isMmsCapable(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return false;
        }

        try {
            Class<?> partypes[] = new Class[0];
            Method sIsVoiceCapable = TelephonyManager.class.getMethod(
                    "isVoiceCapable", partypes);

            Object arglist[] = new Object[0];
            Object retobj = sIsVoiceCapable.invoke(telephonyManager, arglist);
            return (Boolean) retobj;
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // Failure, must be another device.
            // Assume that it is voice capable.
        } catch (IllegalAccessException iae) {
            // Failure, must be an other device.
            // Assume that it is voice capable.
        } catch (NoSuchMethodException nsme) {
        }
        return true;
    }

    // This is for test only. Allow the camera to launch the specific camera.
    public static int getCameraFacingIntentExtras(Activity currentActivity) {
        int cameraId = -1;

        int intentCameraId =
                currentActivity.getIntent().getIntExtra(CameraUtil.EXTRAS_CAMERA_FACING, -1);

        if (isFrontCameraIntent(intentCameraId)) {
            // Check if the front camera exist
            int frontCameraId = CameraHolder.instance().getFrontCameraId();
            if (frontCameraId != -1) {
                cameraId = frontCameraId;
            }
        } else if (isBackCameraIntent(intentCameraId)) {
            // Check if the back camera exist
            int backCameraId = CameraHolder.instance().getBackCameraId();
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
        if (uri == null) return false;

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

    public static void rectFToRect(RectF rectF, Rect rect) {
        rect.left = Math.round(rectF.left);
        rect.top = Math.round(rectF.top);
        rect.right = Math.round(rectF.right);
        rect.bottom = Math.round(rectF.bottom);
    }

    public static Rect rectFToRect(RectF rectF) {
        Rect rect = new Rect();
        rectFToRect(rectF, rect);
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

    public static void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                                     Rect previewRect) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);

        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // We need to map camera driver coordinates to preview rect coordinates
        Matrix mapping = new Matrix();
        mapping.setRectToRect(new RectF(-1000, -1000, 1000, 1000), rectToRectF(previewRect),
                Matrix.ScaleToFit.FILL);
        matrix.setConcat(mapping, matrix);
    }

    public static String createJpegName(long dateTaken) {
        synchronized (sImageFileNamer) {
            return sImageFileNamer.generateName(dateTaken);
        }
    }

    public static void broadcastNewPicture(Context context, Uri uri) {
        context.sendBroadcast(new Intent(ACTION_NEW_PICTURE, uri));
        // Keep compatibility
        context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));
    }

    public static void fadeIn(View view, float startAlpha, float endAlpha, long duration) {
        if (view.getVisibility() == View.VISIBLE) return;

        view.setVisibility(View.VISIBLE);
        Animation animation = new AlphaAnimation(startAlpha, endAlpha);
        animation.setDuration(duration);
        view.startAnimation(animation);
    }

    public static void fadeIn(View view) {
        fadeIn(view, 0F, 1F, 400);

        // We disabled the button in fadeOut(), so enable it here.
        view.setEnabled(true);
    }

    public static void fadeOut(View view) {
        if (view.getVisibility() != View.VISIBLE) return;

        // Since the button is still clickable before fade-out animation
        // ends, we disable the button first to block click.
        view.setEnabled(false);
        Animation animation = new AlphaAnimation(1F, 0F);
        animation.setDuration(400);
        view.startAnimation(animation);
        view.setVisibility(View.GONE);
    }

    public static int getJpegRotation(int cameraId, int orientation) {
        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        int rotation = 0;
        if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[cameraId];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - orientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + orientation) % 360;
            }
        }
        return rotation;
    }

    /**
     * Down-samples a jpeg byte array.
     * @param data a byte array of jpeg data
     * @param downSampleFactor down-sample factor
     * @return decoded and down-sampled bitmap
     */
    public static Bitmap downSample(final byte[] data, int downSampleFactor) {
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        // Downsample the image
        opts.inSampleSize = downSampleFactor;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    public static void setGpsParameters(Parameters parameters, Location loc) {
        // Clear previous GPS location from the parameters.
        parameters.removeGpsData();

        // We always encode GpsTimeStamp
        parameters.setGpsTimestamp(System.currentTimeMillis() / 1000);

        // Set GPS location.
        if (loc != null) {
            double lat = loc.getLatitude();
            double lon = loc.getLongitude();
            boolean hasLatLon = (lat != 0.0d) || (lon != 0.0d);

            if (hasLatLon) {
                Log.d(TAG, "Set gps location");
                parameters.setGpsLatitude(lat);
                parameters.setGpsLongitude(lon);
                parameters.setGpsProcessingMethod(loc.getProvider().toUpperCase());
                if (loc.hasAltitude()) {
                    parameters.setGpsAltitude(loc.getAltitude());
                } else {
                    // for NETWORK_PROVIDER location provider, we may have
                    // no altitude information, but the driver needs it, so
                    // we fake one.
                    parameters.setGpsAltitude(0);
                }
                if (loc.getTime() != 0) {
                    // Location.getTime() is UTC in milliseconds.
                    // gps-timestamp is UTC in seconds.
                    long utcTimeSeconds = loc.getTime() / 1000;
                    parameters.setGpsTimestamp(utcTimeSeconds);
                }
            } else {
                loc = null;
            }
        }
    }
   public static String getFilpModeString(int value){
        switch(value){
            case 0:
                return CameraSettings.FLIP_MODE_OFF;
            case 1:
                return CameraSettings.FLIP_MODE_H;
            case 2:
                return CameraSettings.FLIP_MODE_V;
            case 3:
                return CameraSettings.FLIP_MODE_VH;
            default:
                return null;
        }
    }
    /**
     * For still image capture, we need to get the right fps range such that the
     * camera can slow down the framerate to allow for less-noisy/dark
     * viewfinder output in dark conditions.
     *
     * @param params Camera's parameters.
     * @return null if no appropiate fps range can't be found. Otherwise, return
     *         the right range.
     */
    public static int[] getPhotoPreviewFpsRange(Parameters params) {
        return getPhotoPreviewFpsRange(params.getSupportedPreviewFpsRange());
    }

    public static int[] getPhotoPreviewFpsRange(List<int[]> frameRates) {
        if (frameRates.size() == 0) {
            Log.e(TAG, "No suppoted frame rates returned!");
            return null;
        }

        // Find the lowest min rate in supported ranges who can cover 30fps.
        int lowestMinRate = MAX_PREVIEW_FPS_TIMES_1000;
        for (int[] rate : frameRates) {
            int minFps = rate[Parameters.PREVIEW_FPS_MIN_INDEX];
            int maxFps = rate[Parameters.PREVIEW_FPS_MAX_INDEX];
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
            int minFps = rate[Parameters.PREVIEW_FPS_MIN_INDEX];
            int maxFps = rate[Parameters.PREVIEW_FPS_MAX_INDEX];
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

    public static int[] getMaxPreviewFpsRange(Parameters params) {
        List<int[]> frameRates = params.getSupportedPreviewFpsRange();
        if (frameRates != null && frameRates.size() > 0) {
            // The list is sorted. Return the last element.
            return frameRates.get(frameRates.size() - 1);
        }
        return new int[0];
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

    public static void playVideo(Activity activity, Uri uri, String title) {
        try {
            boolean isSecureCamera = ((CameraActivity)activity).isSecureCamera();
            UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                    UsageStatistics.ACTION_PLAY_VIDEO, null);
            if (!isSecureCamera) {
                Intent intent = IntentHelper.getVideoPlayerIntent(activity, uri)
                        .putExtra(Intent.EXTRA_TITLE, title)
                        .putExtra(KEY_TREAT_UP_AS_BACK, true);
                activity.startActivityForResult(intent, CameraActivity.REQ_CODE_DONT_SWITCH_TO_PREVIEW);
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
            activity.startActivityForResult(mapsIntent,
                    CameraActivity.REQ_CODE_DONT_SWITCH_TO_PREVIEW);
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
     * @return A {@link java.lang.String} of all the output with newline
     * between each.
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
}
