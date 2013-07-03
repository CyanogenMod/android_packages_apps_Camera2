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
import android.view.View;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.ui.SelectionRenderer;

public class CategoryView extends View implements View.OnClickListener {

    private static final String LOGTAG = "CategoryView";
    public static final int VERTICAL = 0;
    public static final int HORIZONTAL = 1;
    private Paint mPaint = new Paint();
    private Action mAction;
    private Rect mTextBounds = new Rect();
    private int mMargin = 16;
    private int mTextSize = 32;
    private int mTextColor;
    private int mBackgroundColor;
    private Paint mSelectPaint;
    CategoryAdapter mAdapter;
    private int mSelectionStroke;
    private Paint mBorderPaint;
    private int mBorderStroke;
    private int mOrientation = VERTICAL;

    public CategoryView(Context context) {
        super(context);
        setOnClickListener(this);
        Resources res = getResources();
        mBackgroundColor = res.getColor(R.color.filtershow_categoryview_background);
        mTextColor = res.getColor(R.color.filtershow_categoryview_text);
        mSelectionStroke = res.getDimensionPixelSize(R.dimen.thumbnail_margin);
        mTextSize = res.getDimensionPixelSize(R.dimen.category_panel_text_size);
        mMargin = res.getDimensionPixelOffset(R.dimen.category_panel_margin);
        mSelectPaint = new Paint();
        mSelectPaint.setStyle(Paint.Style.FILL);
        mSelectPaint.setColor(res.getColor(R.color.filtershow_category_selection));
        mBorderPaint = new Paint(mSelectPaint);
        mBorderPaint.setColor(Color.BLACK);
        mBorderStroke = mSelectionStroke / 3;
    }

    private void computeTextPosition(String text) {
        if (text == null) {
            return;
        }
        mPaint.setTextSize(mTextSize);
        if (mOrientation == VERTICAL) {
            text = text.toUpperCase();
            // TODO: set this in xml
            mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }
        mPaint.getTextBounds(text, 0, text.length(), mTextBounds);
    }

    public void drawText(Canvas canvas, String text) {
        if (text == null) {
            return;
        }
        float textWidth = mPaint.measureText(text);
        int x = (int) (canvas.getWidth() - textWidth - mMargin);
        if (mOrientation == HORIZONTAL) {
            x = (int) ((canvas.getWidth() - textWidth) / 2.0f);
        }
        if (x < 0) {
            // If the text takes more than the view width,
            // justify to the left.
            x = mMargin;
        }
        int y = canvas.getHeight() - mMargin;
        canvas.drawText(text, x, y, mPaint);
    }

    @Override
    public CharSequence getContentDescription () {
        if (mAction != null) {
            return mAction.getName();
        }
        return null;
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawColor(mBackgroundColor);
        if (mAction != null) {
            mPaint.reset();
            mPaint.setAntiAlias(true);
            computeTextPosition(mAction.getName());
            if (mAction.getImage() == null) {
                mAction.setImageFrame(new Rect(0, 0, getWidth(), getHeight()), mOrientation);
            } else {
                Bitmap bitmap = mAction.getImage();
                canvas.save();
                Rect clipRect = new Rect(mSelectionStroke, mSelectionStroke,
                        getWidth() - mSelectionStroke,
                        getHeight() - 2* mMargin - mTextSize);
                int offsetx = 0;
                int offsety = 0;
                if (mOrientation == HORIZONTAL) {
                    canvas.clipRect(clipRect);
                    offsetx = - (bitmap.getWidth() - clipRect.width()) / 2;
                    offsety = - (bitmap.getHeight() - clipRect.height()) / 2;
                }
                canvas.drawBitmap(bitmap, offsetx, offsety, mPaint);
                canvas.restore();
                if (mAdapter.isSelected(this)) {
                    if (mOrientation == HORIZONTAL) {
                        SelectionRenderer.drawSelection(canvas, 0, 0,
                                getWidth(), getHeight() - mMargin - mTextSize,
                                mSelectionStroke, mSelectPaint, mBorderStroke, mBorderPaint);
                    } else {
                        SelectionRenderer.drawSelection(canvas, 0, 0,
                                Math.min(bitmap.getWidth(), getWidth()),
                                Math.min(bitmap.getHeight(), getHeight()),
                                mSelectionStroke, mSelectPaint, mBorderStroke, mBorderPaint);
                    }
                }
            }
            mPaint.setColor(mBackgroundColor);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(3);
            drawText(canvas, mAction.getName());
            mPaint.setColor(mTextColor);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setStrokeWidth(1);
            drawText(canvas, mAction.getName());
        }
    }

    public void setAction(Action action, CategoryAdapter adapter) {
        mAction = action;
        mAdapter = adapter;
        invalidate();
    }

    public FilterRepresentation getRepresentation() {
        return mAction.getRepresentation();
    }

    @Override
    public void onClick(View view) {
        FilterShowActivity activity = (FilterShowActivity) getContext();
        activity.showRepresentation(mAction.getRepresentation());
        mAdapter.setSelected(this);
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }
}
