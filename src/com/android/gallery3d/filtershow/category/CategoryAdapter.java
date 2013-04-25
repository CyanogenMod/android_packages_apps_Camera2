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
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterTinyPlanetRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterTinyPlanet;
import com.android.gallery3d.filtershow.ui.FilterIconButton;

public class CategoryAdapter extends ArrayAdapter<Action> {

    private static final String LOGTAG = "CategoryAdapter";
    private int mItemHeight = 200;
    private ListView mContainer;
    private int mItemWidth = ListView.LayoutParams.MATCH_PARENT;
    private boolean mUseFilterIconButton = false;

    public CategoryAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
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
            view.setup(action.getName(), null);
            view.setLayoutParams(
                    new ListView.LayoutParams(mItemWidth, mItemHeight));
            return view;
        }
        if (convertView == null) {
            convertView = new CategoryView(getContext());
        }
        CategoryView view = (CategoryView) convertView;
        view.setAction(getItem(position));
        view.setLayoutParams(
                new ListView.LayoutParams(mItemWidth, mItemHeight));
        return view;
    }

    public void setContainer(ListView container) {
        mContainer = container;
    }

    public ListView getContainer() {
        return mContainer;
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
