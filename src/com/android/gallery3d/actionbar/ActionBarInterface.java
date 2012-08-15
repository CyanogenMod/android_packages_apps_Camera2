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

package com.android.gallery3d.actionbar;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.widget.SpinnerAdapter;

public interface ActionBarInterface extends MenuHolder {
    // These values are copied from android.app.ActionBar
    public static final int NAVIGATION_MODE_LIST = 1;
    public static final int NAVIGATION_MODE_STANDARD = 0;

    // These values are copied from android.app.ActionBar
    public static final int DISPLAY_HOME_AS_UP = 4;
    public static final int DISPLAY_SHOW_TITLE = 8;

    public static interface OnNavigationListener {
        public boolean onNavigationItemSelected(int itemPosition, long itemId);
    }

    public static interface OnMenuVisibilityListener {
        public void onMenuVisibilityChanged(boolean isVisible);
    }

    public int getHeight();

    public void setListNavigationCallbacks(
            SpinnerAdapter adapter, OnNavigationListener listener);
    public void setNavigationMode(int mode);
    public void setSelectedNavigationItem(int index);

    public void addOnMenuVisibilityListener(OnMenuVisibilityListener l);
    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener l);

    public void setDisplayOptions(int options, int mask);
    public void setHomeButtonEnabled(boolean enabled);
    public void setTitle(String title);
    public void setSubtitle(String subtitle);
    public void show();
    public void hide();

    public void setShareIntent(Intent intent);

    public void setLogo(Drawable logo);

    public boolean createActionMenu(Menu menu, int menuResId);
    public boolean hasShareMenuItem();
}
