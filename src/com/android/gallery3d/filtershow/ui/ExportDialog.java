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

package com.android.gallery3d.filtershow.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.ProcessingService;
import com.android.gallery3d.filtershow.tools.SaveImage;

import java.io.File;

public class ExportDialog extends DialogFragment implements View.OnClickListener, SeekBar.OnSeekBarChangeListener{
    SeekBar mSeekBar;
    TextView mSeekVal;
    String mSliderLabel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.filtershow_export_dialog, container);
        mSeekBar = (SeekBar) view.findViewById(R.id.qualitySeekBar);
        mSeekVal = (TextView) view.findViewById(R.id.qualityTextView);
        mSliderLabel = getString(R.string.quality) + ": ";
        mSeekVal.setText(mSliderLabel + mSeekBar.getProgress());
        mSeekBar.setOnSeekBarChangeListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);
        view.findViewById(R.id.done).setOnClickListener(this);
        getDialog().setTitle(R.string.export_flattened);
        return view;
    }

    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // Do nothing
    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
     // Do nothing
    }

    @Override
    public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
        mSeekVal.setText(mSliderLabel + arg1);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancel:
                dismiss();
                break;
            case R.id.done:
                FilterShowActivity activity = (FilterShowActivity) getActivity();
                Uri sourceUri = MasterImage.getImage().getUri();
                File dest = SaveImage.getNewFile(activity, sourceUri);
                Intent processIntent = ProcessingService.getSaveIntent(activity, MasterImage
                        .getImage().getPreset(), dest, activity.getSelectedImageUri(), sourceUri,
                        true, mSeekBar.getProgress());
                activity.startService(processIntent);
                dismiss();
                break;
        }
    }
}
