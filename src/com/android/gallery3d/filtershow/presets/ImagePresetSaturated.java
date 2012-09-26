
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterSaturated;

public class ImagePresetSaturated extends ImagePreset {

    public String name() {
        return "Saturated";
    }

    public void setup() {
        mFilters.add(new ImageFilterSaturated());
    }

}
