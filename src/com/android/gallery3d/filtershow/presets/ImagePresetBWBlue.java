
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBWBlue;

public class ImagePresetBWBlue extends ImagePreset {

    @Override
    public String name() {
        return "B&W - Blue";
    }

    @Override
    public void setup() {
        mFilters.add(new ImageFilterBWBlue());
    }

}
