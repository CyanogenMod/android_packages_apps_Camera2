
package com.android.gallery3d.filtershow.presets;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.filters.ImageFilterBWRed;
import com.android.gallery3d.filtershow.filters.ImageFilterFx;

public class ImagePresetFX extends ImagePreset {
    String name;
    Bitmap fxBitmap;

    public String name() {
        return name;
    }

    public ImagePresetFX(Bitmap bitmap, String name) {
        fxBitmap = bitmap;
        this.name = name;
        setup();
    }

    public void setup() {
        if (fxBitmap != null) {
            mFilters.add(new ImageFilterFx(fxBitmap,name));
        }
    }

}
