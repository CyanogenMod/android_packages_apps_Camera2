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

package com.android.gallery3d.filtershow.colorpicker;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.ToggleButton;

import com.android.gallery3d.R;

public class ColorPickerDialog extends Dialog implements ColorListener {
    ToggleButton mSelectedButton;
    GradientDrawable mSelectRect;

    float[] mHSVO = new float[4];

    public ColorPickerDialog(Context context, final ColorListener cl) {
        super(context);

        setContentView(R.layout.filtershow_color_picker);
        ColorValueView csv = (ColorValueView) findViewById(R.id.colorValueView);
        ColorRectView cwv = (ColorRectView) findViewById(R.id.colorRectView);
        ColorOpacityView cvv = (ColorOpacityView) findViewById(R.id.colorOpacityView);
        float[] hsvo = new float[] {
                123, .9f, 1, 1 };

        mSelectRect = (GradientDrawable) getContext()
                .getResources().getDrawable(R.drawable.filtershow_color_picker_roundrect);
        Button selButton = (Button) findViewById(R.id.btnSelect);
        selButton.setCompoundDrawablesWithIntrinsicBounds(null, null, mSelectRect, null);
        Button sel = (Button) findViewById(R.id.btnSelect);

        sel.setOnClickListener(new View.OnClickListener() {
                @Override
            public void onClick(View v) {
                ColorPickerDialog.this.dismiss();
                if (cl != null) {
                    cl.setColor(mHSVO);
                }
            }
        });

        cwv.setColor(hsvo);
        cvv.setColor(hsvo);
        csv.setColor(hsvo);
        csv.addColorListener(cwv);
        cwv.addColorListener(csv);
        csv.addColorListener(cvv);
        cwv.addColorListener(cvv);
        cvv.addColorListener(cwv);
        cvv.addColorListener(csv);
        cvv.addColorListener(this);
        csv.addColorListener(this);
        cwv.addColorListener(this);

    }

    void toggleClick(ToggleButton v, int[] buttons, boolean isChecked) {
        int id = v.getId();
        if (!isChecked) {
            mSelectedButton = null;
            return;
        }
        for (int i = 0; i < buttons.length; i++) {
            if (id != buttons[i]) {
                ToggleButton b = (ToggleButton) findViewById(buttons[i]);
                b.setChecked(false);
            }
        }
        mSelectedButton = v;

        float[] hsv = (float[]) v.getTag();

        ColorValueView csv = (ColorValueView) findViewById(R.id.colorValueView);
        ColorRectView cwv = (ColorRectView) findViewById(R.id.colorRectView);
        ColorOpacityView cvv = (ColorOpacityView) findViewById(R.id.colorOpacityView);
        cwv.setColor(hsv);
        cvv.setColor(hsv);
        csv.setColor(hsv);
    }

    @Override
    public void setColor(float[] hsvo) {
        System.arraycopy(hsvo, 0, mHSVO, 0, mHSVO.length);
        int color = Color.HSVToColor(hsvo);
        mSelectRect.setColor(color);
        setButtonColor(mSelectedButton, hsvo);
    }

    private void setButtonColor(ToggleButton button, float[] hsv) {
        if (button == null) {
            return;
        }
        int color = Color.HSVToColor(hsv);
        button.setBackgroundColor(color);
        float[] fg = new float[] {
                (hsv[0] + 180) % 360,
                hsv[1],
                        (hsv[2] > .5f) ? .1f : .9f
        };
        button.setTextColor(Color.HSVToColor(fg));
        button.setTag(hsv);
    }

}
