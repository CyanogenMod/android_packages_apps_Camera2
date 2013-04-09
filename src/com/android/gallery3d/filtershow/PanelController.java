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
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewPropertyAnimator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.Editor;
import com.android.gallery3d.filtershow.editors.EditorTinyPlanet;
import com.android.gallery3d.filtershow.editors.ImageOnlyEditor;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterTinyPlanet;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.FilterIconButton;

import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

public class PanelController implements OnClickListener {
    private static int PANEL = 0;
    private static int COMPONENT = 1;
    private static int VERTICAL_MOVE = 0;
    private static int HORIZONTAL_MOVE = 1;
    private static final int ANIM_DURATION = 200;
    private static final String LOGTAG = "PanelController";
    private boolean mFixedAspect = false;

    final Handler mHandler = new Handler();

    public static boolean useAnimationsLayer() {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            return true;
        }
        return false;
    }

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
            anim.setDuration(ANIM_DURATION);
            Runnable action = new Runnable() {
                @Override
                public void run() {
                    mContainer.setVisibility(View.GONE);
                }
            };
            if (PanelController.useAnimationsLayer()) {
                anim.withLayer().withEndAction(action);
            } else {
                mHandler.postDelayed(action, ANIM_DURATION);
            }
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
            anim.setDuration(ANIM_DURATION);
            if (PanelController.useAnimationsLayer()) {
                anim.withLayer();
            }
            return anim;
        }
    }

    class UtilityPanel {
        private final Context mContext;
        private final View mView;
        private final LinearLayout mAccessoryViewList;
        private Vector<View> mAccessoryViews = new Vector<View>();
        private final Button mTextView;
        private boolean mSelected = false;
        private String mEffectName = null;
        private int mParameterValue = 0;
        private boolean mShowParameterValue = false;

        public UtilityPanel(Context context, View utilityPanel) {
            mView = utilityPanel;
            View accessoryViewList = mView.findViewById(R.id.panelAccessoryViewList);
            mTextView = (Button) mView.findViewById(R.id.applyEffect);
            mContext = context;
            mAccessoryViewList = (LinearLayout) accessoryViewList;
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

        public void showMenu(boolean show) {
            mTextView.setOnClickListener(null);
            if (show){
                mAccessoryViewList.setVisibility(View.VISIBLE);
                mTextView.setVisibility(View.VISIBLE);
            } else {
                mAccessoryViewList.setVisibility(View.VISIBLE);
                mTextView.setVisibility(View.VISIBLE);
            }

        }

        public View getActionControl() {
            return mView.findViewById(R.id.panelAccessoryViewList);
        }

        public View getEditControl() {
            return mView.findViewById(R.id.controlArea);
        }

        public void removeControlChildren() {
            LinearLayout controlArea = (LinearLayout) mView.findViewById(R.id.controlArea);
            controlArea.removeAllViews();
        }

        public Button getEditTitle() {
            return mTextView;
        }

        public void updateText() {
            String s;
            if (mCurrentEditor == null) {
                String apply = mContext.getString(R.string.apply_effect);
                s = apply + " " + mEffectName + " " + mParameterValue;
            } else {
                s = mCurrentEditor.calculateUserMessage(mContext, mEffectName, mParameterValue);
            }
            mTextView.setText(Html.fromHtml(s));
        }

        public ViewPropertyAnimator unselect() {
            ViewPropertyAnimator anim = mView.animate();
            mView.setX(0);
            mView.setY(0);
            int h = mRowPanel.getHeight();
            anim.y(-h);
            Runnable action = new Runnable() {
                @Override
                public void run() {
                    mView.setVisibility(View.GONE);
                }
            };
            if (PanelController.useAnimationsLayer()) {
                anim.setDuration(ANIM_DURATION).withLayer().withEndAction(action);
            } else {
                mHandler.postDelayed(action, ANIM_DURATION);
            }
            mSelected = false;
            return anim;
        }

        public ViewPropertyAnimator select() {
            mView.setVisibility(View.VISIBLE);
            int h = mRowPanel.getHeight();
            mView.setX(0);
            mView.setY(-h);
            updateText();
            mSelected = true;
            ViewPropertyAnimator anim = mView.animate();
            anim.y(0);
            anim.setDuration(ANIM_DURATION);
            if (PanelController.useAnimationsLayer()) {
                anim.withLayer();
            }
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

    public void clear() {
        mPanels.clear();
        mViews.clear();
        mFilters.clear();
        mImageViews.clear();
    }

    public void setActivity(FilterShowActivity activity) {
        mActivity = activity;
    }

    public void addView(View view) {
        view.setOnClickListener(this);
        mViews.put(view, new ViewType(view, COMPONENT));
    }

    public View getViewFromId(int viewId) {
        for (View view : mPanels.keySet()) {
            if (view.getId() == viewId) {
                return view;
            }
        }
        return null;
    }

    public void addPanel(int viewId, int containerId, int position) {
        View view = mActivity.findViewById(viewId);
        View container = mActivity.findViewById(containerId);
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
    }

    public boolean onBackPressed() {
        if (mUtilityPanel == null || !mUtilityPanel.selected()) {
            return true;
        }
        HistoryAdapter adapter = MasterImage.getImage().getHistory();
        int position = adapter.undo();
        MasterImage.getImage().onHistoryItemClick(position);
        mActivity.showCategoryPanel();
        showPanel(mCurrentPanel);
        mCurrentImage.select();
        if (mCurrentEditor != null) {
            mCurrentEditor.reflectCurrentFilter();
        }
        return false;
    }

    public void onNewValue(int value) {
        mUtilityPanel.onNewValue(value);
    }

    public void showParameter(boolean s) {
        mUtilityPanel.setShowParameter(s);
    }

    public void setCurrentPanel(int panelId) {
        showPanel(getViewFromId(panelId));
    }

    public void setRowPanel(View rowPanel) {
        mRowPanel = rowPanel;
    }

    public void setUtilityPanel(Context context, View utilityPanel) {
        addView(utilityPanel.findViewById(R.id.applyEffect));
        addView(utilityPanel.findViewById(R.id.applyFilter));
        // TODO rename applyFilter to panelFilterDescription
        addView(utilityPanel.findViewById(R.id.cancelFilter));
        mUtilityPanel = new UtilityPanel(context, utilityPanel);
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
            image = (ImageShow) view;
            if (view.getId() == id) {
                view.setVisibility(View.VISIBLE);
                image.select();
            } else {
                view.setVisibility(View.GONE);
                image.unselect();
            }
        }
        return image;
    }

    public void showDefaultImageView() {
        showImageView(R.id.imageShow);
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
            if (anim1 != null) {
                anim1.start();
            }
            if (mCurrentPanel == view) {
                ViewPropertyAnimator anim2 = current.select(-1, VERTICAL_MOVE);
                if (anim2 != null) {
                    anim2.start();
                }
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
            if (anim1 != null) {
                anim1.start();
            }
            if (current != null) {
                ViewPropertyAnimator anim2 = current.unselect(panel.getPosition(), HORIZONTAL_MOVE);
                if (anim2 != null) {
                    anim2.start();
                }
            }
        } else {
            ViewPropertyAnimator anim = panel.select(-1, VERTICAL_MOVE);
            if (anim != null) {
                anim.start();
            }
        }

        showDefaultImageView();
        mCurrentPanel = view;
    }

    public ImagePreset getImagePreset() {
        return MasterImage.getImage().getPreset();
    }

    public void setEffectName(String ename) {
        mUtilityPanel.setEffectName(ename);
    }

    public void removeFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            Log.v(LOGTAG, "RemoveFilterRepresentation: " + filterRepresentation);
            return;
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        copy.removeFilter(filterRepresentation);
        MasterImage.getImage().setPreset(copy, true);
        if (MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            FilterRepresentation lastRepresentation = copy.getLastRepresentation();
            MasterImage.getImage().setCurrentFilterRepresentation(lastRepresentation);
        }
        // Now let's reset the panel
        if (mUtilityPanel == null || !mUtilityPanel.selected()) {
            return;
        }
        showPanel(mCurrentPanel);
        mCurrentImage.select();
        if (mCurrentEditor != null) {
            mCurrentEditor.reflectCurrentFilter();
        }
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
                representation.updateTempParametersFrom(filterRepresentation);
                copy.setHistoryName(filterRepresentation.getName());
                representation.synchronizeRepresentation();
            }
            filterRepresentation = representation;
        }
        MasterImage.getImage().setPreset(copy, true);
        MasterImage.getImage().setCurrentFilterRepresentation(filterRepresentation);
    }

    public void showComponentWithRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        Set<View> views = mViews.keySet();
        for (View view : views) {
            if (view instanceof FilterIconButton) {
                FilterIconButton button = (FilterIconButton) view;
                if (button.getFilterRepresentation().getFilterClass() == filterRepresentation.getFilterClass()) {
                    MasterImage.getImage().setCurrentFilterRepresentation(filterRepresentation);
                    showComponent(view);
                    return;
                }
            }
        }
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

        if (mUtilityPanel != null && !mUtilityPanel.selected() && doPanelTransition) {
            Panel current = mPanels.get(mCurrentPanel);
            ViewPropertyAnimator anim1 = current.unselect(-1, VERTICAL_MOVE);
            if (anim1 != null) {
                anim1.start();
            }
            if (mUtilityPanel != null) {
                ViewPropertyAnimator anim2 = mUtilityPanel.select();
                if (anim2 != null) {
                    anim2.start();
                }
            }
        }

        if (mCurrentImage != null) {
            mCurrentImage.unselect();
        }
        mUtilityPanel.hideAccessoryViews();
        mUtilityPanel.showMenu(false);

        if (view instanceof FilterIconButton) {
            mCurrentEditor = null;
            FilterIconButton component = (FilterIconButton) view;
            FilterRepresentation representation = component.getFilterRepresentation();
            if (representation != null) {
                mUtilityPanel.setEffectName(representation.getName());
                mUtilityPanel.setShowParameter(representation.showParameterValue());

                if (representation.getEditorId() != 0) {
                    if (representation.getEditorId() != ImageOnlyEditor.ID) {
                        mActivity.hideCategoryPanel();
                    }
                    if (mEditorPlaceHolder.contains(representation.getEditorId())) {
                        mCurrentEditor = mEditorPlaceHolder.showEditor(
                                representation.getEditorId());
                        mUtilityPanel.removeControlChildren();
                        mCurrentEditor.setUpEditorUI(
                                mUtilityPanel.getActionControl(), mUtilityPanel.getEditControl(),
                                mUtilityPanel.getEditTitle());
                        mCurrentImage = mCurrentEditor.getImageShow();
                        mCurrentEditor.setPanelController(this);

                    } else {
                        mCurrentImage = showImageView(representation.getEditorId());
                    }
                }
                mUtilityPanel.setShowParameter(representation.showParameterValue());

                mCurrentImage.select();
                if (mCurrentEditor != null) {
                    mCurrentEditor.reflectCurrentFilter();
                    if (mCurrentEditor.useUtilityPanel()) {
                        mUtilityPanel.showMenu(true);
                        mCurrentEditor.openUtilityPanel(mUtilityPanel.mAccessoryViewList);
                    }
                } else if (mCurrentImage.useUtilityPanel()) {
                    mCurrentImage.openUtilityPanel(mUtilityPanel.mAccessoryViewList);
                }
            }
            return;
        }

        mActivity.showCategoryPanel();
        int id = view.getId();
        if (id == EditorTinyPlanet.ID) {
            mCurrentImage = showImageView(R.id.imageTinyPlanet);
            String ename = mCurrentImage.getContext().getString(R.string.tinyplanet);
            mUtilityPanel.setEffectName(ename);

        } else {
            if (id == R.id.cancelFilter) {
                cancelCurrentFilter();
            } else if (id == R.id.applyEffect || id == R.id.applyFilter) {
                if (MasterImage.getImage().getCurrentFilter() instanceof ImageFilterTinyPlanet) {
                    mActivity.saveImage();
                } else {
                    showPanel(mCurrentPanel);
                }
                MasterImage.getImage().invalidateFiltersOnly();

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

    public void cancelCurrentFilter() {
        resetParameters();
        MasterImage masterImage = MasterImage.getImage();
        HistoryAdapter adapter = masterImage.getHistory();

        int position = adapter.undo();
        masterImage.onHistoryItemClick(position);
        mActivity.invalidateViews();
    }

    public void setEditorPlaceHolder(EditorPlaceHolder editorPlaceHolder) {
        mEditorPlaceHolder = editorPlaceHolder;
    }
}
