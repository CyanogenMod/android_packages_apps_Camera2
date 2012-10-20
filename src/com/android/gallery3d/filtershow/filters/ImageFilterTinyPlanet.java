
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.presets.ImagePreset;

/**
 * An image filter which creates a tiny planet projection.
 */
public class ImageFilterTinyPlanet extends ImageFilter {
    private static final String TAG = ImageFilterTinyPlanet.class.getSimpleName();

    public ImageFilterTinyPlanet() {
        setFilterType(TYPE_TINYPLANET);
        mName = "TinyPlanet";

        mMinParameter = 10;
        mMaxParameter = 60;
        mDefaultParameter = 20;
        mPreviewParameter = 20;
        mParameter = 20;
    }

    native protected void nativeApplyFilter(
            Bitmap bitmapIn, int width, int height, Bitmap bitmapOut, int outSize, float scale);

    @Override
    public Bitmap apply(Bitmap bitmapIn, float scaleFactor, boolean highQuality) {
        ImagePreset preset = getImagePreset();
        if (preset != null) {
            if (preset.isPanoramaSafe()) {
                // TODO(haeberling): Get XMPMeta object.
                Object xmp = preset.getImageLoader().getXmpObject();
            } else {
                // TODO(haeberling): What should we do for:
                // !preset.isPanoramaSafe()?
            }
        }

        int w = bitmapIn.getWidth();
        int h = bitmapIn.getHeight();
        int outputSize = Math.min(w, h);

        Bitmap mBitmapOut = Bitmap.createBitmap(
                outputSize, outputSize, Bitmap.Config.ARGB_8888);

        // TODO(haeberling): Add the padding back in based on the meta-data.
        nativeApplyFilter(bitmapIn, w, h, mBitmapOut, outputSize, mParameter / 100f);
        return mBitmapOut;
    }
}
