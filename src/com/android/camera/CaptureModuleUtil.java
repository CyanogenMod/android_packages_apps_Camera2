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

package com.android.camera;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Surface;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.Size;

import java.util.ArrayList;

/**
 * Common utility methods used in capture modules.
 */
public class CaptureModuleUtil {
    private static final Tag TAG = new Tag("CaptureModuleUtil");

    public static int getDeviceNaturalOrientation(Context context) {
        Configuration config = context.getResources().getConfiguration();
        int rotation = CameraUtil.getDisplayRotation();

        if (((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
                config.orientation == Configuration.ORIENTATION_LANDSCAPE) ||
                ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&
                config.orientation == Configuration.ORIENTATION_PORTRAIT)) {
            return Configuration.ORIENTATION_LANDSCAPE;
        } else {
            return Configuration.ORIENTATION_PORTRAIT;
        }
    }

    /**
     * Equivalent to the
     * {@link CameraUtil#getOptimalPreviewSize(java.util.List, double)}
     * method for the camera1 api.
     */
    public static Size getOptimalPreviewSize(Size[] sizes,double targetRatio) {
        return getOptimalPreviewSize(sizes, targetRatio, null);
    }

    /**
     * Returns the best preview size based on the current display resolution,
     * the available preview sizes, the target aspect ratio (typically the
     * aspect ratio of the picture to be taken) as well as a maximum allowed
     * tolerance. If tolerance is 'null', a default tolerance will be used.
     */
    public static Size getOptimalPreviewSize(Size[] sizes,
            double targetRatio, Double aspectRatioTolerance) {
        // TODO(andyhuibers): Don't hardcode this but use device's measurements.
        final int MAX_ASPECT_HEIGHT = 1080;

        // Count sizes with height <= 1080p to mimic camera1 api behavior.
        int count = 0;
        for (Size s : sizes) {
            if (s.getHeight() <= MAX_ASPECT_HEIGHT) {
                count++;
            }
        }
        ArrayList<Size> camera1Sizes = new ArrayList<Size>(count);

        // Set array of all sizes with height <= 1080p
        for (Size s : sizes) {
            if (s.getHeight() <= MAX_ASPECT_HEIGHT) {
                camera1Sizes.add(new Size(s.getWidth(), s.getHeight()));
            }
        }

        int optimalIndex = CameraUtil
                .getOptimalPreviewSizeIndex(camera1Sizes, targetRatio,
                        aspectRatioTolerance);

        if (optimalIndex == -1) {
            return null;
        }

        Size optimal = camera1Sizes.get(optimalIndex);
        for (Size s : sizes) {
            if (s.getWidth() == optimal.getWidth() && s.getHeight() == optimal.getHeight()) {
                return s;
            }
        }
        return null;
    }

    /**
     * Selects the preview buffer dimensions that are closest in size to the
     * size of the view containing the preview.
     */
    public static Size pickBufferDimensions(Size[] supportedPreviewSizes,
            double bestPreviewAspectRatio,
            Context context) {
        // Swap dimensions if the device is not in its natural orientation.
        boolean swapDimens = (CameraUtil.getDisplayRotation() % 180) == 90;
        // Swap dimensions if the device's natural orientation doesn't match
        // the sensor orientation.
        if (CaptureModuleUtil.getDeviceNaturalOrientation(context)
                == Configuration.ORIENTATION_PORTRAIT) {
            swapDimens = !swapDimens;
        }
        double bestAspect = bestPreviewAspectRatio;
        if (swapDimens) {
            bestAspect = 1 / bestAspect;
        }

        Size pick = CaptureModuleUtil.getOptimalPreviewSize(supportedPreviewSizes,
                bestPreviewAspectRatio, null);
        Log.d(TAG, "Picked buffer size: " + pick.toString());
        return pick;
    }
}
