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

package com.android.gallery3d.filtershow.pipeline;

import android.graphics.Bitmap;
import android.util.Log;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;

import java.util.Vector;

public class CacheProcessing {
    private static final String LOGTAG = "CacheProcessing";
    private static final boolean DEBUG = false;
    private Vector<CacheStep> mSteps = new Vector<CacheStep>();

    static class CacheStep {
        FilterRepresentation representation;
        Bitmap cache;
    }

    public Bitmap process(Bitmap originalBitmap,
                          Vector<FilterRepresentation> filters,
                          FilterEnvironment environment) {

        if (filters.size() == 0) {
            return originalBitmap;
        }

        // New set of filters, let's clear the cache and rebuild it.
        if (filters.size() != mSteps.size()) {
            mSteps.clear();
            for (int i = 0; i < filters.size(); i++) {
                FilterRepresentation representation = filters.elementAt(i);
                CacheStep step = new CacheStep();
                step.representation = representation.copy();
                mSteps.add(step);
            }
        }

        if (DEBUG) {
            displayFilters(filters);
        }

        // First, let's find how similar we are in our cache
        // compared to the current list of filters
        int similarUpToIndex = -1;
        for (int i = 0; i < filters.size(); i++) {
            FilterRepresentation representation = filters.elementAt(i);
            CacheStep step = mSteps.elementAt(i);
            boolean similar = step.representation.equals(representation);
            if (similar) {
                similarUpToIndex = i;
            } else {
                break;
            }
        }
        if (DEBUG) {
            Log.v(LOGTAG, "similar up to index " + similarUpToIndex);
        }

        // Now, let's get the earliest cached result in our pipeline
        Bitmap cacheBitmap = null;
        int findBaseImageIndex = similarUpToIndex;
        if (findBaseImageIndex > -1) {
            while (findBaseImageIndex > 0
                    && mSteps.elementAt(findBaseImageIndex).cache == null) {
                findBaseImageIndex--;
            }
            cacheBitmap = mSteps.elementAt(findBaseImageIndex).cache;
        }
        boolean emptyStack = false;
        if (cacheBitmap == null) {
            emptyStack = true;
            // Damn, it's an empty stack, we have to start from scratch
            // TODO: use a bitmap cache + RS allocation instead of Bitmap.copy()
            cacheBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (findBaseImageIndex > -1) {
                FilterRepresentation representation = filters.elementAt(findBaseImageIndex);
                if (representation.getFilterType() != FilterRepresentation.TYPE_GEOMETRY) {
                    cacheBitmap = environment.applyRepresentation(representation, cacheBitmap);
                }
                mSteps.elementAt(findBaseImageIndex).representation = representation.copy();
                mSteps.elementAt(findBaseImageIndex).cache = cacheBitmap;
            }
            if (DEBUG) {
                Log.v(LOGTAG, "empty stack");
            }
        }

        // Ok, so sadly the earliest cached result is before the index we want.
        // We have to rebuild a new result for this position, and then cache it.
        if (findBaseImageIndex != similarUpToIndex) {
            if (DEBUG) {
                Log.v(LOGTAG, "rebuild cacheBitmap from " + findBaseImageIndex
                        + " to " + similarUpToIndex);
            }
            // rebuild the cache image for this step
            if (!emptyStack) {
                cacheBitmap = cacheBitmap.copy(Bitmap.Config.ARGB_8888, true);
            } else {
                // if it was an empty stack, we already applied it
                findBaseImageIndex ++;
            }
            for (int i = findBaseImageIndex; i <= similarUpToIndex; i++) {
                FilterRepresentation representation = filters.elementAt(i);
                if (representation.getFilterType() != FilterRepresentation.TYPE_GEOMETRY) {
                    cacheBitmap = environment.applyRepresentation(representation, cacheBitmap);
                }
                if (DEBUG) {
                    Log.v(LOGTAG, " - " + i  + " => apply " + representation.getName());
                }
            }
            // Let's cache it!
            mSteps.elementAt(similarUpToIndex).cache = cacheBitmap;
        }

        if (DEBUG) {
            Log.v(LOGTAG, "process pipeline from " + similarUpToIndex
                    + " to " + (filters.size() - 1));
        }

        // Now we are good to go, let's use the cacheBitmap as a starting point
        for (int i = similarUpToIndex + 1; i < filters.size(); i++) {
            FilterRepresentation representation = filters.elementAt(i);
            CacheStep currentStep = mSteps.elementAt(i);
            cacheBitmap = cacheBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (representation.getFilterType() != FilterRepresentation.TYPE_GEOMETRY) {
                cacheBitmap = environment.applyRepresentation(representation, cacheBitmap);
            }
            currentStep.representation = representation.copy();
            currentStep.cache = cacheBitmap;
            if (DEBUG) {
                Log.v(LOGTAG, " - " + i  + " => apply " + representation.getName());
            }
        }

        if (DEBUG) {
            Log.v(LOGTAG, "now let's cleanup the cache...");
            displayNbBitmapsInCache();
        }

        // Let's see if we can cleanup the cache for unused bitmaps
        for (int i = 0; i < similarUpToIndex; i++) {
            CacheStep currentStep = mSteps.elementAt(i);
            currentStep.cache = null;
        }

        if (DEBUG) {
            Log.v(LOGTAG, "cleanup done...");
            displayNbBitmapsInCache();
        }
        return cacheBitmap;
    }

    private void displayFilters(Vector<FilterRepresentation> filters) {
        Log.v(LOGTAG, "------>>>");
        for (int i = 0; i < filters.size(); i++) {
            FilterRepresentation representation = filters.elementAt(i);
            CacheStep step = mSteps.elementAt(i);
            boolean similar = step.representation.equals(representation);
            Log.v(LOGTAG, "[" + i + "] - " + representation.getName()
                    + " similar rep ? " + (similar ? "YES" : "NO")
                    + " -- bitmap: " + step.cache);
        }
        Log.v(LOGTAG, "<<<------");
    }

    private void displayNbBitmapsInCache() {
        int nbBitmapsCached = 0;
        for (int i = 0; i < mSteps.size(); i++) {
            CacheStep step = mSteps.elementAt(i);
            if (step.cache != null) {
                nbBitmapsCached++;
            }
        }
        Log.v(LOGTAG, "nb bitmaps in cache: " + nbBitmapsCached + " / " + mSteps.size());
    }

}
