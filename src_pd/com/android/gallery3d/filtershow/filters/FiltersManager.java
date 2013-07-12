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

package com.android.gallery3d.filtershow.filters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class FiltersManager extends BaseFiltersManager {
    private static FiltersManager sInstance = null;
    private static FiltersManager sPreviewInstance = null;
    private static FiltersManager sHighresInstance = null;
    private static int mImageBorderSize = 4; // in percent
    public FiltersManager() {
        init();
    }

    public static FiltersManager getPreviewManager() {
        if (sPreviewInstance == null) {
            sPreviewInstance = new FiltersManager();
        }
        return sPreviewInstance;
    }

    public static FiltersManager getManager() {
        if (sInstance == null) {
            sInstance = new FiltersManager();
        }
        return sInstance;
    }

    @Override
    public void addBorders(Context context) {

        // Do not localize
        String[] serializationNames = {
                "FRAME_4X5",
                "FRAME_BRUSH",
                "FRAME_GRUNGE",
                "FRAME_SUMI_E",
                "FRAME_TAPE",
                "FRAME_BLACK",
                "FRAME_BLACK_ROUNDED",
                "FRAME_WHITE",
                "FRAME_WHITE_ROUNDED",
                "FRAME_CREAM",
                "FRAME_CREAM_ROUNDED"
        };

        // The "no border" implementation
        int i = 0;
        FilterRepresentation rep = new FilterImageBorderRepresentation(0);
        mBorders.add(rep);

        // Regular borders
        ArrayList <FilterRepresentation> borderList = new ArrayList<FilterRepresentation>();


        rep = new FilterImageBorderRepresentation(R.drawable.filtershow_border_4x5);
        borderList.add(rep);

        rep = new FilterImageBorderRepresentation(R.drawable.filtershow_border_brush);
        borderList.add(rep);

        rep = new FilterImageBorderRepresentation(R.drawable.filtershow_border_grunge);
        borderList.add(rep);

        rep = new FilterImageBorderRepresentation(R.drawable.filtershow_border_sumi_e);
        borderList.add(rep);

        rep = new FilterImageBorderRepresentation(R.drawable.filtershow_border_tape);
        borderList.add(rep);

        rep = new FilterColorBorderRepresentation(Color.BLACK, mImageBorderSize, 0);
        borderList.add(rep);

        rep = new FilterColorBorderRepresentation(Color.BLACK, mImageBorderSize,
                mImageBorderSize);
        borderList.add(rep);

        rep = new FilterColorBorderRepresentation(Color.WHITE, mImageBorderSize, 0);
        borderList.add(rep);

        rep = new FilterColorBorderRepresentation(Color.WHITE, mImageBorderSize,
                mImageBorderSize);
        borderList.add(rep);

        int creamColor = Color.argb(255, 237, 237, 227);
        rep = new FilterColorBorderRepresentation(creamColor, mImageBorderSize, 0);
        borderList.add(rep);

        rep = new FilterColorBorderRepresentation(creamColor, mImageBorderSize,
                mImageBorderSize);
        borderList.add(rep);

        for (FilterRepresentation filter : borderList) {
            filter.setSerializationName(serializationNames[i++]);
            addRepresentation(filter);
        }

    }

    public static FiltersManager getHighresManager() {
        if (sHighresInstance == null) {
            sHighresInstance = new FiltersManager();
        }
        return sHighresInstance;
    }

    public static void reset() {
        sInstance = null;
        sPreviewInstance = null;
        sHighresInstance = null;
    }

    public static void setResources(Resources resources) {
        FiltersManager.getManager().setFilterResources(resources);
        FiltersManager.getPreviewManager().setFilterResources(resources);
        FiltersManager.getHighresManager().setFilterResources(resources);
    }
}
