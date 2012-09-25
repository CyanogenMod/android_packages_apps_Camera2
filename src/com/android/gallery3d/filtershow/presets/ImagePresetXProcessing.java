
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterGradient;

import android.graphics.Color;

public class ImagePresetXProcessing extends ImagePreset {

    public String name() {
        return "X-Process";
    }

    public void setup() {
        ImageFilterGradient filter = new ImageFilterGradient();
        filter.addColor(Color.BLACK, 0.0f);
        filter.addColor(Color.argb(255, 29, 82, 83), 0.4f);
        filter.addColor(Color.argb(255, 211, 217, 186), 1.0f);
        mFilters.add(filter);
    }

}
