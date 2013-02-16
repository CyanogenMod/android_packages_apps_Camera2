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
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewPropertyAnimator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterTinyPlanet;
import com.android.gallery3d.filtershow.imageshow.ImageCrop;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.FilterIconButton;

import java.util.HashMap;
import java.util.Vector;

public class PanelController implements OnClickListener {
    private static int PANEL = 0;
    private static int COMPONENT = 1;
    private static int VERTICAL_MOVE = 0;
    private static int HORIZONTAL_MOVE = 1;
    private static final int ANIM_DURATION = 200;
    private static final String LOGTAG = "PanelController";
    private boolean mDisableFilterButtons = false;
    private boolean mFixedAspect = false;

    public void setFixedAspect(boolean t) {
        mFixedAspect = t;
    }

    class Panel {
        private final View mView;
        private final View mContainer;
        private int mPosition = 0;
        private final Vector<View> mSubviews = new Vector<View>();

        public Panel(View view, View container, int position) {
            mView = view;
            mContainer = container;
            mPosition = position;
        }

        public void addView(View view) {
            mSubviews.add(view);
        }

        public int getPosition() {
            return mPosition;
        }

        public ViewPropertyAnimator unselect(int newPos, int move) {
            ViewPropertyAnimator anim = mContainer.animate();
            mView.setSelected(false);
            mContainer.setX(0);
            mContainer.setY(0);
            int delta = 0;
            int w = mRowPanel.getWidth();
            int h = mRowPanel.getHeight();
            if (move == HORIZONTAL_MOVE) {
                if (newPos > mPosition) {
                    delta = -w;
                } else {
                    delta = w;
                }
                anim.x(delta);
            } else if (move == VERTICAL_MOVE) {
                anim.y(h);
            }
            anim.setDuration(ANIM_DURATION).withLayer().withEndAction(new Runnable() {
                @Override
                public void run() {
                    mContainer.setVisibility(View.GONE);
                }
            });
            return anim;
        }

        public ViewPropertyAnimator select(int oldPos, int move) {
            mView.setSelected(true);
            mContainer.setVisibility(View.VISIBLE);
            mContainer.setX(0);
            mContainer.setY(0);
            ViewPropertyAnimator anim = mContainer.animate();
            int w = mRowPanel.getWidth();
            int h = mRowPanel.getHeight();
            if (move == HORIZONTAL_MOVE) {
                if (oldPos < mPosition) {
                    mContainer.setX(w);
                } else {
                    mContainer.setX(-w);
                }
                anim.x(0);
            } else if (move == VERTICAL_MOVE) {
                mContainer.setY(h);
                anim.y(0);
            }
            anim.setDuration(ANIM_DURATION).withLayer();
            return anim;
        }
    }

    class UtilityPanel {
        private final Context mContext;
        private final View mView;
        private final LinearLayout mAccessoryViewList;
        private Vector<View> mAccessoryViews = new Vector<View>();
        private final TextView mTextView;
        private boolean mSelected = false;
        private String mEffectName = null;
        private int mParameterValue = 0;
        private boolean mShowParameterValue = false;
        boolean firstTimeCropDisplayed = true;

        public UtilityPanel(Context context, View view, View accessoryViewList,
                View textView) {
            mContext = context;
            mView = view;
            mAccessoryViewList = (LinearLayout) accessoryViewList;
            mTextView = (TextView) textView;
        }

        public boolean selected() {
            return mSelected;
        }

        public void hideAccessoryViews() {
            int childCount = mAccessoryViewList.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = mAccessoryViewList.getChildAt(i);
                child.setVisibility(View.GONE);
            }
        }

        public void onNewValue(int value) {
            mParameterValue = value;
            updateText();
        }

        public void setEffectName(String effectName) {
            mEffectName = effectName;
            setShowParameter(true);
        }

        public void setShowParameter(boolean s) {
            mShowParameterValue = s;
            updateText();
        }

        public void updateText() {
            String apply = mContext.getString(R.string.apply_effect);
            if (mShowParameterValue) {
                mTextView.setText(Html.fromHtml(apply + " " + mEffectName + " "
                        + mParameterValue));
            } else {
                mTextView.setText(Html.fromHtml(apply + " " + mEffectName));
            }
        }

        public ViewPropertyAnimator unselect() {
            ViewPropertyAnimator anim = mView.animate();
            mView.setX(0);
            mView.setY(0);
            int h = mRowPanel.getHeight();
            anim.y(-h);
            anim.setDuration(ANIM_DURATION).withLayer().withEndAction(new Runnable() {
                @Override
                public void run() {
                    mView.setVisibility(View.GONE);
                }
            });
            mSelected = false;
            return anim;
        }

        public ViewPropertyAnimator select() {
            mView.setVisibility(View.VISIBLE);
            int h = mRowPanel.getHeight();
            mView.setX(0);
            mView.setY(-h);
            updateText();
            ViewPropertyAnimator anim = mView.animate();
            anim.y(0);
            anim.setDuration(ANIM_DURATION).withLayer();
            mSelected = true;
            return anim;
        }

    }

    class ViewType {
        private final int mType;
        private final View mView;

        public ViewType(View view, int type) {
            mView = view;
            mType = type;
        }

        public int type() {
            return mType;
        }
    }

    private final HashMap<View, Panel> mPanels = new HashMap<View, Panel>();
    private final HashMap<View, ViewType> mViews = new HashMap<View, ViewType>();
    private final HashMap<String, ImageFilter> mFilters = new HashMap<String, ImageFilter>();
    private final Vector<View> mImageViews = new Vector<View>();
    private View mCurrentPanel = null;
    private View mRowPanel = null;
    private UtilityPanel mUtilityPanel = null;
    private ImageShow mCurrentImage = null;
    private Editor mCurrentEditor = null;
    private FilterShowActivity mActivity = null;
    private EditorPlaceHolder mEditorPlaceHolder = null;

    public void setActivity(FilterShowActivity activity) {
        mActivity = activity;
    }

    public void addView(View view) {
        view.setOnClickListener(this);
        mViews.put(view, new ViewType(view, COMPONENT));
    }

    public void addPanel(View view, View container, int position) {
        mPanels.put(view, new Panel(view, container, position));
        view.setOnClickListener(this);
        mViews.put(view, new ViewType(view, PANEL));
    }

    public void addComponent(View aPanel, View component) {
        Panel panel = mPanels.get(aPanel);
        if (panel == null) {
            return;
        }
        panel.addView(component);
        component.setOnClickListener(this);
        mViews.put(component, new ViewType(component, COMPONENT));
    }

    public void addFilter(ImageFilter filter) {
        mFilters.put(filter.getName(), filter);
    }

    public void addImageView(View view) {
        mImageViews.add(view);
        ImageShow imageShow = (ImageShow) view;
        imageShow.setPanelController(this);
    }

    public void resetParameters() {
        showPanel(mCurrentPanel);
        if (mCurrentImage != null) {
            mCurrentImage.resetParameter();
            mCurrentImage.select();
            if (mCurrentEditor != null) {
                mCurrentEditor.reflectCurrentFilter();
            }

        }
        if (mDisableFilterButtons) {
            mActivity.enableFilterButtons();
            mDisableFilterButtons = false;
        }
    }

    public boolean onBackPressed() {
        if (mUtilityPanel == null || !mUtilityPanel.selected()) {
            return true;
        }
        HistoryAdapter adapter = MasterImage.getImage().getHistory();
        int position = adapter.undo();
        MasterImage.getImage().onHistoryItemClick(position);
        showPanel(mCurrentPanel);
        mCurrentImage.select();
        if (mCurrentEditor != null) {
            mCurrentEditor.reflectCurrentFilter();
        }

        if (mDisableFilterButtons) {
            mActivity.enableFilterButtons();
            mActivity.resetHistory();
            mDisableFilterButtons = false;
        }
        return false;
    }

    public void onNewValue(int value) {
        mUtilityPanel.onNewValue(value);
    }

    public void showParameter(boolean s) {
        mUtilityPanel.setShowParameter(s);
    }

    public void setCurrentPanel(View panel) {
        showPanel(panel);
    }

    public void setRowPanel(View rowPanel) {
        mRowPanel = rowPanel;
    }

    public void setUtilityPanel(Context context, View utilityPanel,
            View accessoryViewList, View textView) {
        mUtilityPanel = new UtilityPanel(context, utilityPanel,
                accessoryViewList, textView);
    }

    @Override
    public void onClick(View view) {
        ViewType type = mViews.get(view);
        if (type.type() == PANEL) {
            showPanel(view);
        } else if (type.type() == COMPONENT) {
            showComponent(view);
        }
    }

    public ImageShow showImageView(int id) {
        ImageShow image = null;
        mActivity.hideImageViews();
        for (View view : mImageViews) {
            if (view.getId() == id) {
                view.setVisibility(View.VISIBLE);
                image = (ImageShow) view;
            } else {
                view.setVisibility(View.GONE);
            }
        }
        return image;
    }

    public void showDefaultImageView() {
        showImageView(R.id.imageShow).setShowControls(false);
        MasterImage.getImage().setCurrentFilter(null);
        MasterImage.getImage().setCurrentFilterRepresentation(null);
    }

    public void showPanel(View view) {
        view.setVisibility(View.VISIBLE);
        boolean removedUtilityPanel = false;
        Panel current = mPanels.get(mCurrentPanel);
        if (mUtilityPanel != null && mUtilityPanel.selected()) {
            ViewPropertyAnimator anim1 = mUtilityPanel.unselect();
            removedUtilityPanel = true;
            anim1.start();
            if (mCurrentPanel == view) {
                ViewPropertyAnimator anim2 = current.select(-1, VERTICAL_MOVE);
                anim2.start();
                showDefaultImageView();
            }
        }

        if (mCurrentPanel == view) {
            return;
        }

        Panel panel = mPanels.get(view);
        if (!removedUtilityPanel) {
            int currentPos = -1;
            if (current != null) {
                currentPos = current.getPosition();
            }
            ViewPropertyAnimator anim1 = panel.select(currentPos, HORIZONTAL_MOVE);
            anim1.start();
            if (current != null) {
                ViewPropertyAnimator anim2 = current.unselect(panel.getPosition(), HORIZONTAL_MOVE);
                anim2.start();
            }
        } else {
            ViewPropertyAnimator anim = panel.select(-1, VERTICAL_MOVE);
            anim.start();
        }
        showDefaultImageView();
        mCurrentPanel = view;
    }

    public ImagePreset getImagePreset() {
        return MasterImage.getImage().getPreset();
    }

    /**
    public ImageFilter setImagePreset(ImageFilter filter, String name) {
        ImagePreset copy = new ImagePreset(getImagePreset());
        copy.add(filter);
        copy.setHistoryName(name);
        copy.setIsFx(false);
        mMasterImage.setPreset(copy, true);
        return filter;
    }
     */

    // TODO: remove this.
    public void ensureFilter(String name) {
        /*
        ImagePreset preset = getImagePreset();
        ImageFilter filter = preset.getFilter(name);
        if (filter != null) {
            // If we already have a filter, we might still want
            // to push it onto the history stack.
            ImagePreset copy = new ImagePreset(getImagePreset());
            copy.setHistoryName(name);
            mMasterImage.setPreset(copy, true);
            filter = copy.getFilter(name);
        }

        if (filter == null) {
            ImageFilter filterInstance = mFilters.get(name);
            if (filterInstance != null) {
                try {
                    ImageFilter newFilter = filterInstance.clone();
                    newFilter.reset();
                    filter = setImagePreset(newFilter, name);
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (filter != null) {
            mMasterImage.setCurrentFilter(filter);
        }
        */
    }

    public void useFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        if (MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            return;
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        FilterRepresentation representation = copy.getRepresentation(filterRepresentation);
        if (representation == null) {
            copy.addFilter(filterRepresentation);
        } else {
            if (filterRepresentation.allowsMultipleInstances()) {
                representation.useParametersFrom(filterRepresentation);
                copy.setHistoryName(filterRepresentation.getName());
            }
            filterRepresentation = representation;
        }
        MasterImage.getImage().setPreset(copy, true);
        MasterImage.getImage().setCurrentFilterRepresentation(filterRepresentation);
    }

    public void showComponent(View view) {

        boolean doPanelTransition = true;
        if (view instanceof FilterIconButton) {
            FilterRepresentation f = ((FilterIconButton) view).getFilterRepresentation();
            if (f != null) {
                // FIXME: this check shouldn't be necessary (f shouldn't be null)
                doPanelTransition = f.showUtilityPanel();
            }
        }

        if (mUtilityPanel != null && !mUtilityPanel.selected() && doPanelTransition ) {
            Panel current = mPanels.get(mCurrentPanel);
            ViewPropertyAnimator anim1 = current.unselect(-1, VERTICAL_MOVE);
            anim1.start();
            if (mUtilityPanel != null) {
                ViewPropertyAnimator anim2 = mUtilityPanel.select();
                anim2.start();
            }
        }

        if (mCurrentImage != null) {
            mCurrentImage.unselect();
        }
        mUtilityPanel.hideAccessoryViews();

        if (view instanceof FilterIconButton) {
            mCurrentEditor = null;
            FilterIconButton component = (FilterIconButton) view;
            FilterRepresentation representation = component.getFilterRepresentation();
            if (representation != null) {
                mUtilityPanel.setEffectName(representation.getName());
                mUtilityPanel.setShowParameter(representation.showParameterValue());

                if (representation.getEditorId() != 0) {
                    if (mEditorPlaceHolder.contains(representation.getEditorId())) {
                        mCurrentEditor = mEditorPlaceHolder.showEditor(representation.getEditorId());
                        mCurrentImage = mCurrentEditor.getImageShow();
                        mCurrentEditor.setPanelController(this);
                    } else {
                        mCurrentImage = showImageView(representation.getEditorId());
                    }
                }
                mCurrentImage.setShowControls(representation.showEditingControls());
                mUtilityPanel.setShowParameter(representation.showParameterValue());

                mCurrentImage.select();
                if (mCurrentEditor != null) {
                    mCurrentEditor.reflectCurrentFilter();
                    if (mCurrentEditor.useUtilityPanel()) {
                        mCurrentEditor.openUtilityPanel(mUtilityPanel.mAccessoryViewList);
                    }
                } else if (mCurrentImage.useUtilityPanel()) {
                    mCurrentImage.openUtilityPanel(mUtilityPanel.mAccessoryViewList);
                }
            }
            return;
        }

        switch (view.getId()) {
            case R.id.tinyplanetButton: {
                mCurrentImage = showImageView(R.id.imageTinyPlanet).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.tinyplanet);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                if (!mDisableFilterButtons) {
                    mActivity.disableFilterButtons();
                    mDisableFilterButtons = true;
                }
                break;
            }
            case R.id.straightenButton: {
                mCurrentImage = showImageView(R.id.imageStraighten);
                String ename = mCurrentImage.getContext().getString(R.string.straighten);
                mUtilityPanel.setEffectName(ename);
                break;
            }
            case R.id.cropButton: {
                mCurrentImage = showImageView(R.id.imageCrop);
                String ename = mCurrentImage.getContext().getString(R.string.crop);
                mUtilityPanel.setEffectName(ename);
                mUtilityPanel.setShowParameter(false);
                if (mCurrentImage instanceof ImageCrop && mUtilityPanel.firstTimeCropDisplayed) {
                    ((ImageCrop) mCurrentImage).clear();
                    mUtilityPanel.firstTimeCropDisplayed = false;
                }
                ((ImageCrop) mCurrentImage).setFixedAspect(mFixedAspect);
                break;
            }
            case R.id.rotateButton: {
                mCurrentImage = showImageView(R.id.imageRotate);
                String ename = mCurrentImage.getContext().getString(R.string.rotate);
                mUtilityPanel.setEffectName(ename);
                break;
            }
            case R.id.flipButton: {
                mCurrentImage = showImageView(R.id.imageFlip);
                String ename = mCurrentImage.getContext().getString(R.string.mirror);
                mUtilityPanel.setEffectName(ename);
                mUtilityPanel.setShowParameter(false);
                break;
            }
            case R.id.redEyeButton: {
                mCurrentImage = showImageView(R.id.imageRedEyes).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.redeye);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.applyEffect: {
                if (MasterImage.getImage().getCurrentFilter() instanceof ImageFilterTinyPlanet) {
                    mActivity.saveImage();
                } else {
                    if (mCurrentImage instanceof ImageCrop) {
                        ((ImageCrop) mCurrentImage).saveAndSetPreset();
                    }
                    showPanel(mCurrentPanel);
                }
                MasterImage.getImage().invalidateFiltersOnly();
                break;
            }
        }
        mCurrentImage.select();
        if (mCurrentEditor != null) {
            mCurrentEditor.reflectCurrentFilter();
            if (mCurrentEditor.useUtilityPanel()) {
                mCurrentEditor.openUtilityPanel(mUtilityPanel.mAccessoryViewList);
            }
        } else if (mCurrentImage.useUtilityPanel()) {
            mCurrentImage.openUtilityPanel(mUtilityPanel.mAccessoryViewList);
        }

    }

    public void setEditorPlaceHolder(EditorPlaceHolder editorPlaceHolder) {
        mEditorPlaceHolder = editorPlaceHolder;
    }
}
