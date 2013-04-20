/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.filtershow.category;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;

public class CategoryView extends View implements View.OnClickListener {

    private static final String LOGTAG = "CategoryView";
    private Paint mPaint = new Paint();
    private Action mAction;
    private Rect mTextBounds = new Rect();
    private static int sMargin = 16;
    private static int sTextSize = 32;
    private int mTextColor;
    private int mBackgroundColor;

    public static void setTextSize(int size) {
        sTextSize = size;
    }

    public static void setMargin(int margin) {
        sMargin = margin;
    }

    public CategoryView(Context context) {
        super(context);
        setOnClickListener(this);
        Resources res = getResources();
        mBackgroundColor = res.getColor(R.color.filtershow_categoryview_background);
        mTextColor = res.getColor(R.color.filtershow_categoryview_text);
    }

    public void drawText(Canvas canvas, String text) {
        if (text == null) {
            return;
        }
        text = text.toUpperCase();
        mPaint.reset();
        mPaint.setColor(mTextColor);
        mPaint.setTextSize(sTextSize);
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setAntiAlias(true);
        float textWidth = mPaint.measureText(text);
        mPaint.getTextBounds(text, 0, text.length(), mTextBounds);
        int x = (int) (canvas.getWidth() - textWidth - sMargin);
        int y = canvas.getHeight() - sMargin;
        canvas.drawText(text, x, y, mPaint);
    }

    public void onDraw(Canvas canvas) {
        canvas.drawColor(mBackgroundColor);
        if (mAction != null) {
            drawText(canvas, mAction.getName());
            if (mAction.getImage() == null) {
                mAction.setImageFrame(new Rect(0, 0, canvas.getWidth(), canvas.getHeight()));
            } else {
                Bitmap bitmap = mAction.getImage();
                canvas.drawBitmap(bitmap, 0, 0, mPaint);
            }
        }
    }

    public void setAction(Action action) {
        mAction = action;
        invalidate();
    }

    public FilterRepresentation getRepresentation() {
        return mAction.getRepresentation();
    }

    @Override
    public void onClick(View view) {
        FilterShowActivity activity = (FilterShowActivity) getContext();
        activity.showRepresentation(mAction.getRepresentation());
    }
}
