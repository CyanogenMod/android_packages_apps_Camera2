
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBWRed;

public class ImagePresetBWRed extends ImagePreset {

    @Override
    public String name() {
        return "B&W - Red";
    }

    @Override
    public void setup() {
        mFilters.add(new ImageFilterBWRed());
    }

}
