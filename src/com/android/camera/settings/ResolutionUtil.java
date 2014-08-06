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

import com.android.camera.util.ApiHelper;
import com.android.ex.camera2.portability.Size;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is used to help manage the many different resolutions available on
 * the device. <br/>
 * It allows you to specify which aspect ratios to offer the user, and then
 * chooses which resolutions are the most pertinent to avoid overloading the
 * user with so many options.
 */
public class ResolutionUtil {

    public static final String NEXUS_5_LARGE_16_BY_9 = "1836x3264";
    public static final float NEXUS_5_LARGE_16_BY_9_ASPECT_RATIO = 16f / 9f;
    public static Size NEXUS_5_LARGE_16_BY_9_SIZE = new Size(1836, 3264);

    /**
     * These are the preferred aspect ratios for the settings. We will take HAL
     * supported aspect ratios that are within RATIO_TOLERANCE of these values.
     * We will also take the maximum supported resolution for full sensor image.
     */
    private static Float[] sDesiredAspectRatios = {
            16.0f / 9.0f, 4.0f / 3.0f
    };

    private static Size[] sDesiredAspectRatioSizes = {
            new Size(16, 9), new Size(4, 3)
    };

    private static final float RATIO_TOLERANCE = .05f;

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
                if (Math.abs(aspectRatio.floatValue() - targetRatio) <= RATIO_TOLERANCE) {
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
     * @return the closest desiredAspectRatio within RATIO_TOLERANCE, or the
     *         original ratio
     */
    private static float fuzzAspectRatio(float aspectRatio) {
        for (float desiredAspectRatio : sDesiredAspectRatios) {
            if ((Math.abs(aspectRatio - desiredAspectRatio)) < RATIO_TOLERANCE) {
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
            Float aspectRatio = size.width() / (float) size.height();
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
            aspectRatio = new Size(sDesiredAspectRatioSizes[index]);
        }
        return aspectRatio;
    }

    /**
     * See {@link #getApproximateSize(Size)}.
     * <p>
     * TODO: Move this whole util to {@link android.util.Size}
     */
    public static com.android.camera.util.Size getApproximateSize(
            com.android.camera.util.Size size) {
        Size result = getApproximateSize(new Size(size.getWidth(), size.getHeight()));
        return new com.android.camera.util.Size(result.width(), result.height());
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

}
