package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import com.android.gallery3d.filtershow.PanelController;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ImageSlave extends ImageShow {
    private ImageShow mMasterImageShow = null;

    public ImageSlave(Context context) {
        super(context);
    }

    public ImageSlave(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageShow getMaster() {
        return mMasterImageShow;
    }

    public void setMaster(ImageShow master) {
        mMasterImageShow = master;
    }

    @Override
    public ImagePreset getImagePreset() {
        return mMasterImageShow.getImagePreset();
    }

    @Override
    public void setImagePreset(ImagePreset preset, boolean addToHistory) {
        mMasterImageShow.setImagePreset(preset, addToHistory);
    }

    @Override
    public void setCurrentFilter(ImageFilter filter) {
        mMasterImageShow.setCurrentFilter(filter);
    }

    @Override
    public ImageFilter getCurrentFilter() {
        return mMasterImageShow.getCurrentFilter();
    }

    public void updateAngle() {
        mMasterImageShow.setImageRotation(mImageRotation, mImageRotationZoomFactor);
    }

    @Override
    public boolean showTitle() {
        return false;
    }

    @Override
    public float getImageRotation() {
        return mMasterImageShow.getImageRotation();
    }

    @Override
    public float getImageRotationZoomFactor() {
        return mMasterImageShow.getImageRotationZoomFactor();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void setPanelController(PanelController controller) {
        mMasterImageShow.setPanelController(controller);
    }

    @Override
    public PanelController getPanelController() {
        return mMasterImageShow.getPanelController();
    }


}
