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
package com.android.gallery3d.filtershow.editors;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.controller.BasicParameterStyle;
import com.android.gallery3d.filtershow.controller.FilterView;
import com.android.gallery3d.filtershow.controller.Parameter;
import com.android.gallery3d.filtershow.filters.FilterChanSatRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.pipeline.RenderingRequest;
import com.android.gallery3d.filtershow.pipeline.RenderingRequestCaller;

public class EditorChanSat extends ParametricEditor implements OnSeekBarChangeListener, FilterView {
    public static final int ID = R.id.editorChanSat;
    private final String LOGTAG = "EditorGrunge";
    private SwapButton mButton;
    private final Handler mHandler = new Handler();

    int[] mMenuStrings = {
            R.string.editor_chan_sat_main,
            R.string.editor_chan_sat_red,
            R.string.editor_chan_sat_yellow,
            R.string.editor_chan_sat_green,
            R.string.editor_chan_sat_cyan,
            R.string.editor_chan_sat_blue,
            R.string.editor_chan_sat_magenta
    };

    String mCurrentlyEditing = null;

    public EditorChanSat() {
        super(ID, R.layout.filtershow_default_editor, R.id.basicEditor);
    }

    @Override
    public String calculateUserMessage(Context context, String effectName, Object parameterValue) {
        FilterRepresentation rep = getLocalRepresentation();
        if (rep == null || !(rep instanceof FilterChanSatRepresentation)) {
            return "";
        }
        FilterChanSatRepresentation csrep = (FilterChanSatRepresentation) rep;
        int mode = csrep.getParameterMode();
        String paramString;

        paramString = mContext.getString(mMenuStrings[mode]);

        int val = csrep.getCurrentParameter();
        return paramString + ((val > 0) ? " +" : " ") + val;
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        mButton = (SwapButton) accessoryViewList.findViewById(R.id.applyEffect);
        mButton.setText(mContext.getString(R.string.editor_chan_sat_main));

        final PopupMenu popupMenu = new PopupMenu(mImageShow.getActivity(), mButton);

        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_chan_sat, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                selectMenuItem(item);
                return true;
            }
        });
        mButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                popupMenu.show();
            }
        });
        mButton.setListener(this);

        FilterChanSatRepresentation csrep = getChanSatRep();
        String menuString = mContext.getString(mMenuStrings[0]);
        switchToMode(csrep, FilterChanSatRepresentation.MODE_MASTER, menuString);

    }

    public int getParameterIndex(int id) {
        switch (id) {
            case R.id.editor_chan_sat_main:
                return FilterChanSatRepresentation.MODE_MASTER;
            case R.id.editor_chan_sat_red:
                return FilterChanSatRepresentation.MODE_RED;
            case R.id.editor_chan_sat_yellow:
                return FilterChanSatRepresentation.MODE_YELLOW;
            case R.id.editor_chan_sat_green:
                return FilterChanSatRepresentation.MODE_GREEN;
            case R.id.editor_chan_sat_cyan:
                return FilterChanSatRepresentation.MODE_CYAN;
            case R.id.editor_chan_sat_blue:
                return FilterChanSatRepresentation.MODE_BLUE;
            case R.id.editor_chan_sat_magenta:
                return FilterChanSatRepresentation.MODE_MAGENTA;
        }
        return -1;
    }

    @Override
    public void detach() {
        mButton.setListener(null);
        mButton.setOnClickListener(null);
    }

    private void updateSeekBar(FilterChanSatRepresentation rep) {
        mControl.updateUI();
    }

    @Override
    protected Parameter getParameterToEdit(FilterRepresentation rep) {
        if (rep instanceof FilterChanSatRepresentation) {
            FilterChanSatRepresentation csrep = (FilterChanSatRepresentation) rep;
            Parameter param = csrep.getFilterParameter(csrep.getParameterMode());
            if (param instanceof BasicParameterStyle) {
                param.setFilterView(EditorChanSat.this);
            }
            return param;
        }
        return null;
    }

    private FilterChanSatRepresentation getChanSatRep() {
        FilterRepresentation rep = getLocalRepresentation();
        if (rep != null
                && rep instanceof FilterChanSatRepresentation) {
            FilterChanSatRepresentation csrep = (FilterChanSatRepresentation) rep;
            return csrep;
        }
        return null;
    }

    @Override
    public void computeIcon(int n, RenderingRequestCaller caller) {
        FilterChanSatRepresentation rep = getChanSatRep();
        if (rep == null) return;
        rep = (FilterChanSatRepresentation) rep.copy();
        ImagePreset preset = new ImagePreset();
        preset.addFilter(rep);
        Bitmap src = MasterImage.getImage().getThumbnailBitmap();
        RenderingRequest.post(null, src, preset, RenderingRequest.STYLE_ICON_RENDERING,
                caller);
    }

    protected void selectMenuItem(MenuItem item) {
        if (getLocalRepresentation() != null
                && getLocalRepresentation() instanceof FilterChanSatRepresentation) {
            FilterChanSatRepresentation csrep =
                    (FilterChanSatRepresentation) getLocalRepresentation();

            switchToMode(csrep, getParameterIndex(item.getItemId()), item.getTitle().toString());

        }
    }

    protected void switchToMode(FilterChanSatRepresentation csrep, int mode, String title) {
        csrep.setParameterMode(mode);
        mCurrentlyEditing = title;
        mButton.setText(mCurrentlyEditing);
        {
            Parameter param = getParameterToEdit(csrep);

            control(param, mEditControl);
        }
        updateSeekBar(csrep);
        mView.invalidate();
    }

    @Override
    public void swapLeft(MenuItem item) {
        super.swapLeft(item);
        mButton.setTranslationX(0);
        mButton.animate().translationX(mButton.getWidth()).setDuration(SwapButton.ANIM_DURATION);
        Runnable updateButton = new Runnable() {
            @Override
            public void run() {
                mButton.animate().cancel();
                mButton.setTranslationX(0);
            }
        };
        mHandler.postDelayed(updateButton, SwapButton.ANIM_DURATION);
        selectMenuItem(item);
    }

    @Override
    public void swapRight(MenuItem item) {
        super.swapRight(item);
        mButton.setTranslationX(0);
        mButton.animate().translationX(-mButton.getWidth()).setDuration(SwapButton.ANIM_DURATION);
        Runnable updateButton = new Runnable() {
            @Override
            public void run() {
                mButton.animate().cancel();
                mButton.setTranslationX(0);
            }
        };
        mHandler.postDelayed(updateButton, SwapButton.ANIM_DURATION);
        selectMenuItem(item);
    }
}
