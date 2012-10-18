
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.util.Log;

public class ImageFilterTinyPlanet extends ImageFilter {
    private static final String TAG = ImageFilterTinyPlanet.class.getSimpleName();

    public ImageFilterTinyPlanet() {
        setFilterType(TYPE_TINYPLANET);
        mName = "TinyPlanet";
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        Log.d(TAG, "Applying tiny planet.");

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // Print TinyPlanet as text on the image as a placeholder
        // TODO(haeberling): Implement the real deal.
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize((int) (((mParameter + 100) / 200f) * 100));
        paint.setTextAlign(Align.CENTER);
        canvas.drawText("TinyPlanet", w / 2, h / 2, paint);
        return super.apply(bitmap, scaleFactor, highQuality);
    }
}
