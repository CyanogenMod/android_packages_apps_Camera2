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

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;

public class CategoryPanel extends Fragment {

    public static final String FRAGMENT_TAG = "CategoryPanel";
    private static final String PARAMETER_TAG = "currentPanel";

    private int mCurrentAdapter = MainPanel.LOOKS;
    private CategoryAdapter mAdapter;

    public void setAdapter(int value) {
        mCurrentAdapter = value;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        loadAdapter(mCurrentAdapter);
    }

    private void loadAdapter(int adapter) {
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        switch (adapter) {
            case MainPanel.LOOKS: {
                mAdapter = activity.getCategoryLooksAdapter();
                mAdapter.initializeSelection(MainPanel.LOOKS);
                break;
            }
            case MainPanel.BORDERS: {
                mAdapter = activity.getCategoryBordersAdapter();
                mAdapter.initializeSelection(MainPanel.BORDERS);
                break;
            }
            case MainPanel.GEOMETRY: {
                mAdapter = activity.getCategoryGeometryAdapter();
                mAdapter.initializeSelection(MainPanel.GEOMETRY);
                break;
            }
            case MainPanel.FILTERS: {
                mAdapter = activity.getCategoryFiltersAdapter();
                mAdapter.initializeSelection(MainPanel.FILTERS);
                break;
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(PARAMETER_TAG, mCurrentAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LinearLayout main = (LinearLayout) inflater.inflate(
                R.layout.filtershow_category_panel_new, container,
                false);

        if (savedInstanceState != null) {
            int selectedPanel = savedInstanceState.getInt(PARAMETER_TAG);
            loadAdapter(selectedPanel);
        }

        View panelView = main.findViewById(R.id.listItems);
        if (panelView instanceof CategoryTrack) {
            CategoryTrack panel = (CategoryTrack) panelView;
            mAdapter.setUseFilterIconButton(true);
            panel.setAdapter(mAdapter);
            mAdapter.setContainer(panel);
        } else {
            ListView panel = (ListView) main.findViewById(R.id.listItems);
            panel.setAdapter(mAdapter);
            mAdapter.setContainer(panel);
        }
        return main;
    }

}
