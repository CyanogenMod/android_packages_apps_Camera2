/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.View.MeasureSpec;

import java.io.File;

public class CacheBarView extends GLView implements TextButton.OnClickedListener {
    private static final String TAG = "CacheBarView";
    private static final int FONT_COLOR = 0xffffffff;
    private static final int MSG_REFRESH_STORAGE = 1;
    private static final int PIN_SIZE = 36;

    public interface Listener {
        void onDoneClicked();
    }

    private GalleryActivity mActivity;
    private Context mContext;

    private StorageInfo mStorageInfo;
    private long mUserChangeDelta;
    private Future<StorageInfo> mStorageInfoFuture;
    private Handler mHandler;

    private int mTotalHeight;
    private int mPinLeftMargin;
    private int mPinRightMargin;
    private int mButtonRightMargin;

    private NinePatchTexture mBackground;
    private GLView mLeftPin;            // The pin icon.
    private GLView mLeftLabel;          // "Make available offline"
    private ProgressBar mStorageBar;
    private Label mStorageLabel;        // "27.26 GB free"
    private TextButton mDoneButton;     // "Done"

    private Listener mListener;

    public CacheBarView(GalleryActivity activity, int resBackground, int height,
            int pinLeftMargin, int pinRightMargin, int buttonRightMargin,
            int fontSize) {
        mActivity = activity;
        mContext = activity.getAndroidContext();

        mPinLeftMargin = pinLeftMargin;
        mPinRightMargin = pinRightMargin;
        mButtonRightMargin = buttonRightMargin;

        mBackground = new NinePatchTexture(mContext, resBackground);
        Rect paddings = mBackground.getPaddings();

        // The total height of the strip that includes the bar containing Pin,
        // Label, DoneButton, ..., ect. and the extended fading bar.
        mTotalHeight = height + paddings.top;

        mLeftPin = new Icon(mContext, R.drawable.ic_manage_pin, PIN_SIZE, PIN_SIZE);
        mLeftLabel = new Label(mContext, R.string.make_available_offline,
                fontSize, FONT_COLOR);
        addComponent(mLeftPin);
        addComponent(mLeftLabel);

        mDoneButton = new TextButton(mContext, R.string.done);
        mDoneButton.setOnClickListener(this);
        NinePatchTexture normal = new NinePatchTexture(
                mContext, R.drawable.btn_default_normal_holo_dark);
        NinePatchTexture pressed = new NinePatchTexture(
                mContext, R.drawable.btn_default_pressed_holo_dark);
        mDoneButton.setNormalBackground(normal);
        mDoneButton.setPressedBackground(pressed);
        addComponent(mDoneButton);

        // Initially the progress bar and label are invisible.
        // It will be made visible after we have the storage information.
        mStorageBar = new ProgressBar(mContext,
                R.drawable.progress_primary_holo_dark,
                R.drawable.progress_secondary_holo_dark,
                R.drawable.progress_bg_holo_dark);
        mStorageLabel = new Label(mContext, "", 14, Color.WHITE);
        addComponent(mStorageBar);
        addComponent(mStorageLabel);
        mStorageBar.setVisibility(GLView.INVISIBLE);
        mStorageLabel.setVisibility(GLView.INVISIBLE);

        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_REFRESH_STORAGE:
                        mStorageInfo = (StorageInfo) msg.obj;
                        refreshStorageInfo();
                        break;
                }
            }
        };
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    // Called by mDoneButton
    public void onClicked(GLView source) {
        if (mListener != null) {
            mListener.onDoneClicked();
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        // The size of mStorageLabel can change, so we need to layout
        // even if the size of CacheBarView does not change.
        int w = right - left;
        int h = bottom - top;

        mLeftPin.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int pinH = mLeftPin.getMeasuredHeight();
        int pinW = mLeftPin.getMeasuredWidth();
        int pinT = (h - pinH) / 2;
        int pinL = mPinLeftMargin;
        mLeftPin.layout(pinL, pinT, pinL + pinW, pinT + pinH);

        mLeftLabel.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int labelH = mLeftLabel.getMeasuredHeight();
        int labelW = mLeftLabel.getMeasuredWidth();
        int labelT = (h - labelH) / 2;
        int labelL = pinL + pinW + mPinRightMargin;
        mLeftLabel.layout(labelL, labelT, labelL + labelW, labelT + labelH);

        mDoneButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int doneH = mDoneButton.getMeasuredHeight();
        int doneW = mDoneButton.getMeasuredWidth();
        int doneT = (h - doneH) / 2;
        int doneR = w - mButtonRightMargin;
        mDoneButton.layout(doneR - doneW, doneT, doneR, doneT + doneH);

        int centerX = w / 2;
        int centerY = h / 2;

        int capBarH = 20;
        int capBarW = 200;
        int capBarT = centerY - capBarH / 2;
        int capBarL = centerX - capBarW / 2;
        mStorageBar.layout(capBarL, capBarT, capBarL + capBarW,
                capBarT + capBarH);

        mStorageLabel.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int capLabelH = mStorageLabel.getMeasuredHeight();
        int capLabelW = mStorageLabel.getMeasuredWidth();
        int capLabelT = centerY - capLabelH / 2;
        int capLabelL = centerX + capBarW / 2 + 8;
        mStorageLabel.layout(capLabelL , capLabelT, capLabelL + capLabelW,
                capLabelT + capLabelH);
    }

    public void refreshStorageInfo() {
        long used = mStorageInfo.usedBytes;
        long total = mStorageInfo.totalBytes;
        long cached = mStorageInfo.usedCacheBytes;
        long target = mStorageInfo.targetCacheBytes;

        double primary = (double) used / total;
        double secondary =
                (double) (used - cached + target + mUserChangeDelta) / total;

        mStorageBar.setProgress((int) (primary * 10000));
        mStorageBar.setSecondaryProgress((int) (secondary * 10000));

        long freeBytes = mStorageInfo.totalBytes - mStorageInfo.usedBytes;
        String sizeString = Formatter.formatFileSize(mContext, freeBytes);
        String label = mContext.getString(R.string.free_space_format, sizeString);
        mStorageLabel.setText(label);
        mStorageBar.setVisibility(GLView.VISIBLE);
        mStorageLabel.setVisibility(GLView.VISIBLE);
        requestLayout(); // because the size of the label may have changed.
    }

    public void increaseTargetCacheSize(long delta) {
        mUserChangeDelta += delta;
        refreshStorageInfo();
    }

    @Override
    protected void renderBackground(GLCanvas canvas) {
        Rect paddings = mBackground.getPaddings();
        mBackground.draw(canvas, 0, -paddings.top, getWidth(), mTotalHeight);
    }

    public void resume() {
        mStorageInfoFuture = mActivity.getThreadPool().submit(
            new StorageInfoJob(),
            new FutureListener<StorageInfo>() {
                    public void onFutureDone(Future<StorageInfo> future) {
                        mStorageInfoFuture = null;
                        if (!future.isCancelled()) {
                            mHandler.sendMessage(mHandler.obtainMessage(
                                    MSG_REFRESH_STORAGE, future.get()));
                        }
                    }
                });
    }

    public void pause() {
        if (mStorageInfoFuture != null) {
            mStorageInfoFuture.cancel();
            mStorageInfoFuture = null;
        }
        mStorageBar.setVisibility(GLView.INVISIBLE);
        mStorageLabel.setVisibility(GLView.INVISIBLE);
    }

    public static class StorageInfo {
        long totalBytes;      // number of bytes the storage has.
        long usedBytes;       // number of bytes already used.
        long usedCacheBytes;  // number of bytes used for the cache (should be less
                              // then usedBytes).
        long targetCacheBytes;// number of bytes used for the cache
                              // if all pending downloads (and removals) are completed.
    }

    private class StorageInfoJob implements Job<StorageInfo> {
        public StorageInfo run(JobContext jc) {
            File cacheDir = mContext.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = mContext.getCacheDir();
            }
            String path = cacheDir.getAbsolutePath();
            StatFs stat = new StatFs(path);
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            long totalBlocks = stat.getBlockCount();
            StorageInfo si = new StorageInfo();
            si.totalBytes = blockSize * totalBlocks;
            si.usedBytes = blockSize * (totalBlocks - availableBlocks);
            si.usedCacheBytes = mActivity.getDataManager().getTotalUsedCacheSize();
            si.targetCacheBytes = mActivity.getDataManager().getTotalTargetCacheSize();
            return si;
        }
    }
}
