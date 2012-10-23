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

package com.android.gallery3d.filtershow;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.ImageFilter;

public class ImageStateAdapter extends ArrayAdapter<ImageFilter> {
    private static final String LOGTAG = "ImageStateAdapter";

    public ImageStateAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.filtershow_imagestate_row, null);
        }
        ImageFilter filter = getItem(position);
        if (filter != null) {
            TextView itemLabel = (TextView) view.findViewById(R.id.imagestate_label);
            itemLabel.setText(filter.getName());
            TextView itemParameter = (TextView) view.findViewById(R.id.imagestate_parameter);
            itemParameter.setText("" + filter.getParameter());
        }
        return view;
    }
}
