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

package com.android.gallery3d.filtershow.editors;

import android.app.Dialog;
import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.colorpicker.ColorGridDialog;
import com.android.gallery3d.filtershow.colorpicker.RGBListener;
import com.android.gallery3d.filtershow.filters.FilterDrawRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilterDraw;
import com.android.gallery3d.filtershow.imageshow.ImageDraw;

public class EditorDraw extends Editor {
    private static final String LOGTAG = "EditorDraw";
    public static final int ID = R.id.editorDraw;
    public ImageDraw mImageDraw;

    public EditorDraw() {
        super(ID);
    }

    @Override
    public void createEditor(Context context, FrameLayout frameLayout) {
        super.createEditor(context, frameLayout);
        mView = mImageShow = mImageDraw = new ImageDraw(context);
        mImageDraw.setEditor(this);

    }

    @Override
    public void reflectCurrentFilter() {
        super.reflectCurrentFilter();
        FilterRepresentation rep = getLocalRepresentation();

        if (rep != null && getLocalRepresentation() instanceof FilterDrawRepresentation) {
            FilterDrawRepresentation drawRep = (FilterDrawRepresentation) getLocalRepresentation();
            mImageDraw.setFilterDrawRepresentation(drawRep);
        }
    }

    @Override
    public void openUtilityPanel(final LinearLayout accessoryViewList) {
        Button view = (Button) accessoryViewList.findViewById(R.id.applyEffect);
        view.setText(mContext.getString(R.string.draw_style));
        view.setOnClickListener(new OnClickListener() {

                @Override
            public void onClick(View arg0) {
                showPopupMenu(accessoryViewList);
            }
        });
    }

    @Override
    public boolean showsSeekBar() {
        return false;
    }

    private void showPopupMenu(LinearLayout accessoryViewList) {
        final Button button = (Button) accessoryViewList.findViewById(
                R.id.applyEffect);
        if (button == null) {
            return;
        }
        final PopupMenu popupMenu = new PopupMenu(mImageShow.getActivity(), button);
        popupMenu.getMenuInflater().inflate(R.menu.filtershow_menu_draw, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                ImageFilterDraw filter = (ImageFilterDraw) mImageShow.getCurrentFilter();
                if (item.getItemId() == R.id.draw_menu_color) {
                    showColorGrid(item);
                } else if (item.getItemId() == R.id.draw_menu_size) {
                    showSizeDialog(item);
                } else if (item.getItemId() == R.id.draw_menu_style_brush_marker) {
                    ImageDraw idraw = (ImageDraw) mImageShow;
                    idraw.setStyle(ImageFilterDraw.BRUSH_STYLE_MARKER);
                } else if (item.getItemId() == R.id.draw_menu_style_brush_spatter) {
                    ImageDraw idraw = (ImageDraw) mImageShow;
                    idraw.setStyle(ImageFilterDraw.BRUSH_STYLE_SPATTER);
                } else if (item.getItemId() == R.id.draw_menu_style_line) {
                    ImageDraw idraw = (ImageDraw) mImageShow;
                    idraw.setStyle(ImageFilterDraw.SIMPLE_STYLE);
                } else if (item.getItemId() == R.id.draw_menu_clear) {
                    ImageDraw idraw = (ImageDraw) mImageShow;
                    idraw.resetParameter();
                    commitLocalRepresentation();
                }
                mView.invalidate();
                return true;
            }
        });
        popupMenu.show();
    }

    public void showSizeDialog(final MenuItem item) {
        FilterShowActivity ctx = mImageShow.getActivity();
        final Dialog dialog = new Dialog(ctx);
        dialog.setTitle(R.string.draw_size_title);
        dialog.setContentView(R.layout.filtershow_draw_size);
        final SeekBar bar = (SeekBar) dialog.findViewById(R.id.sizeSeekBar);
        ImageDraw idraw = (ImageDraw) mImageShow;
        bar.setProgress(idraw.getSize());
        Button button = (Button) dialog.findViewById(R.id.sizeAcceptButton);
        button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                int p = bar.getProgress();
                ImageDraw idraw = (ImageDraw) mImageShow;
                idraw.setSize(p + 1);
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    public void showColorGrid(final MenuItem item) {
        RGBListener cl = new RGBListener() {
            @Override
            public void setColor(int rgb) {
                ImageDraw idraw = (ImageDraw) mImageShow;
                idraw.setColor(rgb);
            }
        };
        ColorGridDialog cpd = new ColorGridDialog(mImageShow.getActivity(), cl);
        cpd.show();
        LayoutParams params = cpd.getWindow().getAttributes();
    }
}
