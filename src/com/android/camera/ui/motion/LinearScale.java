/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.ui.motion;

/**
 * Represents a discrete linear scale function.
 */
public final class LinearScale {
    private final float mDomainA;
    private final float mDomainB;
    private final float mRangeA;
    private final float mRangeB;

    private final float mScale;

    public LinearScale(float domainA, float domainB, float rangeA, float rangeB) {
        mDomainA = domainA;
        mDomainB = domainB;
        mRangeA = rangeA;
        mRangeB = rangeB;

        // Precomputed ratio between input domain and output range.
        float scale = (mRangeB - mRangeA) / (mDomainB - mDomainA);
        mScale = Float.isNaN(scale) ? 0.0f : scale;
    }

    /**
     * Clamp a given domain value to the given domain.
     */
    public float clamp(float domainValue) {
        if (mDomainA > mDomainB) {
            return Math.max(mDomainB, Math.min(mDomainA, domainValue));
        }

        return Math.max(mDomainA, Math.min(mDomainB, domainValue));
    }

    /**
     * Returns true if the value is within the domain.
     */
    public boolean isInDomain(float domainValue) {
        if (mDomainA > mDomainB) {
            return domainValue <= mDomainA && domainValue >= mDomainB;
        }
        return domainValue >= mDomainA && domainValue <= mDomainB;
    }

    /**
     * Linearly scale a given domain value into the output range.
     */
    public float scale(float domainValue) {
        return mRangeA + (domainValue - mDomainA) * mScale;
    }

    /**
     * For the current domain and range parameters produce a new scale function
     * that is the inverse of the current scale function.
     */
    public LinearScale inverse() {
        return new LinearScale(mRangeA, mRangeB, mDomainA, mDomainB);
    }

    @Override
    public String toString() {
        return "LinearScale{" +
              "mDomainA=" + mDomainA +
              ", mDomainB=" + mDomainB +
              ", mRangeA=" + mRangeA +
              ", mRangeB=" + mRangeB + "}";
    }
}