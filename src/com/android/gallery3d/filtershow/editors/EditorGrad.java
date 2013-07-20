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
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ToggleButton;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.controller.Control;
import com.android.gallery3d.filtershow.controller.FilterView;
import com.android.gallery3d.filtershow.controller.Parameter;
import com.android.gallery3d.filtershow.controller.ParameterActionAndInt;
import com.android.gallery3d.filtershow.filters.FilterGradRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.imageshow.ImageGrad;
import com.android.gallery3d.filtershow.imageshow.MasterImage;

public class EditorGrad extends ParametricEditor
        implements OnSeekBarChangeListener, ParameterActionAndInt {
    private static final String LOGTAG = "EditorGrad";
    public static final int ID = R.id.editorGrad;
    PopupMenu mPopupMenu;
    ToggleButton mAddModeButton;
    String mEffectName = "";
    private static final int MODE_BRIGHTNESS = FilterGradRepresentation.PARAM_BRIGHTNESS;
    private static final int MODE_SATURATION = FilterGradRepresentation.PARAM_SATURATION;
    private static final int MODE_CONTRAST = FilterGradRepresentation.PARAM_CONTRAST;
    private static final int ADD_ICON = R.drawable.ic_grad_add;
    private static final int DEL_ICON = R.drawable.ic_grad_del;
    private int mSliderMode = MODE_BRIGHTNESS;
    ImageGrad mImageGrad;

    public EditorGrad() {
        super(ID, R.layout.filtershow_grad_editor, R.id.gradEditor);
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        mImageGrad = (ImageGrad) mImageShow;
        mImageGrad.setEditor(this);

    }

    public void clearAddMode() {
        mAddModeButton.setChecked(false);
        FilterRepresentation tmpRep = getLocalRepresentation();
        if (tmpRep instanceof FilterGradRepresentation) {
            updateMenuItems((FilterGradRepresentation) tmpRep);
        }
    }

    @Override
    public void reflectCurrentFilter() {
        super.reflectCurrentFilter();
        FilterRepresentation tmpRep = getLocalRepresentation();
        if (tmpRep instanceof FilterGradRepresentation) {
            FilterGradRepresentation rep = (FilterGradRepresentation) tmpRep;
            boolean f = rep.showParameterValue();

            mImageGrad.setRepresentation(rep);
        }
    }

    public void updateSeekBar(FilterGradRepresentation rep) {
        mControl.updateUI();
    }

    @Override
    public void onProgressChanged(SeekBar sbar, int progress, boolean arg2) {
        FilterRepresentation tmpRep = getLocalRepresentation();
        if (tmpRep instanceof FilterGradRepresentation) {
            FilterGradRepresentation rep = (FilterGradRepresentation) tmpRep;
            int min = rep.getParameterMin(mSliderMode);
            int value = progress + min;
            rep.setParameter(mSliderMode, value);
            mView.invalidate();
            commitLocalRepresentation();
        }
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        Button view = (Button) accessoryViewList.findViewById(R.id.applyEffect);
        view.setText(mContext.getString(R.string.editor_grad_brightness));
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showPopupMenu(accessoryViewList);
            }
        });

        setUpPopupMenu(view);
        setEffectName();
    }

    private void updateMenuItems(FilterGradRepresentation rep) {
        int n = rep.getNumberOfBands();
    }

    public void setEffectName() {
        if (mPopupMenu != null) {
            MenuItem item = mPopupMenu.getMenu().findItem(R.id.editor_grad_brightness);
            mEffectName = item.getTitle().toString();
        }
    }

    private void showPopupMenu(LinearLayout accessoryViewList) {
        Button button = (Button) accessoryViewList.findViewById(R.id.applyEffect);
        if (button == null) {
            return;
        }

        if (mPopupMenu == null) {
            setUpPopupMenu(button);
        }
        mPopupMenu.show();
    }

    private void setUpPopupMenu(Button button) {
        mPopupMenu = new PopupMenu(mImageShow.getActivity(), button);
        mPopupMenu.getMenuInflater()
                .inflate(R.menu.filtershow_menu_grad, mPopupMenu.getMenu());
        FilterGradRepresentation rep = (FilterGradRepresentation) getLocalRepresentation();
        if (rep == null) {
            return;
        }
        updateMenuItems(rep);
        hackFixStrings(mPopupMenu.getMenu());
        setEffectName();
        updateText();

        mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                FilterRepresentation tmpRep = getLocalRepresentation();

                if (tmpRep instanceof FilterGradRepresentation) {
                    FilterGradRepresentation rep = (FilterGradRepresentation) tmpRep;
                    int cmdID = item.getItemId();
                    switch (cmdID) {
                        case R.id.editor_grad_brightness:
                            mSliderMode = MODE_BRIGHTNESS;
                            mEffectName = item.getTitle().toString();
                            break;
                        case R.id.editor_grad_contrast:
                            mSliderMode = MODE_CONTRAST;
                            mEffectName = item.getTitle().toString();
                            break;
                        case R.id.editor_grad_saturation:
                            mSliderMode = MODE_SATURATION;
                            mEffectName = item.getTitle().toString();
                            break;
                    }
                    updateMenuItems(rep);
                    updateSeekBar(rep);

                    commitLocalRepresentation();
                    mView.invalidate();
                }
                return true;
            }
        });
    }

    @Override
    public String calculateUserMessage(Context context, String effectName, Object parameterValue) {
        FilterGradRepresentation rep = getGradRepresentation();
        if (rep == null) {
            return mEffectName;
        }
        int val = rep.getParameter(mSliderMode);
        return mEffectName.toUpperCase() + ((val > 0) ? " +" : " ") + val;
    }

    private FilterGradRepresentation getGradRepresentation() {
        FilterRepresentation tmpRep = getLocalRepresentation();
        if (tmpRep instanceof FilterGradRepresentation) {
            return (FilterGradRepresentation) tmpRep;
        }
        return null;
    }

    @Override
    public int getMaximum() {
        FilterGradRepresentation rep = getGradRepresentation();
        if (rep == null) {
            return 0;
        }
        return rep.getParameterMax(mSliderMode);
    }

    @Override
    public int getMinimum() {
        FilterGradRepresentation rep = getGradRepresentation();
        if (rep == null) {
            return 0;
        }
        return rep.getParameterMin(mSliderMode);
    }

    @Override
    public int getDefaultValue() {
        return 0;
    }

    @Override
    public int getValue() {
        FilterGradRepresentation rep = getGradRepresentation();
        if (rep == null) {
            return 0;
        }
        return rep.getParameter(mSliderMode);
    }

    @Override
    public String getValueString() {
        return null;
    }

    @Override
    public void setValue(int value) {
        FilterGradRepresentation rep = getGradRepresentation();
        if (rep == null) {
            return;
        }
        rep.setParameter(mSliderMode, value);
    }

    @Override
    public String getParameterName() {
        return mEffectName;
    }

    @Override
    public String getParameterType() {
        return sParameterType;
    }

    @Override
    public void setController(Control c) {

    }

    @Override
    public void fireLeftAction() {
        FilterGradRepresentation rep = getGradRepresentation();
        if (rep == null) {
            return;
        }
        rep.addBand(MasterImage.getImage().getOriginalBounds());
        updateMenuItems(rep);
        updateSeekBar(rep);

        commitLocalRepresentation();
        mView.invalidate();
    }

    @Override
    public int getLeftIcon() {
        return ADD_ICON;
    }

    @Override
    public void fireRightAction() {
        FilterGradRepresentation rep = getGradRepresentation();
        if (rep == null) {
            return;
        }
        rep.deleteCurrentBand();

        updateMenuItems(rep);
        updateSeekBar(rep);
        commitLocalRepresentation();
        mView.invalidate();
    }

    @Override
    public int getRightIcon() {
        return DEL_ICON;
    }

    @Override
    public void setFilterView(FilterView editor) {

    }

    @Override
    public void copyFrom(Parameter src) {

    }

}
