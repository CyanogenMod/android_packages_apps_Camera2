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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.CamcorderProfile;
import android.os.storage.StorageVolume;
import android.util.SparseArray;

import com.android.camera.debug.Log;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Callback;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraDeviceInfo;
import com.android.ex.camera2.portability.CameraSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility functions around camera settings.
 */
public class SettingsUtil {
    /**
     * Returns the maximum video recording duration (in milliseconds).
     */
    public static int getMaxVideoDuration(Context context) {
        int duration = 0; // in milliseconds, 0 means unlimited.
        try {
            duration = context.getResources().getInteger(R.integer.max_video_recording_length);
        } catch (Resources.NotFoundException ex) {
        }
        return duration;
    }

    /** The selected Camera sizes. */
    public static class SelectedPictureSizes {
        public Size large;
        public Size medium;
        public Size small;

        /**
         * This takes a string preference describing the desired resolution and
         * returns the camera size it represents. <br/>
         * It supports historical values of SIZE_LARGE, SIZE_MEDIUM, and
         * SIZE_SMALL as well as resolutions separated by an x i.e. "1024x576" <br/>
         * If it fails to parse the string, it will return the old SIZE_LARGE
         * value.
         *
         * @param sizeSetting the preference string to convert to a size
         * @param supportedSizes all possible camera sizes that are supported
         * @return the size that this setting represents
         */
        public Size getFromSetting(String sizeSetting, List<Size> supportedSizes) {
            if (SIZE_LARGE.equals(sizeSetting)) {
                return large;
            } else if (SIZE_MEDIUM.equals(sizeSetting)) {
                return medium;
            } else if (SIZE_SMALL.equals(sizeSetting)) {
                return small;
            } else if (sizeSetting != null && sizeSetting.split("x").length == 2) {
                Size desiredSize = sizeFromSettingString(sizeSetting);
                if (supportedSizes.contains(desiredSize)) {
                    return desiredSize;
                }
            }
            return large;
        }

        @Override
        public String toString() {
            return "SelectedPictureSizes: " + large + ", " + medium + ", " + small;
        }
    }

    /** The selected {@link CamcorderProfile} qualities. */
    public static class SelectedVideoQualities {
        public int large = -1;
        public int medium = -1;
        public int small = -1;

        public int getFromSetting(String sizeSetting) {
            // Sanitize the value to be either small, medium or large. Default
            // to the latter.
            if (!SIZE_SMALL.equals(sizeSetting) && !SIZE_MEDIUM.equals(sizeSetting)) {
                sizeSetting = SIZE_LARGE;
            }

            if (SIZE_LARGE.equals(sizeSetting)) {
                return large;
            } else if (SIZE_MEDIUM.equals(sizeSetting)) {
                return medium;
            } else {
                return small;
            }
        }
    }

    private static final Log.Tag TAG = new Log.Tag("SettingsUtil");

    /** Enable debug output. */
    private static final boolean DEBUG = false;

    private static final String SIZE_LARGE = "large";
    private static final String SIZE_MEDIUM = "medium";
    private static final String SIZE_SMALL = "small";

    /** The ideal "medium" picture size is 50% of "large". */
    private static final float MEDIUM_RELATIVE_PICTURE_SIZE = 0.5f;

    /** The ideal "small" picture size is 25% of "large". */
    private static final float SMALL_RELATIVE_PICTURE_SIZE = 0.25f;

    /** Video qualities sorted by size. */
    public static int[] sVideoQualities = new int[] {
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_CIF,
            CamcorderProfile.QUALITY_QVGA,
            CamcorderProfile.QUALITY_QCIF
    };

    public static SparseArray<SelectedPictureSizes> sCachedSelectedPictureSizes =
            new SparseArray<SelectedPictureSizes>(2);
    public static SparseArray<SelectedVideoQualities> sCachedSelectedVideoQualities =
            new SparseArray<SelectedVideoQualities>(2);

    /**
     * Based on the selected size, this method returns the matching concrete
     * resolution.
     *
     * @param sizeSetting The setting selected by the user. One of "large",
     *            "medium, "small".
     * @param supported The list of supported resolutions.
     * @param cameraId This is used for caching the results for finding the
     *            different sizes.
     */
    public static Size getPhotoSize(String sizeSetting, List<Size> supported, int cameraId) {
        if (ResolutionUtil.NEXUS_5_LARGE_16_BY_9.equals(sizeSetting)) {
            return ResolutionUtil.NEXUS_5_LARGE_16_BY_9_SIZE;
        }
        Size selectedSize = getCameraPictureSize(sizeSetting, supported, cameraId);
        return selectedSize;
    }

    /**
     * Based on the selected size (large, medium or small), and the list of
     * supported resolutions, this method selects and returns the best matching
     * picture size.
     *
     * @param sizeSetting The setting selected by the user. One of "large",
     *            "medium, "small".
     * @param supported The list of supported resolutions.
     * @param cameraId This is used for caching the results for finding the
     *            different sizes.
     * @return The selected size.
     */
    private static Size getCameraPictureSize(String sizeSetting, List<Size> supported,
            int cameraId) {
        return getSelectedCameraPictureSizes(supported, cameraId).getFromSetting(sizeSetting,
                supported);
    }

    /**
     * Based on the list of supported resolutions, this method selects the ones
     * that shall be selected for being 'large', 'medium' and 'small'.
     *
     * @return It's guaranteed that all three sizes are filled. If less than
     *         three sizes are supported, the selected sizes might contain
     *         duplicates.
     */
    static SelectedPictureSizes getSelectedCameraPictureSizes(List<Size> supported, int cameraId) {
        List<Size> supportedCopy = new LinkedList<Size>(supported);
        if (sCachedSelectedPictureSizes.get(cameraId) != null) {
            return sCachedSelectedPictureSizes.get(cameraId);
        }
        if (supportedCopy == null) {
            return null;
        }

        SelectedPictureSizes selectedSizes = new SelectedPictureSizes();

        // Sort supported sizes by total pixel count, descending.
        Collections.sort(supportedCopy, new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                int leftArea = lhs.width() * lhs.height();
                int rightArea = rhs.width() * rhs.height();
                return rightArea - leftArea;
            }
        });
        if (DEBUG) {
            Log.d(TAG, "Supported Sizes:");
            for (Size size : supportedCopy) {
                Log.d(TAG, " --> " + size.width() + "x" + size.height() + "  "
                        + ((size.width() * size.height()) / 1000000f) + " - "
                        + (size.width() / (float) size.height()));
            }
        }

        // Large size is always the size with the most pixels reported.
        selectedSizes.large = supportedCopy.remove(0);

        // If possible we want to find medium and small sizes with the same
        // aspect ratio as 'large'.
        final float targetAspectRatio = selectedSizes.large.width()
                / (float) selectedSizes.large.height();

        // Create a list of sizes with the same aspect ratio as "large" which we
        // will search in primarily.
        ArrayList<Size> aspectRatioMatches = new ArrayList<Size>();
        for (Size size : supportedCopy) {
            float aspectRatio = size.width() / (float) size.height();
            // Allow for small rounding errors in aspect ratio.
            if (Math.abs(aspectRatio - targetAspectRatio) < 0.01) {
                aspectRatioMatches.add(size);
            }
        }

        // If we have at least two more resolutions that match the 'large'
        // aspect ratio, use that list to find small and medium sizes. If not,
        // use the full list with any aspect ratio.
        final List<Size> searchList = (aspectRatioMatches.size() >= 2) ? aspectRatioMatches
                : supportedCopy;

        // Edge cases: If there are no further supported resolutions, use the
        // only one we have.
        // If there is only one remaining, use it for small and medium. If there
        // are two, use the two for small and medium.
        // These edge cases should never happen on a real device, but might
        // happen on test devices and emulators.
        if (searchList.isEmpty()) {
            Log.w(TAG, "Only one supported resolution.");
            selectedSizes.medium = selectedSizes.large;
            selectedSizes.small = selectedSizes.large;
        } else if (searchList.size() == 1) {
            Log.w(TAG, "Only two supported resolutions.");
            selectedSizes.medium = searchList.get(0);
            selectedSizes.small = searchList.get(0);
        } else if (searchList.size() == 2) {
            Log.w(TAG, "Exactly three supported resolutions.");
            selectedSizes.medium = searchList.get(0);
            selectedSizes.small = searchList.get(1);
        } else {

            // Based on the large pixel count, determine the target pixel count
            // for medium and small.
            final int largePixelCount = selectedSizes.large.width() * selectedSizes.large.height();
            final int mediumTargetPixelCount = (int) (largePixelCount * MEDIUM_RELATIVE_PICTURE_SIZE);
            final int smallTargetPixelCount = (int) (largePixelCount * SMALL_RELATIVE_PICTURE_SIZE);

            int mediumSizeIndex = findClosestSize(searchList, mediumTargetPixelCount);
            int smallSizeIndex = findClosestSize(searchList, smallTargetPixelCount);

            // If the selected sizes are the same, move the small size one down
            // or
            // the medium size one up.
            if (searchList.get(mediumSizeIndex).equals(searchList.get(smallSizeIndex))) {
                if (smallSizeIndex < (searchList.size() - 1)) {
                    smallSizeIndex += 1;
                } else {
                    mediumSizeIndex -= 1;
                }
            }
            selectedSizes.medium = searchList.get(mediumSizeIndex);
            selectedSizes.small = searchList.get(smallSizeIndex);
        }
        sCachedSelectedPictureSizes.put(cameraId, selectedSizes);
        return selectedSizes;
    }

    /**
     * Determines the video quality for large/medium/small for the given camera.
     * Returns the one matching the given setting. Defaults to 'large' of the
     * qualitySetting does not match either large. medium or small.
     *
     * @param qualitySetting One of 'large', 'medium', 'small'.
     * @param cameraId The ID of the camera for which to get the quality
     *            setting.
     * @return The CamcorderProfile quality setting.
     */
    public static int getVideoQuality(String qualitySetting, int cameraId) {
        return getSelectedVideoQualities(cameraId).getFromSetting(qualitySetting);
    }

    static SelectedVideoQualities getSelectedVideoQualities(int cameraId) {
        if (sCachedSelectedVideoQualities.get(cameraId) != null) {
            return sCachedSelectedVideoQualities.get(cameraId);
        }

        // Go through the sizes in descending order, see if they are supported,
        // and set large/medium/small accordingly.
        // If no quality is supported at all, the first call to
        // getNextSupportedQuality will throw an exception.
        // If only one quality is supported, then all three selected qualities
        // will be the same.
        int largeIndex = getNextSupportedVideoQualityIndex(cameraId, -1);
        int mediumIndex = getNextSupportedVideoQualityIndex(cameraId, largeIndex);
        int smallIndex = getNextSupportedVideoQualityIndex(cameraId, mediumIndex);

        SelectedVideoQualities selectedQualities = new SelectedVideoQualities();
        selectedQualities.large = sVideoQualities[largeIndex];
        selectedQualities.medium = sVideoQualities[mediumIndex];
        selectedQualities.small = sVideoQualities[smallIndex];
        sCachedSelectedVideoQualities.put(cameraId, selectedQualities);
        return selectedQualities;
    }

    /**
     * Starting from 'start' this method returns the next supported video
     * quality.
     */
    private static int getNextSupportedVideoQualityIndex(int cameraId, int start) {
        for (int i = start + 1; i < sVideoQualities.length; ++i) {
            if (isVideoQualitySupported(sVideoQualities[i])
                    && CamcorderProfile.hasProfile(cameraId, sVideoQualities[i])) {
                // We found a new supported quality.
                return i;
            }
        }

        // Failed to find another supported quality.
        if (start < 0 || start >= sVideoQualities.length) {
            // This means we couldn't find any supported quality.
            throw new IllegalArgumentException("Could not find supported video qualities.");
        }

        // We previously found a larger supported size. In this edge case, just
        // return the same index as the previous size.
        return start;
    }

    /**
     * @return Whether the given {@link CamcorderProfile} is supported on the
     *         current device/OS version.
     */
    private static boolean isVideoQualitySupported(int videoQuality) {
        // 4k is only supported on L or higher but some devices falsely report
        // to have support for it on K, see b/18172081.
        if (!ApiHelper.isLOrHigher() && videoQuality == CamcorderProfile.QUALITY_2160P) {
            return false;
        }
        return true;
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
            int pixelCountDiff = Math.abs((size.width() * size.height()) - targetPixelCount);
            if (pixelCountDiff < closestMatchPixelCountDiff) {
                closestMatchIndex = i;
                closestMatchPixelCountDiff = pixelCountDiff;
            }
        }
        return closestMatchIndex;
    }

    private static final String SIZE_SETTING_STRING_DIMENSION_DELIMITER = "x";

    /**
     * This is used to serialize a size to a string for storage in settings
     *
     * @param size The size to serialize.
     * @return the string to be saved in preferences
     */
    public static String sizeToSettingString(Size size) {
        return size.width() + SIZE_SETTING_STRING_DIMENSION_DELIMITER + size.height();
    }

    /**
     * This parses a setting string and returns the representative size.
     *
     * @param sizeSettingString The string that stored in settings to represent a size.
     * @return the represented Size.
     */
    public static Size sizeFromSettingString(String sizeSettingString) {
        if (sizeSettingString == null) {
            return null;
        }
        String[] parts = sizeSettingString.split(SIZE_SETTING_STRING_DIMENSION_DELIMITER);
        if (parts.length != 2) {
            return null;
        }

        try {
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            return new Size(width, height);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Updates an AlertDialog.Builder to explain what it means to enable
     * location on captures.
     */
    public static AlertDialog.Builder getFirstTimeLocationAlertBuilder(
            AlertDialog.Builder builder, Callback<Boolean> callback) {
        if (callback == null) {
            return null;
        }

        getLocationAlertBuilder(builder, callback)
                .setMessage(R.string.remember_location_prompt);

        return builder;
    }

    /**
     * Updates an AlertDialog.Builder for choosing whether to include location
     * on captures.
     */
    public static AlertDialog.Builder getLocationAlertBuilder(AlertDialog.Builder builder,
            final Callback<Boolean> callback) {
        if (callback == null) {
            return null;
        }

        builder.setTitle(R.string.remember_location_title)
                .setPositiveButton(R.string.remember_location_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int arg1) {
                                callback.onCallback(true);
                            }
                        })
                .setNegativeButton(R.string.remember_location_no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int arg1) {
                                callback.onCallback(false);
                            }
                        });

        return builder;
    }

    /**
     * Gets the first (lowest-indexed) camera matching the given criterion.
     *
     * @param facing Either {@link CAMERA_FACING_BACK}, {@link CAMERA_FACING_FRONT}, or some other
     *               implementation of {@link CameraDeviceSelector}.
     * @return The ID of the first camera matching the supplied criterion, or
     *         -1, if no camera meeting the specification was found.
     */
    public static int getCameraId(CameraDeviceInfo info, CameraDeviceSelector chooser) {
        if (info == null) {
            return -1;
        }
        int numCameras = info.getNumberOfCameras();
        for (int i = 0; i < numCameras; ++i) {
            CameraDeviceInfo.Characteristics props = info.getCharacteristics(i);
            if (props == null) {
                // Skip this device entry
                continue;
            }
            if (chooser.useCamera(props)) {
                return i;
            }
        }
        return -1;
    }

    private static List<StorageVolume> sMountedStorageVolumes;

    public static void setMountedStorageVolumes(List<StorageVolume> volumes) {
        sMountedStorageVolumes = volumes;
    }

    public static List<StorageVolume> getMountedStorageVolumes() {
        return sMountedStorageVolumes;
    }

    public static interface CameraDeviceSelector {
        /**
         * Given the static characteristics of a specific camera device, decide whether it is the
         * one we will use.
         *
         * @param info The static characteristics of a device.
         * @return Whether we're electing to use this particular device.
         */
        public boolean useCamera(CameraDeviceInfo.Characteristics info);
    }

    public static final CameraDeviceSelector CAMERA_FACING_BACK = new CameraDeviceSelector() {
        @Override
        public boolean useCamera(CameraDeviceInfo.Characteristics info) {
            return info.isFacingBack();
        }};

    public static final CameraDeviceSelector CAMERA_FACING_FRONT = new CameraDeviceSelector() {
        @Override
        public boolean useCamera(CameraDeviceInfo.Characteristics info) {
            return info.isFacingFront();
        }};
}
