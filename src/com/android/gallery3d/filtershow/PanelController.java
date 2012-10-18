
package com.android.gallery3d.filtershow;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewPropertyAnimator;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterContrast;
import com.android.gallery3d.filtershow.filters.ImageFilterExposure;
import com.android.gallery3d.filtershow.filters.ImageFilterHue;
import com.android.gallery3d.filtershow.filters.ImageFilterRedEye;
import com.android.gallery3d.filtershow.filters.ImageFilterSaturated;
import com.android.gallery3d.filtershow.filters.ImageFilterShadows;
import com.android.gallery3d.filtershow.filters.ImageFilterSharpen;
import com.android.gallery3d.filtershow.filters.ImageFilterVibrance;
import com.android.gallery3d.filtershow.filters.ImageFilterVignette;
import com.android.gallery3d.filtershow.filters.ImageFilterWBalance;
import com.android.gallery3d.filtershow.imageshow.ImageCrop;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.ui.ImageCurves;
import com.android.gallery3d.filtershow.ui.ImageButtonTitle;

import java.util.HashMap;
import java.util.Vector;

public class PanelController implements OnClickListener {
    private static int PANEL = 0;
    private static int COMPONENT = 1;
    private static int VERTICAL_MOVE = 0;
    private static int HORIZONTAL_MOVE = 1;
    private static final int ANIM_DURATION = 200;

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
        private int mCurrentAspectButton = 0;
        private static final int NUMBER_OF_ASPECT_BUTTONS = 6;
        private static final int ASPECT_NONE = 0;
        private static final int ASPECT_1TO1 = 1;
        private static final int ASPECT_5TO7 = 2;
        private static final int ASPECT_4TO6 = 3;
        private static final int ASPECT_16TO9 = 4;
        private static final int ASPECT_ORIG = 5;

        public UtilityPanel(Context context, View view, View textView, View button) {
            mContext = context;
            mView = view;
            mTextView = (TextView) textView;
            mAspectButton = button;
        }

        public boolean selected() {
            return mSelected;
        }

        public void nextAspectButton() {
            if (mAspectButton instanceof ImageButtonTitle
                    && mCurrentImage instanceof ImageCrop) {
                switch (mCurrentAspectButton) {
                    case ASPECT_NONE:
                        ((ImageButtonTitle) mAspectButton).setText(mContext
                                .getString(R.string.aspect)
                                + " "
                                + mContext.getString(R.string.aspect1to1_effect));
                        ((ImageCrop) mCurrentImage).apply(1, 1);
                        break;
                    case ASPECT_1TO1:
                        ((ImageButtonTitle) mAspectButton).setText(mContext
                                .getString(R.string.aspect)
                                + " "
                                + mContext.getString(R.string.aspect5to7_effect));
                        ((ImageCrop) mCurrentImage).apply(7, 5);
                        break;
                    case ASPECT_5TO7:
                        ((ImageButtonTitle) mAspectButton).setText(mContext
                                .getString(R.string.aspect)
                                + " "
                                + mContext.getString(R.string.aspect4to6_effect));
                        ((ImageCrop) mCurrentImage).apply(6, 4);
                        break;
                    case ASPECT_4TO6:
                        ((ImageButtonTitle) mAspectButton).setText(mContext
                                .getString(R.string.aspect)
                                + " "
                                + mContext.getString(R.string.aspect9to16_effect));
                        ((ImageCrop) mCurrentImage).apply(16, 9);
                        break;
                    case ASPECT_16TO9:
                        ((ImageButtonTitle) mAspectButton).setText(mContext
                                .getString(R.string.aspect)
                                + " "
                                + mContext.getString(R.string.aspectOriginal_effect));
                        ((ImageCrop) mCurrentImage).applyOriginal();
                        break;
                    case ASPECT_ORIG:
                        ((ImageButtonTitle) mAspectButton).setText(mContext
                                .getString(R.string.aspect)
                                + " "
                                + mContext.getString(R.string.aspectNone_effect));
                        ((ImageCrop) mCurrentImage).applyClear();
                        break;
                    default:
                        ((ImageButtonTitle) mAspectButton).setText(mContext
                                .getString(R.string.aspect)
                                + " "
                                + mContext.getString(R.string.aspectNone_effect));
                        ((ImageCrop) mCurrentImage).applyClear();
                        mCurrentAspectButton = ASPECT_NONE;
                        break;
                }
                mCurrentAspectButton = (mCurrentAspectButton + 1) % NUMBER_OF_ASPECT_BUTTONS;
            }
        }

        void setCurrentAspectButton(int n){
            mCurrentAspectButton = n;
        }

        public void showAspectButtons() {
            if (mAspectButton != null)
                mAspectButton.setVisibility(View.VISIBLE);
        }

        public void hideAspectButtons() {
            if (mAspectButton != null)
                mAspectButton.setVisibility(View.GONE);
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
        mCurrentImage.resetParameter();
        showPanel(mCurrentPanel);
        mCurrentImage.select();
    }

    public boolean onBackPressed() {
        if (mUtilityPanel == null || !mUtilityPanel.selected()) {
            return true;
        }
        resetParameters();
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
            View button) {
        mUtilityPanel = new UtilityPanel(context, utilityPanel, textView, button);
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
        copy.setIsFx(false);
        mMasterImage.setImagePreset(copy);
        return filter;
    }

    public void ensureFilter(String name) {
        ImagePreset preset = getImagePreset();
        ImageFilter filter = preset.getFilter(name);
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
            curves.nextChannel();
            return;
        }

        if (mCurrentImage != null) {
            mCurrentImage.unselect();
        }
        mUtilityPanel.hideAspectButtons();
        switch (view.getId()) {
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
                mUtilityPanel.setCurrentAspectButton(-1);
                mUtilityPanel.nextAspectButton();
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
                curves.reloadCurve();
                mCurrentImage = curves;
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
                mUtilityPanel.nextAspectButton();
                mUtilityPanel.showAspectButtons();
                break;
            }
            case R.id.applyEffect: {
                showPanel(mCurrentPanel);
                break;
            }
        }
        mCurrentImage.select();
    }
}
