
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterBW;

public class ImagePresetBW extends ImagePreset {

    @Override
    public String name() {
        return "B&W";
    }

    @Override
    public void setup() {
        mFilters.add(new ImageFilterBW());
    }

}
