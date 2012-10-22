/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.exif;

public class Rational {

    private final long mNominator;
    private final long mDenominator;

    public Rational(long nominator, long denominator) {
        mNominator = nominator;
        mDenominator = denominator;
    }

    public long getNominator() {
        return mNominator;
    }

    public long getDenominator() {
        return mDenominator;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Rational) {
            Rational data = (Rational) obj;
            return mNominator == data.mNominator && mDenominator == data.mDenominator;
        }
        return false;
    }
}