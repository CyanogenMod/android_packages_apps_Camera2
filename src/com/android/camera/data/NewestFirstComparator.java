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

import java.util.Comparator;
import java.util.Date;

/**
 * Sort filmstrip items by newest first, then by most recently modified
 * then by title comparison.
 */
public class NewestFirstComparator implements Comparator<FilmstripItem> {
    @Override
    public int compare(FilmstripItem d1, FilmstripItem d2) {
        FilmstripItemData d1Data = d1.getData();
        FilmstripItemData d2Data = d2.getData();
        int cmp = compareDate(d1Data.getCreationDate(),
              d2Data.getCreationDate());
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
    private static int compareDate(Date v1, Date v2) {
        return v1.compareTo(v2) * -1;
    }
}