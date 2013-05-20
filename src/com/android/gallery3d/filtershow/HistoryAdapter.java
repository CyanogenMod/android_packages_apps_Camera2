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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.util.Vector;

public class HistoryAdapter extends ArrayAdapter<ImagePreset> {
    private static final String LOGTAG = "HistoryAdapter";
    private int mCurrentPresetPosition = 0;
    private String mBorders = null;
    private String mCrop = null;
    private String mRotate = null;
    private String mStraighten = null;
    private String mMirror = null;
    private MenuItem mUndoMenuItem = null;
    private MenuItem mRedoMenuItem = null;
    private MenuItem mResetMenuItem = null;

    private Bitmap mOriginalBitmap = null;

    public HistoryAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);
        FilterShowActivity activity = (FilterShowActivity) context;
        mBorders = context.getString(R.string.borders);
        mCrop = context.getString(R.string.crop);
        mRotate = context.getString(R.string.rotate);
        mStraighten = context.getString(R.string.straighten);
        mMirror = context.getString(R.string.mirror);
    }

    public void setMenuItems(MenuItem undoItem, MenuItem redoItem, MenuItem resetItem) {
        mUndoMenuItem = undoItem;
        mRedoMenuItem = redoItem;
        mResetMenuItem = resetItem;
        updateMenuItems();
    }

    public boolean canReset() {
        if (getCount() <= 1) {
            return false;
        }
        return true;
    }

    public boolean canUndo() {
        if (mCurrentPresetPosition == getCount() - 1) {
            return false;
        }
        return true;
    }

    public boolean canRedo() {
        if (mCurrentPresetPosition == 0) {
            return false;
        }
        return true;
    }

    public void updateMenuItems() {
        if (mUndoMenuItem != null) {
            setEnabled(mUndoMenuItem, canUndo());
        }
        if (mRedoMenuItem != null) {
            setEnabled(mRedoMenuItem, canRedo());
        }
        if (mResetMenuItem != null) {
            setEnabled(mResetMenuItem, canReset());
        }
    }

    private void setEnabled(MenuItem item, boolean enabled) {
        item.setEnabled(enabled);
        Drawable drawable = item.getIcon();
        if (drawable != null) {
            drawable.setAlpha(enabled ? 255 : 80);
        }
    }

    public void setCurrentPreset(int n) {
        mCurrentPresetPosition = n;
        updateMenuItems();
        this.notifyDataSetChanged();
    }

    public void reset() {
        if (getCount() == 0) {
            return;
        }
        ImagePreset first = getItem(getCount() - 1);
        clear();
        addHistoryItem(first);
        updateMenuItems();
    }

    public ImagePreset getLast() {
        if (getCount() == 0) {
            return null;
        }
        return getItem(0);
    }

    public ImagePreset getCurrent() {
        return getItem(mCurrentPresetPosition);
    }

    public void addHistoryItem(ImagePreset preset) {
        insert(preset, 0);
        updateMenuItems();
    }

    @Override
    public void insert(ImagePreset preset, int position) {
        if (mCurrentPresetPosition != 0) {
            // in this case, let's discount the presets before the current one
            Vector<ImagePreset> oldItems = new Vector<ImagePreset>();
            for (int i = mCurrentPresetPosition; i < getCount(); i++) {
                oldItems.add(getItem(i));
            }
            clear();
            for (int i = 0; i < oldItems.size(); i++) {
                add(oldItems.elementAt(i));
            }
            mCurrentPresetPosition = position;
            this.notifyDataSetChanged();
        }
        super.insert(preset, position);
        mCurrentPresetPosition = position;
        this.notifyDataSetChanged();
    }

    public int redo() {
        mCurrentPresetPosition--;
        if (mCurrentPresetPosition < 0) {
            mCurrentPresetPosition = 0;
        }
        this.notifyDataSetChanged();
        updateMenuItems();
        return mCurrentPresetPosition;
    }

    public int undo() {
        mCurrentPresetPosition++;
        if (mCurrentPresetPosition >= getCount()) {
            mCurrentPresetPosition = getCount() - 1;
        }
        this.notifyDataSetChanged();
        updateMenuItems();
        return mCurrentPresetPosition;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.filtershow_history_operation_row, null);
        }

        ImagePreset item = getItem(position);
        if (item != null) {
            TextView itemView = (TextView) view.findViewById(R.id.rowTextView);
            if (itemView != null) {
                itemView.setText(item.historyName());
            }
            ImageView preview = (ImageView) view.findViewById(R.id.preview);
            Bitmap bmp = item.getPreviewImage();
            if (position == getCount()-1 && mOriginalBitmap != null) {
                bmp = mOriginalBitmap;
            }
            if (bmp != null) {
                preview.setImageBitmap(bmp);
            } else {
                preview.setImageResource(android.R.color.transparent);
            }
            if (position == mCurrentPresetPosition) {
                view.setBackgroundColor(Color.WHITE);
            } else {
                view.setBackgroundResource(R.color.background_main_toolbar);
            }
        }

        return view;
    }


    public void setOriginalBitmap(Bitmap originalBitmap) {
        mOriginalBitmap = originalBitmap;
    }
}
