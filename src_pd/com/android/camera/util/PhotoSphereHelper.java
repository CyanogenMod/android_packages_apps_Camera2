package com.android.camera.util;


import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.camera.CameraModule;
import com.android.camera.app.StitchingProgressManager;

public class PhotoSphereHelper {
    public static class PanoramaMetadata {
        // Whether a panorama viewer should be used
        public final boolean mUsePanoramaViewer;
        // Whether a panorama is 360 degrees
        public final boolean mIsPanorama360;

        public PanoramaMetadata(boolean usePanoramaViewer, boolean isPanorama360) {
            mUsePanoramaViewer = usePanoramaViewer;
            mIsPanorama360 = isPanorama360;
        }
    }

    public static class PanoramaViewHelper {

        public PanoramaViewHelper(Activity activity) {
            /* Do nothing */
        }

        public void onStart() {
            /* Do nothing */
        }

        public void onCreate() {
            /* Do nothing */
        }

        public void onStop() {
            /* Do nothing */
        }

        public void showPanorama(Uri uri) {
            /* Do nothing */
        }
    }

    public static final PanoramaMetadata NOT_PANORAMA = new PanoramaMetadata(false, false);

    public static void setupCaptureIntent(Context context, Intent it, String outputDir) {
        /* Do nothing */
    }

    public static boolean hasLightCycleCapture(Context context) {
        return false;
    }

    public static PanoramaMetadata getPanoramaMetadata(Context context, Uri uri) {
        return NOT_PANORAMA;
    }

    public static CameraModule createPanoramaModule() {
        return null;
    }

    public static StitchingProgressManager createStitchingManagerInstance(Context context) {
        return null;
    }

    /**
     * Get the file path from a Media storage URI.
     */
    public static String getPathFromURI(ContentResolver contentResolver, Uri contentUri) {
        return null;
    }

    /**
     * Get the modified time from a Media storage URI.
     */
    public static long getModifiedTimeFromURI(ContentResolver contentResolver, Uri contentUri) {
        return 0;
    }
}
