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
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.ui.PopupList;


public class SimpleActionBarView extends LinearLayout {

    private PopupList mPopupList;

    public SimpleActionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.simple_action_bar, this);
        Button button = (Button) findViewById(R.id.menu_button);
        button.setText("Menu");
        mPopupList = new PopupList(context, button);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPopupList.show();
            }
        });
    }

    public void setOptionsMenu(SimpleMenu menu) {
        mPopupList.clearItems();
        for (int i = 0, n = menu.getItemCount(); i < n; ++i) {
            SimpleMenu.Item item = menu.getItem(i);
            mPopupList.addItem(item.id, item.title);
        }
    }
}
