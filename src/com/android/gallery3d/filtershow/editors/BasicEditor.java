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
import com.android.gallery3d.filtershow.filters.*;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

/**
 * The basic editor that all the one parameter filters
 */
public class BasicEditor extends Editor implements OnSeekBarChangeListener {
    public static int ID = R.id.basicEditor;
    private SeekBar mSeekBar;
    private final String LOGTAG = "Editor";
    private int mLayoutID = R.layout.filtershow_default_editor;
    private int mViewID = R.id.basicEditor;

    public BasicEditor() {
        super(ID);
    }

    protected BasicEditor(int id) {
        super(id);
    }

    protected BasicEditor(int id, int layoutID, int viewID) {
        super(id);
        int mLayoutID = layoutID;
        int mViewID = viewID;
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        unpack(mViewID, mLayoutID);
        mSeekBar = (SeekBar) mView.findViewById(R.id.filterSeekBar);
        mSeekBar.setOnSeekBarChangeListener(this);
    }

    @Override
    public void reflectCurrentFilter() {
        FilterRepresentation filterRepresentation = MasterImage.getImage().getCurrentFilterRepresentation();
        if (filterRepresentation != null && filterRepresentation instanceof FilterBasicRepresentation) {
            FilterBasicRepresentation interval = (FilterBasicRepresentation) filterRepresentation;
            boolean f = interval.showParameterValue();
            mSeekBar.setVisibility((f) ? View.VISIBLE : View.INVISIBLE);
            int value = interval.getValue();
            int min = interval.getMinimum();
            int max = interval.getMaximum();
            mSeekBar.setMax(max - min);
            mSeekBar.setProgress(value - min);
        }
    }

    @Override
    public void onProgressChanged(SeekBar sbar, int progress, boolean arg2) {
        FilterRepresentation filterRepresentation = MasterImage.getImage().getCurrentFilterRepresentation();
        if (filterRepresentation != null && filterRepresentation instanceof FilterBasicRepresentation) {
            FilterBasicRepresentation interval = (FilterBasicRepresentation) filterRepresentation;
            int value = progress + interval.getMinimum();
            interval.setValue(value);
            mImageShow.onNewValue(value);
            mView.invalidate();
            if (interval.showParameterValue()) {
                mPanelController.onNewValue(value);
            }

            Log.v(LOGTAG, "    #### progress=" + value);
            MasterImage.getImage().updateBuffers();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
    }
}
