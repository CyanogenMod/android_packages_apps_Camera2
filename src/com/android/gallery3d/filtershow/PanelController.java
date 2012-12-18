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
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewPropertyAnimator;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterBwFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterContrast;
import com.android.gallery3d.filtershow.filters.ImageFilterCurves;
import com.android.gallery3d.filtershow.filters.ImageFilterExposure;
import com.android.gallery3d.filtershow.filters.ImageFilterHue;
import com.android.gallery3d.filtershow.filters.ImageFilterRedEye;
import com.android.gallery3d.filtershow.filters.ImageFilterSaturated;
import com.android.gallery3d.filtershow.filters.ImageFilterShadows;
import com.android.gallery3d.filtershow.filters.ImageFilterSharpen;
import com.android.gallery3d.filtershow.filters.ImageFilterTinyPlanet;
import com.android.gallery3d.filtershow.filters.ImageFilterVibrance;
import com.android.gallery3d.filtershow.filters.ImageFilterVignette;
import com.android.gallery3d.filtershow.filters.ImageFilterWBalance;
import com.android.gallery3d.filtershow.imageshow.ImageCrop;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.FramedTextButton;
import com.android.gallery3d.filtershow.ui.ImageCurves;

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
        private final TextView mTextView;
        private boolean mSelected = false;
        private String mEffectName = null;
        private int mParameterValue = 0;
        private boolean mShowParameterValue = false;
        private View mAspectButton = null;
        private View mCurvesButton = null;
        boolean firstTimeCropDisplayed = true;

        public UtilityPanel(Context context, View view, View textView,
                View aspectButton, View curvesButton) {
            mContext = context;
            mView = view;
            mTextView = (TextView) textView;
            mAspectButton = aspectButton;
            mCurvesButton = curvesButton;
        }

        public boolean selected() {
            return mSelected;
        }

        public void setAspectButton(FramedTextButton button, int itemId) {
            ImageCrop imageCrop = (ImageCrop) mCurrentImage;
            switch (itemId) {
                case R.id.crop_menu_1to1: {
                    String t = mContext.getString(R.string.aspect1to1_effect);
                    button.setText(t);
                    imageCrop.apply(1, 1);
                    imageCrop.setAspectString(t);
                    break;
                }
                case R.id.crop_menu_4to3: {
                    String t = mContext.getString(R.string.aspect4to3_effect);
                    button.setText(t);
                    imageCrop.apply(4, 3);
                    imageCrop.setAspectString(t);
                    break;
                }
                case R.id.crop_menu_3to4: {
                    String t = mContext.getString(R.string.aspect3to4_effect);
                    button.setText(t);
                    imageCrop.apply(3, 4);
                    imageCrop.setAspectString(t);
                    break;
                }
                case R.id.crop_menu_5to7: {
                    String t = mContext.getString(R.string.aspect5to7_effect);
                    button.setText(t);
                    imageCrop.apply(5, 7);
                    imageCrop.setAspectString(t);
                    break;
                }
                case R.id.crop_menu_7to5: {
                    String t = mContext.getString(R.string.aspect7to5_effect);
                    button.setText(t);
                    imageCrop.apply(7, 5);
                    imageCrop.setAspectString(t);
                    break;
                }
                case R.id.crop_menu_none: {
                    String t = mContext.getString(R.string.aspectNone_effect);
                    button.setText(t);
                    imageCrop.applyClear();
                    imageCrop.setAspectString(t);
                    break;
                }
                case R.id.crop_menu_original: {
                    String t = mContext.getString(R.string.aspectOriginal_effect);
                    button.setText(t);
                    imageCrop.applyOriginal();
                    imageCrop.setAspectString(t);
                    break;
                }
            }
            imageCrop.invalidate();
        }

        public void showAspectButtons() {
            if (mAspectButton != null)
                mAspectButton.setVisibility(View.VISIBLE);
        }

        public void hideAspectButtons() {
            if (mAspectButton != null)
                mAspectButton.setVisibility(View.GONE);
        }

        public void showCurvesButtons() {
            if (mCurvesButton != null)
                mCurvesButton.setVisibility(View.VISIBLE);
        }

        public void hideCurvesButtons() {
            if (mCurvesButton != null)
                mCurvesButton.setVisibility(View.GONE);
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
    private final Vector<View> mImageViews = new Vector<View>();
    private View mCurrentPanel = null;
    private View mRowPanel = null;
    private UtilityPanel mUtilityPanel = null;
    private ImageShow mMasterImage = null;
    private ImageShow mCurrentImage = null;
    private FilterShowActivity mActivity = null;

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
        HistoryAdapter adapter = mMasterImage.getHistory();
        int position = adapter.undo();
        mMasterImage.onItemClick(position);
        showPanel(mCurrentPanel);
        mCurrentImage.select();
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

    public void setUtilityPanel(Context context, View utilityPanel, View textView,
            View aspectButton, View curvesButton) {
        mUtilityPanel = new UtilityPanel(context, utilityPanel, textView,
                aspectButton, curvesButton);
    }

    public void setMasterImage(ImageShow imageShow) {
        mMasterImage = imageShow;
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
        mMasterImage.setCurrentFilter(null);
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
        return mMasterImage.getImagePreset();
    }

    public ImageFilter setImagePreset(ImageFilter filter, String name) {
        ImagePreset copy = new ImagePreset(getImagePreset());
        copy.add(filter);
        copy.setHistoryName(name);
        copy.setIsFx(false);
        mMasterImage.setImagePreset(copy);
        return filter;
    }

    public void ensureFilter(String name) {
        ImagePreset preset = getImagePreset();
        ImageFilter filter = preset.getFilter(name);
        if (filter != null) {
            // If we already have a filter, we might still want
            // to push it onto the history stack.
            ImagePreset copy = new ImagePreset(getImagePreset());
            copy.setHistoryName(name);
            mMasterImage.setImagePreset(copy);
            filter = copy.getFilter(name);
        }

        if (filter == null && name.equalsIgnoreCase(
                mCurrentImage.getContext().getString(R.string.curvesRGB))) {
            filter = setImagePreset(new ImageFilterCurves(), name);
        }
        if (filter == null && name.equalsIgnoreCase(
                mCurrentImage.getContext().getString(R.string.tinyplanet))) {
            filter = setImagePreset(new ImageFilterTinyPlanet(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.vignette))) {
            filter = setImagePreset(new ImageFilterVignette(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.sharpness))) {
            filter = setImagePreset(new ImageFilterSharpen(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.contrast))) {
            filter = setImagePreset(new ImageFilterContrast(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.saturation))) {
            filter = setImagePreset(new ImageFilterSaturated(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.bwfilter))) {
            filter = setImagePreset(new ImageFilterBwFilter(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.hue))) {
            filter = setImagePreset(new ImageFilterHue(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.exposure))) {
            filter = setImagePreset(new ImageFilterExposure(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.vibrance))) {
            filter = setImagePreset(new ImageFilterVibrance(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(
                        R.string.shadow_recovery))) {
            filter = setImagePreset(new ImageFilterShadows(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.redeye))) {
            filter = setImagePreset(new ImageFilterRedEye(), name);
        }
        if (filter == null
                && name.equalsIgnoreCase(mCurrentImage.getContext().getString(R.string.wbalance))) {
            filter = setImagePreset(new ImageFilterWBalance(), name);
        }
        mMasterImage.setCurrentFilter(filter);
    }

    private void showCurvesPopupMenu(final ImageCurves curves, final FramedTextButton anchor) {
        PopupMenu popupMenu = new PopupMenu(mCurrentImage.getContext(), anchor);
        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_curves, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                curves.setChannel(item.getItemId());
                anchor.setTextFrom(item.getItemId());
                return true;
            }
        });
        popupMenu.show();
    }

    private void showCropPopupMenu(final FramedTextButton anchor) {
        PopupMenu popupMenu = new PopupMenu(mCurrentImage.getContext(), anchor);
        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_crop, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mUtilityPanel.setAspectButton(anchor, item.getItemId());
                return true;
            }
        });
        popupMenu.show();
    }

    public void showComponent(View view) {
        if (mUtilityPanel != null && !mUtilityPanel.selected()) {
            Panel current = mPanels.get(mCurrentPanel);
            ViewPropertyAnimator anim1 = current.unselect(-1, VERTICAL_MOVE);
            anim1.start();
            if (mUtilityPanel != null) {
                ViewPropertyAnimator anim2 = mUtilityPanel.select();
                anim2.start();
            }
        }

        if (view.getId() == R.id.pickCurvesChannel) {
            ImageCurves curves = (ImageCurves) showImageView(R.id.imageCurves);
            showCurvesPopupMenu(curves, (FramedTextButton) view);
            return;
        }

        if (view.getId() == R.id.aspect) {
            showCropPopupMenu((FramedTextButton) view);
            return;
        }

        if (mCurrentImage != null) {
            mCurrentImage.unselect();
        }
        mUtilityPanel.hideAspectButtons();
        mUtilityPanel.hideCurvesButtons();
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
                if (mCurrentImage instanceof ImageCrop && mUtilityPanel.firstTimeCropDisplayed){
                    ((ImageCrop) mCurrentImage).applyClear();
                    mUtilityPanel.firstTimeCropDisplayed = false;
                }
                mUtilityPanel.showAspectButtons();
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
            case R.id.vignetteButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.vignette);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.curvesButtonRGB: {
                ImageCurves curves = (ImageCurves) showImageView(R.id.imageCurves);
                String ename = curves.getContext().getString(R.string.curvesRGB);
                mUtilityPanel.setEffectName(ename);
                mUtilityPanel.setShowParameter(false);
                mUtilityPanel.showCurvesButtons();
                mCurrentImage = curves;
                ensureFilter(ename);
                break;
            }
            case R.id.sharpenButton: {
                mCurrentImage = showImageView(R.id.imageZoom).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.sharpness);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.contrastButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.contrast);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.saturationButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.saturation);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.bwfilterButton: {
            mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
            String ename = mCurrentImage.getContext().getString(R.string.bwfilter);
            mUtilityPanel.setEffectName(ename);
            ensureFilter(ename);
            break;
        }
            case R.id.wbalanceButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(false);
                String ename = mCurrentImage.getContext().getString(R.string.wbalance);
                mUtilityPanel.setEffectName(ename);
                mUtilityPanel.setShowParameter(false);
                ensureFilter(ename);
                break;
            }
            case R.id.hueButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.hue);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.exposureButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.exposure);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.vibranceButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.vibrance);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.shadowRecoveryButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.shadow_recovery);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.redEyeButton: {
                mCurrentImage = showImageView(R.id.imageShow).setShowControls(true);
                String ename = mCurrentImage.getContext().getString(R.string.redeye);
                mUtilityPanel.setEffectName(ename);
                ensureFilter(ename);
                break;
            }
            case R.id.aspect: {
                mUtilityPanel.showAspectButtons();
                break;
            }
            case R.id.applyEffect: {
                if (mMasterImage.getCurrentFilter() instanceof ImageFilterTinyPlanet) {
                    mActivity.saveImage();
                } else {
                    if (mCurrentImage instanceof ImageCrop) {
                        ((ImageCrop) mCurrentImage).saveAndSetPreset();
                    }
                    showPanel(mCurrentPanel);
                }
                break;
            }
        }
        mCurrentImage.select();
    }
}
