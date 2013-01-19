/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.editors;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.ImageFilter;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 * The basic editor that all the one parameter filters
 */
public class BasicEditor extends Editor implements OnSeekBarChangeListener {
    public static int ID = R.id.basicEditor;
    private SeekBar mSeekBar;
    private final String LOGTAG = "Editor";

    public BasicEditor() {
        super(ID);
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        unpack(R.id.basicEditor, R.layout.filtershow_default_editor);
        mSeekBar = (SeekBar) mView.findViewById(R.id.filterSeekBar);
        mSeekBar.setOnSeekBarChangeListener(this);
    }

    @Override
    public void reflectCurrentFilter() {
        ImageFilter filter = mImageShow.getCurrentFilter();
        if (filter == null) {
            return;
        }
        boolean f = filter.showParameterValue();
        mSeekBar.setVisibility((f) ? View.VISIBLE : View.INVISIBLE);
        int parameter = filter.getParameter();
        int maxp = filter.getMaxParameter();
        int minp = filter.getMinParameter();
        mSeekBar.setMax(maxp - minp);
        mSeekBar.setProgress(parameter - minp);
    }

    @Override
    public void onProgressChanged(SeekBar sbar, int progress, boolean arg2) {
        ImageFilter filter = mImageShow.getCurrentFilter();
        int minp = filter.getMinParameter();
        int value = progress + minp;
        mImageShow.onNewValue(value);
        mView.invalidate();
        if (filter.showParameterValue()) {
            mPanelController.onNewValue(value);
        }

        Log.v(LOGTAG, "    #### progress=" + value);
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
    }
}
