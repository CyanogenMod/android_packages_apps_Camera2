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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilterTinyPlanet;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.FilterIconButton;

public class CategoryAdapter extends ArrayAdapter<Action> {

    private static final String LOGTAG = "CategoryAdapter";
    private int mItemHeight;
    private View mContainer;
    private int mItemWidth = ListView.LayoutParams.MATCH_PARENT;
    private boolean mUseFilterIconButton = false;
    private int mSelectedPosition;
    int mCategory;

    public CategoryAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mItemHeight = (int) (context.getResources().getDisplayMetrics().density * 100);
    }

    public CategoryAdapter(Context context) {
        this(context, 0);
    }

    public void setItemHeight(int height) {
        mItemHeight = height;
    }

    public void setItemWidth(int width) {
        mItemWidth = width;
    }

    @Override
    public void add(Action action) {
        super.add(action);
        action.setAdapter(this);
    }

    public void initializeSelection(int category) {
        mCategory = category;
        if (category == MainPanel.LOOKS || category == MainPanel.BORDERS) {
            ImagePreset preset = MasterImage.getImage().getPreset();
            if (preset != null) {
                for (int i = 0; i < getCount(); i++) {
                    if (preset.historyName().equals(getItem(i).getRepresentation().getName())) {
                        mSelectedPosition = i;
                    }
                }
            }
        } else {
            mSelectedPosition = -1;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mUseFilterIconButton) {
            if (convertView == null) {
                LayoutInflater inflater =
                        (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.filtericonbutton, parent, false);
            }
            FilterIconButton view = (FilterIconButton) convertView;
            Action action = getItem(position);
            view.setAction(action);
            view.setup(action.getName(), null, this);
            view.setLayoutParams(
                    new ListView.LayoutParams(mItemWidth, mItemHeight));
            view.setTag(position);
            if (mCategory == MainPanel.LOOKS || mCategory == MainPanel.BORDERS) {
                view.setBackgroundResource(0);
            }
            return view;
        }
        if (convertView == null) {
            convertView = new CategoryView(getContext());
        }
        CategoryView view = (CategoryView) convertView;
        view.setAction(getItem(position), this);
        view.setLayoutParams(
                new ListView.LayoutParams(mItemWidth, mItemHeight));
        view.setTag(position);
        return view;
    }

    public void setSelected(View v) {
        int old = mSelectedPosition;
        mSelectedPosition = (Integer) v.getTag();
        if (old != -1) {
            invalidateView(old);
        }
        invalidateView(mSelectedPosition);
    }

    public boolean isSelected(View v) {
        return (Integer) v.getTag() == mSelectedPosition;
    }

    private void invalidateView(int position) {
        View child = null;
        if (mContainer instanceof ListView) {
            ListView lv = (ListView) mContainer;
            child = lv.getChildAt(position - lv.getFirstVisiblePosition());
        } else {
            CategoryTrack ct = (CategoryTrack) mContainer;
            child = ct.getChildAt(position);
        }
        if (child != null) {
            child.invalidate();
        }
    }

    public void setContainer(View container) {
        mContainer = container;
    }

    public void imageLoaded() {
        notifyDataSetChanged();
    }

    public void setUseFilterIconButton(boolean useFilterIconButton) {
        mUseFilterIconButton = useFilterIconButton;
    }

    public boolean isUseFilterIconButton() {
        return mUseFilterIconButton;
    }

    public FilterRepresentation getTinyPlanet() {
        for (int i = 0; i < getCount(); i++) {
            Action action = getItem(i);
            if (action.getRepresentation() != null
                    && action.getRepresentation().getFilterClass()
                    == ImageFilterTinyPlanet.class) {
                return action.getRepresentation();
            }
        }
        return null;
    }

    public void removeTinyPlanet() {
        for (int i = 0; i < getCount(); i++) {
            Action action = getItem(i);
            if (action.getRepresentation() != null
                    && action.getRepresentation().getFilterClass()
                    == ImageFilterTinyPlanet.class) {
                remove(action);
                return;
            }
        }
    }
}
