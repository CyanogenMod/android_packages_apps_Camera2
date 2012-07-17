package com.android.gallery3d.actionbar;

import android.content.Intent;
import android.view.View;

public class SimpleActionMode implements ActionModeInterface {

    @Override
    public void setMenuItemVisible(int menuItemId, boolean visible) {
    }

    @Override
    public void setMenuItemTitle(int menuItemId, String title) {
    }

    @Override
    public void setMenuItemIntent(int menuItemId, Intent intent) {
    }

    @Override
    public void inflateMenu(int operation) {
    }

    @Override
    public void setCustomView(View view) {
    }

    @Override
    public void finish() {
    }

    @Override
    public void setShareIntent(Intent intent) {
    }

    @Override
    public boolean hasShareButton() {
        return false;
    }

    @Override
    public void setOnShareTargetSelectedListener(OnShareTargetSelectedListener listener) {
    }
}
