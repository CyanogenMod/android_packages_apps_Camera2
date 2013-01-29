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

package com.android.gallery3d.ingest.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;

import com.android.gallery3d.R;

public class MtpThumbnailTileView extends ImageView implements Checkable {
    private static final int FADE_IN_TIME_MS = 80;

    private Paint mForegroundPaint;
    private boolean mIsChecked;
    private int mObjectHandle;
    private int mGeneration;

    private void init() {
        mForegroundPaint = new Paint();
        mForegroundPaint.setColor(getResources().getColor(R.color.ingest_highlight_semitransparent));
        showPlaceholder();
    }

    public MtpThumbnailTileView(Context context) {
        super(context);
        init();
    }

    public MtpThumbnailTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MtpThumbnailTileView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Force this to be square
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mIsChecked) {
            canvas.drawRect(canvas.getClipBounds(), mForegroundPaint);
        }
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        mIsChecked = checked;
    }

    @Override
    public void toggle() {
        setChecked(!mIsChecked);
    }

    private void showPlaceholder() {
        setAlpha(0f);
    }

    private LoadThumbnailTask mTask;

    public void setMtpDeviceAndObjectInfo(MtpDevice device, MtpObjectInfo object, int gen) {
        int handle = object.getObjectHandle();
        if (handle == mObjectHandle && gen == mGeneration) {
            return;
        }
        animate().cancel();
        if (mTask != null) {
            mTask.cancel(true);
        }
        mGeneration = gen;
        mObjectHandle = handle;
        Bitmap thumbnail = MtpBitmapCache.getInstanceForDevice(device)
                .get(handle);
        if (thumbnail != null) {
            setAlpha(1f);
            setImageBitmap(thumbnail);
        } else {
            showPlaceholder();
            mTask = new LoadThumbnailTask(device);
            mTask.execute(object);
        }
    }

    private class LoadThumbnailTask extends AsyncTask<MtpObjectInfo, Void, Bitmap> {
        private MtpDevice mDevice;

        public LoadThumbnailTask(MtpDevice device) {
            mDevice = device;
        }

        @Override
        protected Bitmap doInBackground(MtpObjectInfo... args) {
            Bitmap result = null;
            if (!isCancelled()) {
                result = MtpBitmapCache.getInstanceForDevice(mDevice).getOrCreate(
                        args[0].getObjectHandle());
            }
            mDevice = null;
            return result;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (isCancelled() || result == null) {
                return;
            }
            setAlpha(0f);
            setImageBitmap(result);
            animate().alpha(1f).setDuration(FADE_IN_TIME_MS);
        }

        @Override
        protected void onCancelled() {
        }
    }
}
