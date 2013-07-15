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

package com.android.gallery3d.filtershow.presets;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;

public class PresetManagementDialog extends DialogFragment implements View.OnClickListener {
    private UserPresetsAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.filtershow_presets_management_dialog, container);

        FilterShowActivity activity = (FilterShowActivity) getActivity();
        mAdapter = activity.getUserPresetsAdapter();
        ListView panel = (ListView) view.findViewById(R.id.listItems);
        panel.setAdapter(mAdapter);

        view.findViewById(R.id.cancel).setOnClickListener(this);
        view.findViewById(R.id.addpreset).setOnClickListener(this);
        view.findViewById(R.id.ok).setOnClickListener(this);
        getDialog().setTitle(getString(R.string.filtershow_manage_preset));
        return view;
    }

    @Override
    public void onClick(View v) {
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        switch (v.getId()) {
            case R.id.cancel:
                mAdapter.clearChangedRepresentations();
                mAdapter.clearDeletedRepresentations();
                activity.updateUserPresetsFromAdapter(mAdapter);
                dismiss();
                break;
            case R.id.addpreset:
                activity.saveCurrentImagePreset();
                dismiss();
                break;
            case R.id.ok:
                mAdapter.updateCurrent();
                activity.updateUserPresetsFromAdapter(mAdapter);
                dismiss();
                break;
        }
    }
}
