
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBWGreen;

public class ImagePresetBWGreen extends ImagePreset {

    @Override
    public String name() {
        return "B&W - Green";
    }

    @Override
    public void setup() {
        mFilters.add(new ImageFilterBWGreen());
    }

}
