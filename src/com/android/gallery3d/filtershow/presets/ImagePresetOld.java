
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterGradient;

import android.graphics.Color;

public class ImagePresetOld extends ImagePreset {

    public String name() {
        return "Old";
    }

    public void setup() {
        ImageFilterGradient filter = new ImageFilterGradient();
        filter.addColor(Color.BLACK, 0.0f);
        filter.addColor(Color.argb(255, 228, 231, 193), 1.0f);
        mFilters.add(filter);
    }

}
