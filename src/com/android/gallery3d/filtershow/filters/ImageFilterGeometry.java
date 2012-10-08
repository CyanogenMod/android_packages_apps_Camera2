
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata.FLIP;

public class ImageFilterGeometry extends ImageFilter {
    private final Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
    private GeometryMetadata mGeometry = null;

    public ImageFilterGeometry() {
        mName = "Geometry";
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterGeometry filter = (ImageFilterGeometry) super.clone();
        return filter;
    }

    public void setGeometryMetadata(GeometryMetadata m){
        mGeometry = m;
    }

    native protected void nativeApplyFilterFlip(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, int flip);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if(mGeometry.getFlipType() == FLIP.NONE){
            return bitmap;
        }
        Bitmap flipBitmap = bitmap.copy(mConfig, true);
        nativeApplyFilterFlip(bitmap, bitmap.getWidth(), bitmap.getHeight(), flipBitmap,
                flipBitmap.getWidth(), flipBitmap.getHeight(), 1);
        return flipBitmap;
    }

}
