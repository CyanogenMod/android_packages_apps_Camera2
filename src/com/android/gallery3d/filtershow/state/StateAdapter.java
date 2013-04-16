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

package com.android.gallery3d.filtershow.state;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;

import java.util.Vector;

public class StateAdapter extends ArrayAdapter<State> {

    private int mOrientation;
    private PanelTrack mListener;
    private String mOriginalText;
    private String mResultText;

    public StateAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mOriginalText = context.getString(R.string.state_panel_original);
        mResultText = context.getString(R.string.state_panel_result);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        StateView view = null;
        if (convertView == null) {
            convertView = new StateView(getContext());
        }
        view = (StateView) convertView;
        State state = getItem(position);
        view.setState(state);
        view.setOrientation(mOrientation);
        view.setBackgroundAlpha(1.0f);
        return view;
    }

    public boolean contains(State state) {
        for (int i = 0; i < getCount(); i++) {
            if (state == getItem(i)) {
                return true;
            }
        }
        return false;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    @Override
    public void notifyDataSetChanged() {
        if (mListener != null) {
            mListener.fillContent(false);
        }
    }

    public void addAll(Vector<FilterRepresentation> filters) {
        clear();
        add(new State(mOriginalText));
        for (FilterRepresentation filter : filters) {
            State state = new State(filter.getName());
            state.setFilterRepresentation(filter);
            add(state);
        }
        add(new State(mResultText));
        notifyDataSetChanged();
    }

    void setListener(PanelTrack listener) {
        mListener = listener;
    }

    @Override
    public void remove(State state) {
        super.remove(state);
        FilterRepresentation filterRepresentation = state.getFilterRepresentation();
        FilterShowActivity activity = (FilterShowActivity) getContext();
        activity.getPanelController().removeFilterRepresentation(filterRepresentation);
    }
}
