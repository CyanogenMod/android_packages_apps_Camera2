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

import java.util.ArrayList;

public class SimpleMenu implements MenuHolder {
    // These values are copied from MenuItem in HoneyComb
    private static final int SHOW_AS_ACTION_NEVER = 0;
    private static final int SHOW_AS_ACTION_ALWAYS = 1;
    private static final int SHOW_AS_ACTION_IFROOM = 2;
    private static final int SHOW_AS_ACTION_WITH_TEXT = 4;

    // A bit mask to get WHEN to show as action. It is one of the following
    // values: SHOW_AS_ACTION_NEVER, SHOW_AS_ACTION_ALWAYS, or
    // SHOW_AS_ACTION_IFROOM.
    private static final int SHOW_AS_ACTION_MASK = 0x03;

    public static class Item {
        public int id;
        public int iconId;
        public String title;
        public boolean visible;
        public int showAsAction;
        public Intent intent;
    }

    private ArrayList<Item> mItems = new ArrayList<Item>();

    public void clear() {
        mItems.clear();
    }

    public void addItem(Item item) {
        mItems.add(item);
    }

    @Override
    public void setMenuItemVisible(int menuItemId, boolean visible) {
        Item item = findItem(menuItemId);
        if (item != null) item.visible = visible;
    }

    public int getItemCount() {
        return mItems.size();
    }

    public Item getItem(int index) {
        return mItems.get(index);
    }

    public Item findItem(int menuItemId) {
        for (int i = 0, n = mItems.size(); i < n; ++i) {
            Item item = mItems.get(i);
            if (item.id == menuItemId) return item;
        }
        return null;
    }

    @Override
    public void setMenuItemTitle(int menuItemId, String title) {
        Item item = findItem(menuItemId);
        if (item != null) item.title = title;
    }

    @Override
    public void setMenuItemIntent(int menuItemId, Intent intent) {
        Item item = findItem(menuItemId);
        if (item != null) item.intent = intent;
    }
}
