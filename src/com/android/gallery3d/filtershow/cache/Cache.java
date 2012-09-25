
package com.android.gallery3d.filtershow.cache;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public interface Cache {
    public void setOriginalBitmap(Bitmap bitmap);

    public void reset(ImagePreset preset);

    public void prepare(ImagePreset preset);

    public Bitmap get(ImagePreset preset);

    public void addObserver(ImageShow observer);
}
