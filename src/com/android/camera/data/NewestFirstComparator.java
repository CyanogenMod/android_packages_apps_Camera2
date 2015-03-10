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

package com.android.camera.data;

import com.google.common.base.Preconditions;

import java.util.Comparator;
import java.util.Date;

/**
 * Sort filmstrip items by newest first, then by most recently modified
 * then by title comparison.
 */
public class NewestFirstComparator implements Comparator<FilmstripItem> {
    private final Date mNow;

    private static final int MILLIS_IN_DAY = 24 * 60 * 60 * 1000;

    /**
     * Construct a comparator that sorts items by newest first. We ignore future
     * creation dates using the supplied current date as a baseline.
     *
     * @param now present date to be used in comparisons to rule out dates in
     *            the future.
     */
    public NewestFirstComparator(Date now) {
        Preconditions.checkNotNull(now);
        // Buffer by 24 hours to protect against false positives due to intraday
        // time zone issues.
        mNow = new Date(now.getTime() + MILLIS_IN_DAY);
    }

    @Override
    public int compare(FilmstripItem d1, FilmstripItem d2) {
        FilmstripItemData d1Data = d1.getData();
        FilmstripItemData d2Data = d2.getData();

        // If creation date is in future, fall back to modified, b/19565464.
        Date d1PrimaryDate = isFuture(d1Data.getCreationDate()) ?
                d1Data.getLastModifiedDate() : d1Data.getCreationDate();
        Date d2PrimaryDate = isFuture(d2Data.getCreationDate()) ?
                d2Data.getLastModifiedDate() : d2Data.getCreationDate();

        int cmp = compareDate(d1PrimaryDate, d2PrimaryDate);
        if (cmp == 0) {
            cmp = compareDate(d1Data.getLastModifiedDate(),
                  d2Data.getLastModifiedDate());
        }
        if (cmp == 0) {
            cmp = d1Data.getTitle().compareTo(d2Data.getTitle());
        }
        return cmp;
    }

    /**
     * Normal date comparison will sort these oldest first,
     * so invert the order by multiplying by -1.
     */
    private int compareDate(Date v1, Date v2) {
        return v1.compareTo(v2) * -1;
    }

    /**
     * Is the Date in the future from a base date. If the date is in the future
     * (larger) than the base date provided, return true.
     *
     * @param date The date to check whether it is in the future
     * @param base The date to use as a baseline 'present'
     * @return true if date is in the future from base
     */
    private boolean isFuture(Date date) {
        return mNow.compareTo(date) < 0;
    }
}