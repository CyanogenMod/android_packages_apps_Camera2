/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.android.camera.exif.Rational;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Size;

import com.google.common.collect.Lists;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;


/**
 * This class is used to help manage the many different resolutions available on
 * the device. <br/>
 * It allows you to specify which aspect ratios to offer the user, and then
 * chooses which resolutions are the most pertinent to avoid overloading the
 * user with so many options.
 */
public class ResolutionUtil {
    /**
     * Different aspect ratio constants.
     */
    public static final Rational ASPECT_RATIO_16x9 = new Rational(16, 9);
    public static final Rational ASPECT_RATIO_4x3 = new Rational(4, 3);
    private static final double ASPECT_RATIO_TOLERANCE = 0.05;

    public static final String NEXUS_5_LARGE_16_BY_9 = "1836x3264";
    public static final float NEXUS_5_LARGE_16_BY_9_ASPECT_RATIO = 16f / 9f;
    public static Size NEXUS_5_LARGE_16_BY_9_SIZE = new Size(3264, 1836);

    /**
     * These are the preferred aspect ratios for the settings. We will take HAL
     * supported aspect ratios that are within ASPECT_RATIO_TOLERANCE of these values.
     * We will also take the maximum supported resolution for full sensor image.
     */
    private static Float[] sDesiredAspectRatios = {
            16.0f / 9.0f, 4.0f / 3.0f
    };

    private static Size[] sDesiredAspectRatioSizes = {
            new Size(16, 9), new Size(4, 3)
    };

    /**
     * A resolution bucket holds a list of sizes that are of a given aspect
     * ratio.
     */
    private static class ResolutionBucket {
        public Float aspectRatio;
        /**
         * This is a sorted list of sizes, going from largest to smallest.
         */
        public List<Size> sizes = new LinkedList<Size>();
        /**
         * This is the head of the sizes array.
         */
        public Size largest;
        /**
         * This is the area of the largest size, used for sorting
         * ResolutionBuckets.
         */
        public Integer maxPixels = 0;

        /**
         * Use this to add a new resolution to this bucket. It will insert it
         * into the sizes array and update appropriate members.
         *
         * @param size the new size to be added
         */
        public void add(Size size) {
            sizes.add(size);
            Collections.sort(sizes, new Comparator<Size>() {
                @Override
                public int compare(Size size, Size size2) {
                    // sort area greatest to least
                    return Integer.compare(size2.width() * size2.height(),
                            size.width() * size.height());
                }
            });
            maxPixels = sizes.get(0).width() * sizes.get(0).height();
        }
    }

    /**
     * Given a list of camera sizes, this uses some heuristics to decide which
     * options to present to a user. It currently returns up to 3 sizes for each
     * aspect ratio. The aspect ratios returned include the ones in
     * sDesiredAspectRatios, and the largest full sensor ratio. T his guarantees
     * that users can use a full-sensor size, as well as any of the preferred
     * aspect ratios from above;
     *
     * @param sizes A super set of all sizes to be displayed
     * @param isBackCamera true if these are sizes for the back camera
     * @return The list of sizes to display grouped first by aspect ratio
     *         (sorted by maximum area), and sorted within aspect ratio by area)
     */
    public static List<Size> getDisplayableSizesFromSupported(List<Size> sizes, boolean isBackCamera) {
        List<ResolutionBucket> buckets = parseAvailableSizes(sizes, isBackCamera);

        List<Float> sortedDesiredAspectRatios = new ArrayList<Float>();
        // We want to make sure we support the maximum pixel aspect ratio, even
        // if it doesn't match a desired aspect ratio
        sortedDesiredAspectRatios.add(buckets.get(0).aspectRatio.floatValue());

        // Now go through the buckets from largest mp to smallest, adding
        // desired ratios
        for (ResolutionBucket bucket : buckets) {
            Float aspectRatio = bucket.aspectRatio;
            if (Arrays.asList(sDesiredAspectRatios).contains(aspectRatio)
                    && !sortedDesiredAspectRatios.contains(aspectRatio)) {
                sortedDesiredAspectRatios.add(aspectRatio);
            }
        }

        List<Size> result = new ArrayList<Size>(sizes.size());
        for (Float targetRatio : sortedDesiredAspectRatios) {
            for (ResolutionBucket bucket : buckets) {
                Number aspectRatio = bucket.aspectRatio;
                if (Math.abs(aspectRatio.floatValue() - targetRatio) <= ASPECT_RATIO_TOLERANCE) {
                    result.addAll(pickUpToThree(bucket.sizes));
                }
            }
        }
        return result;
    }

    /**
     * Get the area in pixels of a size.
     *
     * @param size the size to measure
     * @return the area.
     */
    private static int area(Size size) {
        if (size == null) {
            return 0;
        }
        return size.width() * size.height();
    }

    /**
     * Given a list of sizes of a similar aspect ratio, it tries to pick evenly
     * spaced out options. It starts with the largest, then tries to find one at
     * 50% of the last chosen size for the subsequent size.
     *
     * @param sizes A list of Sizes that are all of a similar aspect ratio
     * @return A list of at least one, and no more than three representative
     *         sizes from the list.
     */
    private static List<Size> pickUpToThree(List<Size> sizes) {
        List<Size> result = new ArrayList<Size>();
        Size largest = sizes.get(0);
        result.add(largest);
        Size lastSize = largest;
        for (Size size : sizes) {
            double targetArea = Math.pow(.5, result.size()) * area(largest);
            if (area(size) < targetArea) {
                // This candidate is smaller than half the mega pixels of the
                // last one. Let's see whether the previous size, or this size
                // is closer to the desired target.
                if (!result.contains(lastSize)
                        && (targetArea - area(lastSize) < area(size) - targetArea)) {
                    result.add(lastSize);
                } else {
                    result.add(size);
                }
            }
            lastSize = size;
            if (result.size() == 3) {
                break;
            }
        }

        // If we have less than three, we can add the smallest size.
        if (result.size() < 3 && !result.contains(lastSize)) {
            result.add(lastSize);
        }
        return result;
    }

    /**
     * Take an aspect ratio and squish it into a nearby desired aspect ratio, if
     * possible.
     *
     * @param aspectRatio the aspect ratio to fuzz
     * @return the closest desiredAspectRatio within ASPECT_RATIO_TOLERANCE, or the
     *         original ratio
     */
    private static float fuzzAspectRatio(float aspectRatio) {
        for (float desiredAspectRatio : sDesiredAspectRatios) {
            if ((Math.abs(aspectRatio - desiredAspectRatio)) < ASPECT_RATIO_TOLERANCE) {
                return desiredAspectRatio;
            }
        }
        return aspectRatio;
    }

    /**
     * This takes a bunch of supported sizes and buckets them by aspect ratio.
     * The result is a list of buckets sorted by each bucket's largest area.
     * They are sorted from largest to smallest. This will bucket aspect ratios
     * that are close to the sDesiredAspectRatios in to the same bucket.
     *
     * @param sizes all supported sizes for a camera
     * @param isBackCamera true if these are sizes for the back camera
     * @return all of the sizes grouped by their closest aspect ratio
     */
    private static List<ResolutionBucket> parseAvailableSizes(List<Size> sizes, boolean isBackCamera) {
        HashMap<Float, ResolutionBucket> aspectRatioToBuckets = new HashMap<Float, ResolutionBucket>();

        for (Size size : sizes) {
            Float aspectRatio = (float) size.getWidth() / (float) size.getHeight();
            // If this aspect ratio is close to a desired Aspect Ratio,
            // fuzz it so that they are bucketed together
            aspectRatio = fuzzAspectRatio(aspectRatio);
            ResolutionBucket bucket = aspectRatioToBuckets.get(aspectRatio);
            if (bucket == null) {
                bucket = new ResolutionBucket();
                bucket.aspectRatio = aspectRatio;
                aspectRatioToBuckets.put(aspectRatio, bucket);
            }
            bucket.add(size);
        }
        if (ApiHelper.IS_NEXUS_5 && isBackCamera) {
            aspectRatioToBuckets.get(16 / 9.0f).add(NEXUS_5_LARGE_16_BY_9_SIZE);
        }
        List<ResolutionBucket> sortedBuckets = new ArrayList<ResolutionBucket>(
                aspectRatioToBuckets.values());
        Collections.sort(sortedBuckets, new Comparator<ResolutionBucket>() {
            @Override
            public int compare(ResolutionBucket resolutionBucket, ResolutionBucket resolutionBucket2) {
                return Integer.compare(resolutionBucket2.maxPixels, resolutionBucket.maxPixels);
            }
        });
        return sortedBuckets;
    }

    /**
     * Given a size, return a string describing the aspect ratio by reducing the
     *
     * @param size the size to describe
     * @return a string description of the aspect ratio
     */
    public static String aspectRatioDescription(Size size) {
        Size aspectRatio = reduce(size);
        return aspectRatio.width() + "x" + aspectRatio.height();
    }

    /**
     * Reduce an aspect ratio to its lowest common denominator. The ratio of the
     * input and output sizes is guaranteed to be the same.
     *
     * @param aspectRatio the aspect ratio to reduce
     * @return The reduced aspect ratio which may equal the original.
     */
    public static Size reduce(Size aspectRatio) {
        BigInteger width = BigInteger.valueOf(aspectRatio.width());
        BigInteger height = BigInteger.valueOf(aspectRatio.height());
        BigInteger gcd = width.gcd(height);
        int numerator = Math.max(width.intValue(), height.intValue()) / gcd.intValue();
        int denominator = Math.min(width.intValue(), height.intValue()) / gcd.intValue();
        return new Size(numerator, denominator);
    }

    /**
     * Given a size return the numerator of its aspect ratio
     *
     * @param size the size to measure
     * @return the numerator
     */
    public static int aspectRatioNumerator(Size size) {
        Size aspectRatio = reduce(size);
        return aspectRatio.width();
    }

    /**
     * Given a size, return the closest aspect ratio that falls close to the
     * given size.
     *
     * @param size the size to approximate
     * @return the closest desired aspect ratio, or the original aspect ratio if
     *         none were close enough
     */
    public static Size getApproximateSize(Size size) {
        Size aspectRatio = reduce(size);
        float fuzzy = fuzzAspectRatio(size.width() / (float) size.height());
        int index = Arrays.asList(sDesiredAspectRatios).indexOf(fuzzy);
        if (index != -1) {
            aspectRatio = sDesiredAspectRatioSizes[index];
        }
        return aspectRatio;
    }

    /**
     * Given a size return the numerator of its aspect ratio
     *
     * @param size
     * @return the denominator
     */
    public static int aspectRatioDenominator(Size size) {
        BigInteger width = BigInteger.valueOf(size.width());
        BigInteger height = BigInteger.valueOf(size.height());
        BigInteger gcd = width.gcd(height);
        int denominator = Math.min(width.intValue(), height.intValue()) / gcd.intValue();
        return denominator;
    }

    /**
     * Returns the aspect ratio for the given size.
     *
     * @param size The given size.
     * @return A {@link Rational} which represents the aspect ratio.
     */
    public static Rational getAspectRatio(Size size) {
        int width = size.getWidth();
        int height = size.getHeight();
        int numerator = width;
        int denominator = height;
        if (height > width) {
            numerator = height;
            denominator = width;
        }
        return new Rational(numerator, denominator);
    }

    public static boolean hasSameAspectRatio(Rational ar1, Rational ar2) {
        return Math.abs(ar1.toDouble() - ar2.toDouble()) < ASPECT_RATIO_TOLERANCE;
    }

    /**
     * Selects the maximal resolution for the given desired aspect ratio from all available
     * resolutions.  If no resolution exists for the desired aspect ratio, return a resolution
     * with the maximum number of pixels.
     *
     * @param desiredAspectRatio The desired aspect ratio.
     * @param sizes All available resolutions.
     * @return The maximal resolution for desired aspect ratio ; if no sizes are found, then
     *      return size of (0,0)
     */
    public static Size getLargestPictureSize(Rational desiredAspectRatio, List<Size> sizes) {
        int maxPixelNumNoAspect = 0;
        Size maxSize = new Size(0, 0);

        // Fix for b/21758681
        // Do first pass with the candidate with closest size, regardless of aspect ratio,
        // to loosen the requirement of valid preview sizes.  As long as one size exists
        // in the list, we should pass back a valid size.
        for (Size size : sizes) {
            int pixelNum = size.getWidth() * size.getHeight();
            if (pixelNum > maxPixelNumNoAspect) {
                maxPixelNumNoAspect = pixelNum;
                maxSize = size;
            }
        }

        // With second pass, override first pass with the candidate with closest
        // size AND similar aspect ratio.  If there are no valid candidates are found
        // in the second pass, take the candidate from the first pass.
        int maxPixelNumWithAspect = 0;
        for (Size size : sizes) {
            Rational aspectRatio = getAspectRatio(size);
            // Skip if the aspect ratio is not desired.
            if (!hasSameAspectRatio(aspectRatio, desiredAspectRatio)) {
                continue;
            }
            int pixelNum = size.getWidth() * size.getHeight();
            if (pixelNum > maxPixelNumWithAspect) {
                maxPixelNumWithAspect = pixelNum;
                maxSize = size;
            }
        }

        return maxSize;
    }

    public static DisplayMetrics getDisplayMetrics(Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager wm = AndroidServices.instance().provideWindowManager();
        if (wm != null) {
            wm.getDefaultDisplay().getMetrics(displayMetrics);
        }
        return displayMetrics;
    }

    /**
     * Takes selected sizes and a list of blacklisted sizes. All the blacklistes
     * sizes will be removed from the 'sizes' list.
     *
     * @param sizes the sizes to be filtered.
     * @param blacklistString a String containing a comma-separated list of
     *            sizes that should be removed from the original list.
     * @return A list that contains the filtered items.
     */
    @ParametersAreNonnullByDefault
    public static List<Size> filterBlackListedSizes(List<Size> sizes, String blacklistString) {
        String[] blacklistStringArray = blacklistString.split(",");
        if (blacklistStringArray.length == 0) {
            return sizes;
        }

        Set<String> blacklistedSizes = new HashSet(Lists.newArrayList(blacklistStringArray));
        List<Size> newSizeList = new ArrayList<>();
        for (Size size : sizes) {
            if (!isBlackListed(size, blacklistedSizes)) {
                newSizeList.add(size);
            }
        }
        return newSizeList;
    }

    /**
     * Returns whether the given size is within the blacklist string.
     *
     * @param size the size to check
     * @param blacklistString a String containing a comma-separated list of
     *            sizes that should not be available on the device.
     * @return Whether the given size is blacklisted.
     */
    public static boolean isBlackListed(@Nonnull Size size, @Nonnull String blacklistString) {
        String[] blacklistStringArray = blacklistString.split(",");
        if (blacklistStringArray.length == 0) {
            return false;
        }
        Set<String> blacklistedSizes = new HashSet(Lists.newArrayList(blacklistStringArray));
        return isBlackListed(size, blacklistedSizes);
    }

    private static boolean isBlackListed(@Nonnull Size size, @Nonnull Set<String> blacklistedSizes) {
        String sizeStr = size.getWidth() + "x" + size.getHeight();
        return blacklistedSizes.contains(sizeStr);
    }
}
