
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBW;

public class ImagePresetBW extends ImagePreset {

    public String name() {
        return "Black & White";
    }

    public void setup() {
        mFilters.add(new ImageFilterBW());
    }

}
