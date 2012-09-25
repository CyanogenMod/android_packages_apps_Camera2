
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBWBlue;

public class ImagePresetBWBlue extends ImagePreset {

    public String name() {
        return "Black & White (Blue)";
    }

    public void setup() {
        mFilters.add(new ImageFilterBWBlue());
    }

}
