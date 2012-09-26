
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBWRed;

public class ImagePresetBWRed extends ImagePreset {

    public String name() {
        return "Black & White (Red)";
    }

    public void setup() {
        mFilters.add(new ImageFilterBWRed());
    }

}
