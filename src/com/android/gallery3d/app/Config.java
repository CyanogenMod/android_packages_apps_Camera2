/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.app;

import com.android.gallery3d.R;

import android.content.Context;
import android.content.res.Resources;

final class Config {
    public static class AlbumSetPage {
        private static AlbumSetPage sInstance;

        public final int slotWidth;
        public final int slotHeight;
        public final int displayItemSize;
        public final int labelFontSize;
        public final int labelOffsetY;
        public final int labelMargin;

        public static synchronized AlbumSetPage get(Context context) {
            if (sInstance == null) {
                sInstance = new AlbumSetPage(context);
            }
            return sInstance;
        }

        private AlbumSetPage(Context context) {
            Resources r = context.getResources();
            slotWidth = r.getDimensionPixelSize(R.dimen.albumset_slot_width);
            slotHeight = r.getDimensionPixelSize(R.dimen.albumset_slot_height);
            displayItemSize = r.getDimensionPixelSize(R.dimen.albumset_display_item_size);
            labelFontSize = r.getDimensionPixelSize(R.dimen.albumset_label_font_size);
            labelOffsetY = r.getDimensionPixelSize(R.dimen.albumset_label_offset_y);
            labelMargin = r.getDimensionPixelSize(R.dimen.albumset_label_margin);
        }
    }

    public static class AlbumPage {
        private static AlbumPage sInstance;

        public final int slotWidth;
        public final int slotHeight;
        public final int displayItemSize;

        public static synchronized AlbumPage get(Context context) {
            if (sInstance == null) {
                sInstance = new AlbumPage(context);
            }
            return sInstance;
        }

        private AlbumPage(Context context) {
            Resources r = context.getResources();
            slotWidth = r.getDimensionPixelSize(R.dimen.album_slot_width);
            slotHeight = r.getDimensionPixelSize(R.dimen.album_slot_height);
            displayItemSize = r.getDimensionPixelSize(R.dimen.album_display_item_size);
        }
    }

    public static class ManageCachePage extends AlbumSetPage {
        private static ManageCachePage sInstance;

        public final int cacheBarHeight;
        public final int cacheBarPinLeftMargin;
        public final int cacheBarPinRightMargin;
        public final int cacheBarButtonRightMargin;
        public final int cacheBarFontSize;

        public static synchronized ManageCachePage get(Context context) {
            if (sInstance == null) {
                sInstance = new ManageCachePage(context);
            }
            return sInstance;
        }

        public ManageCachePage(Context context) {
            super(context);
            Resources r = context.getResources();
            cacheBarHeight = r.getDimensionPixelSize(R.dimen.cache_bar_height);
            cacheBarPinLeftMargin = r.getDimensionPixelSize(R.dimen.cache_bar_pin_left_margin);
            cacheBarPinRightMargin = r.getDimensionPixelSize(
                    R.dimen.cache_bar_pin_right_margin);
            cacheBarButtonRightMargin = r.getDimensionPixelSize(
                    R.dimen.cache_bar_button_right_margin);
            cacheBarFontSize = r.getDimensionPixelSize(R.dimen.cache_bar_font_size);
        }
    }

    public static class PhotoPage {
        private static PhotoPage sInstance;

        // These are all height values. See the comment in FilmStripView for
        // the meaning of these values.
        public final int filmstripTopMargin;
        public final int filmstripMidMargin;
        public final int filmstripBottomMargin;
        public final int filmstripThumbSize;
        public final int filmstripContentSize;
        public final int filmstripGripSize;
        public final int filmstripBarSize;

        // These are width values.
        public final int filmstripGripWidth;

        public static synchronized PhotoPage get(Context context) {
            if (sInstance == null) {
                sInstance = new PhotoPage(context);
            }
            return sInstance;
        }

        public PhotoPage(Context context) {
            Resources r = context.getResources();
            filmstripTopMargin = r.getDimensionPixelSize(R.dimen.filmstrip_top_margin);
            filmstripMidMargin = r.getDimensionPixelSize(R.dimen.filmstrip_mid_margin);
            filmstripBottomMargin = r.getDimensionPixelSize(R.dimen.filmstrip_bottom_margin);
            filmstripThumbSize = r.getDimensionPixelSize(R.dimen.filmstrip_thumb_size);
            filmstripContentSize = r.getDimensionPixelSize(R.dimen.filmstrip_content_size);
            filmstripGripSize = r.getDimensionPixelSize(R.dimen.filmstrip_grip_size);
            filmstripBarSize = r.getDimensionPixelSize(R.dimen.filmstrip_bar_size);
            filmstripGripWidth = r.getDimensionPixelSize(R.dimen.filmstrip_grip_width);
        }
    }
}

