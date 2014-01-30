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

package com.android.camera.settings;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility functions around camera settings.
 */
public class SettingsUtil {
    private static final String TAG = "SettingsUtil";

    /** Enable debug output. */
    private static final boolean DEBUG = false;

    private static final String SIZE_LARGE = "large";
    private static final String SIZE_MEDIUM = "medium";
    private static final String SIZE_SMALL = "small";

    /** The ideal "medium" picture size is 50% of "large". */
    private static final float MEDIUM_RELATIVE_PICTURE_SIZE = 0.5f;

    /** The ideal "small" picture size is 25% of "large". */
    private static final float SMALL_RELATIVE_PICTURE_SIZE = 0.25f;

    /**
     * Based on the selected size, this method selects the matching concrete
     * resolution and sets it as the picture size.
     *
     * @param sizeSetting The setting selected by the user. One of "large",
     *            "medium, "small".
     * @param supported The list of supported resolutions.
     * @param parameters The Camera parameters to set the selected picture
     *            resolution on.
     */
    public static void setCameraPictureSize(String sizeSetting, List<Size> supported,
            Parameters parameters) {
        Size selectedSize = getCameraPictureSize(sizeSetting, supported);
        Log.d(TAG, "Selected " + sizeSetting + " resolution: " + selectedSize.width + "x"
                + selectedSize.height);
        parameters.setPictureSize(selectedSize.width, selectedSize.height);
    }

    /**
     * Based on the selected size (large, medium or small), and the list of
     * supported resolutions, this method selects and returns the best matching
     * picture size.
     *
     * @param sizeSetting The setting selected by the user. One of "large",
     *            "medium, "small".
     * @param supported The list of supported resolutions.
     * @param parameters The Camera parameters to set the selected picture
     *            resolution on.
     * @return The selected size.
     */
    public static Size getCameraPictureSize(String sizeSetting, List<Size> supported) {
        // Sanitize the value to be either small, medium or large. Default to
        // the latter.
        if (!SIZE_SMALL.equals(sizeSetting) && !SIZE_MEDIUM.equals(sizeSetting)) {
            sizeSetting = SIZE_LARGE;
        }

        // Sort supported sizes by total pixel count, descending.
        Collections.sort(supported, new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                int leftArea = lhs.width * lhs.height;
                int rightArea = rhs.width * rhs.height;
                return rightArea - leftArea;
            }
        });
        if (DEBUG) {
            Log.d(TAG, "Supported Sizes:");
            for (Size size : supported) {
                Log.d(TAG, " --> " + size.width + "x" + size.height + "  "
                        + ((size.width * size.height) / 1000000f) + " - "
                        + (size.width / (float) size.height));
            }
        }

        // Large size is always the size with the most pixels reported.
        Size largeSize = supported.remove(0);
        if (SIZE_LARGE.equals(sizeSetting)) {
            return largeSize;
        }

        // If possible we want to find medium and small sizes with the same
        // aspect ratio as 'large'.
        final float targetAspectRatio = largeSize.width / (float) largeSize.height;

        // Create a list of sizes with the same aspect ratio as "large" which we
        // will search in primarily.
        ArrayList<Size> aspectRatioMatches = new ArrayList<Size>();
        for (Size size : supported) {
            float aspectRatio = size.width / (float) size.height;
            // Allow for small rounding errors in aspect ratio.
            if (Math.abs(aspectRatio - targetAspectRatio) < 0.01) {
                aspectRatioMatches.add(size);
            }
        }

        // If we have at least two more resolutions that match the 'large'
        // aspect ratio, use that list to find small and medium sizes. If not,
        // use the full list with any aspect ratio.
        final List<Size> searchList = (aspectRatioMatches.size() >= 2) ? aspectRatioMatches
                : supported;

        // Edge cases: If there are no further supported resolutions, use the
        // only one we have.
        // If there is only one remaining, use it for small and medium. If there
        // are two, use the two for small and medium.
        // These edge cases should never happen on a real device, but might
        // happen on test devices and emulators.
        if (searchList.isEmpty()) {
            Log.w(TAG, "Only one supported resolution.");
            return largeSize;
        } else if (searchList.size() == 1) {
            Log.w(TAG, "Only two supported resolutions.");
            return searchList.get(0);
        } else if (searchList.size() == 2) {
            int index = SIZE_MEDIUM.equals(sizeSetting) ? 0 : 1;
            return searchList.get(index);
        }

        // Based on the large pixel count, determine the target pixel count for
        // medium and small.
        final int largePixelCount = largeSize.width * largeSize.height;
        final int mediumTargetPixelCount = (int) (largePixelCount * MEDIUM_RELATIVE_PICTURE_SIZE);
        final int smallTargetPixelCount = (int) (largePixelCount * SMALL_RELATIVE_PICTURE_SIZE);

        int mediumSizeIndex = findClosestSize(searchList, mediumTargetPixelCount);
        int smallSizeIndex = findClosestSize(searchList, smallTargetPixelCount);

        // If the selected sizes are the same, move the small size one down or
        // the medium size one up.
        if (searchList.get(mediumSizeIndex).equals(searchList.get(smallSizeIndex))) {
            if (smallSizeIndex < (searchList.size() - 1)) {
                smallSizeIndex += 1;
            } else {
                mediumSizeIndex -= 1;
            }
        }
        int selectedSizeIndex = SIZE_MEDIUM.equals(sizeSetting) ? mediumSizeIndex : smallSizeIndex;
        return searchList.get(selectedSizeIndex);
    }

    /**
     * Returns the index of the size within the given list that is closest to
     * the given target pixel count.
     */
    private static int findClosestSize(List<Size> sortedSizes, int targetPixelCount) {
        int closestMatchIndex = 0;
        int closestMatchPixelCountDiff = Integer.MAX_VALUE;

        for (int i = 0; i < sortedSizes.size(); ++i) {
            Size size = sortedSizes.get(i);
            int pixelCountDiff = Math.abs((size.width * size.height) - targetPixelCount);
            if (pixelCountDiff < closestMatchPixelCountDiff) {
                closestMatchIndex = i;
                closestMatchPixelCountDiff = pixelCountDiff;
            }
        }
        return closestMatchIndex;
    }
}
