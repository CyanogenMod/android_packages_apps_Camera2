
package com.android.gallery3d.filtershow.presets;

import com.android.gallery3d.filtershow.filters.ImageFilterSaturated;

public class ImagePresetSaturated extends ImagePreset {

    @Override
    public String name() {
        return "Saturated";
    }

    @Override
    public void setup() {
        ImageFilterSaturated filter = new ImageFilterSaturated();
        filter.setParameter(50);
        mFilters.add(filter);
    }

}
