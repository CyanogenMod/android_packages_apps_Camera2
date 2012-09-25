
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBWGreen;

public class ImagePresetBWGreen extends ImagePreset {

    public String name() {
        return "Black & White (Green)";
    }

    public void setup() {
        mFilters.add(new ImageFilterBWGreen());
    }

}
