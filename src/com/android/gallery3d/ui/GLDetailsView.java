/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import static com.android.gallery3d.ui.DetailsWindowConfig.FONT_SIZE;
import static com.android.gallery3d.ui.DetailsWindowConfig.LEFT_RIGHT_EXTRA_PADDING;
import static com.android.gallery3d.ui.DetailsWindowConfig.LINE_SPACING;
import static com.android.gallery3d.ui.DetailsWindowConfig.PREFERRED_WIDTH;
import static com.android.gallery3d.ui.DetailsWindowConfig.TOP_BOTTOM_EXTRA_PADDING;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.ui.DetailsAddressResolver.AddressResolvingListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.DetailsHelper.DetailsViewContainer;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.format.Formatter;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;

import java.util.ArrayList;
import java.util.Map.Entry;

public class GLDetailsView extends GLView implements DetailsViewContainer {
    @SuppressWarnings("unused")
    private static final String TAG = "GLDetailsView";
    private static final int FONT_COLOR = Color.WHITE;
    private static final int CLOSE_BUTTON_SIZE = 32;

    private GalleryActivity mContext;
    protected Texture mBackground;
    private StringTexture mTitle;
    private MyDataModel mModel;
    private MediaDetails mDetails;
    private DetailsSource mSource;
    private int mIndex;
    private int mLocationIndex;
    private Icon mCloseButton;
    private int mMaxDetailLength;
    private CloseListener mListener;

    private ScrollView mScrollView;
    private DetailsPanel mDetailPanel = new DetailsPanel();

    public GLDetailsView(GalleryActivity activity, DetailsSource source) {
        mContext = activity;
        mSource = source;

        Context context = activity.getAndroidContext();
        ResourceTexture icon = new ResourceTexture(context, R.drawable.ic_lockscreen_chevron_up);
        setBackground(new NinePatchTexture(context, R.drawable.popup_full_dark));

        mCloseButton = new Icon(context, icon, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE) {
            @Override
            protected boolean onTouch(MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_UP:
                        if (mListener != null) mListener.onClose();
                }
                return true;
            }
        };
        mScrollView = new ScrollView(context);
        mScrollView.addComponent(mDetailPanel);

        super.addComponent(mScrollView);
        super.addComponent(mCloseButton);
    }

    public void setCloseListener(CloseListener listener) {
        mListener = listener;
    }

    public void setBackground(Texture background) {
        if (background == mBackground) return;
        mBackground = background;
        if (background != null && background instanceof NinePatchTexture) {
            Rect p = ((NinePatchTexture) mBackground).getPaddings();
            p.left += LEFT_RIGHT_EXTRA_PADDING;
            p.right += LEFT_RIGHT_EXTRA_PADDING;
            p.top += TOP_BOTTOM_EXTRA_PADDING;
            p.bottom += TOP_BOTTOM_EXTRA_PADDING;
            setPaddings(p);
        } else {
            setPaddings(0, 0, 0, 0);
        }
        Rect p = getPaddings();
        mMaxDetailLength = PREFERRED_WIDTH - p.left - p.right;
        invalidate();
    }

    public void setTitle(String title) {
        mTitle = StringTexture.newInstance(title, FONT_SIZE, FONT_COLOR);
    }

    @Override
    protected void renderBackground(GLCanvas canvas) {
        if (mBackground == null) return;
        int width = getWidth();
        int height = getHeight();

        //TODO: change alpha in the background image.
        canvas.save(GLCanvas.SAVE_FLAG_ALPHA);
        canvas.setAlpha(0.7f);
        mBackground.draw(canvas, 0, 0, width, height);
        canvas.restore();

        Rect p = getPaddings();
        if (mTitle != null) mTitle.draw(canvas, p.left, p.top);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        mScrollView.measure(widthSpec, heightSpec);
        mCloseButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int height = mScrollView.getMeasuredHeight() + mCloseButton.getMeasuredHeight();
        MeasureHelper.getInstance(this)
                .setPreferredContentSize(mScrollView.getMeasuredWidth(), height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void onLayout(boolean sizeChange, int l, int t, int r, int b) {
        int bWidth = mCloseButton.getMeasuredWidth();
        int bHeight = mCloseButton.getMeasuredHeight();
        int width = getWidth();
        int height = getHeight();

        Rect p = getPaddings();
        mCloseButton.layout(width - p.right - bWidth, p.top,
                width - p.right, p.top + bHeight);
        mScrollView.layout(p.left, p.top + bHeight, width - p.right,
                height - p.bottom);
    }

    public void show() {
        reloadDetails(mSource.getIndex());
        setVisibility(GLView.VISIBLE);
        requestLayout();
    }

    public void hide() {
        setVisibility(GLView.INVISIBLE);
        requestLayout();
    }

    public void reloadDetails(int indexHint) {
        int index = mSource.findIndex(indexHint);
        if (index == -1) return;
        MediaDetails details = mSource.getDetails();
        if (details != null) {
            if (mIndex == index && mDetails == details) return;
            mIndex = index;
            mDetails = details;
            setDetails(details);
        }
        mDetailPanel.requestLayout();
    }

    private void setDetails(MediaDetails details) {
        mModel = new MyDataModel(details);
        invalidate();
    }

    private class MyDataModel implements AddressResolvingListener {
        ArrayList<Texture> mItems;

        public MyDataModel(MediaDetails details) {
            Context context = mContext.getAndroidContext();
            mLocationIndex = -1;
            mItems = new ArrayList<Texture>(details.size());
            setTitle(String.format(context.getString(R.string.sequence_in_set),
                    mIndex + 1, mSource.size()));
            setDetails(context, details);
        }

        private void setDetails(Context context, MediaDetails details) {
            for (Entry<Integer, Object> detail : details) {
                String value;
                switch (detail.getKey()) {
                    case MediaDetails.INDEX_LOCATION: {
                        double[] latlng = (double[]) detail.getValue();
                        mLocationIndex = mItems.size();
                        value = DetailsHelper.resolveAddress(mContext, latlng, this);
                        break;
                    }
                    case MediaDetails.INDEX_SIZE: {
                        value = Formatter.formatFileSize(
                                context, (Long) detail.getValue());
                        break;
                    }
                    case MediaDetails.INDEX_WHITE_BALANCE: {
                        value = "1".equals(detail.getValue())
                                ? context.getString(R.string.manual)
                                : context.getString(R.string.auto);
                        break;
                    }
                    case MediaDetails.INDEX_FLASH: {
                        MediaDetails.FlashState flash =
                                (MediaDetails.FlashState) detail.getValue();
                        // TODO: camera doesn't fill in the complete values, show more information
                        // when it is fixed.
                        if (flash.isFlashFired()) {
                            value = context.getString(R.string.flash_on);
                        } else {
                            value = context.getString(R.string.flash_off);
                        }
                        break;
                    }
                    case MediaDetails.INDEX_EXPOSURE_TIME: {
                        value = (String) detail.getValue();
                        double time = Double.valueOf(value);
                        if (time < 1.0f) {
                            value = String.format("1/%d", (int) (0.5f + 1 / time));
                        } else {
                            int integer = (int) time;
                            time -= integer;
                            value = String.valueOf(integer) + "''";
                            if (time > 0.0001) {
                                value += String.format(" 1/%d", (int) (0.5f + 1 / time));
                            }
                        }
                        break;
                    }
                    default: {
                        Object valueObj = detail.getValue();
                        // This shouldn't happen, log its key to help us diagnose the problem.
                        Utils.assertTrue(valueObj != null, "%s's value is Null",
                                DetailsHelper.getDetailsName(context, detail.getKey()));
                        value = valueObj.toString();
                    }
                }
                int key = detail.getKey();
                if (details.hasUnit(key)) {
                    value = String.format("%s : %s %s", DetailsHelper.getDetailsName(
                            context, key), value, context.getString(details.getUnit(key)));
                } else {
                    value = String.format("%s : %s", DetailsHelper.getDetailsName(
                            context, key), value);
                }
                Texture label = MultiLineTexture.newInstance(
                        value, mMaxDetailLength, FONT_SIZE, FONT_COLOR);
                mItems.add(label);
            }
        }

        public Texture getView(int index) {
            return mItems.get(index);
        }

        public int size() {
            return mItems.size();
        }

        public void onAddressAvailable(String address) {
            mItems.set(mLocationIndex, MultiLineTexture.newInstance(
                    address, mMaxDetailLength, FONT_SIZE, FONT_COLOR));
            GLDetailsView.this.invalidate();
        }
    }

    private class DetailsPanel extends GLView {

        @Override
        public void onMeasure(int widthSpec, int heightSpec) {
            if (mTitle == null || mModel == null) {
                MeasureHelper.getInstance(this)
                        .setPreferredContentSize(PREFERRED_WIDTH, 0)
                        .measure(widthSpec, heightSpec);
                return;
            }

            Rect p = getPaddings();
            int h = p.top + LINE_SPACING;
            for (int i = 0, n = mModel.size(); i < n; ++i) {
                h += mModel.getView(i).getHeight() + LINE_SPACING;
            }
            h += p.bottom;

            MeasureHelper.getInstance(this)
                    .setPreferredContentSize(PREFERRED_WIDTH, h)
                    .measure(widthSpec, heightSpec);
        }

        @Override
        protected void render(GLCanvas canvas) {
            super.render(canvas);

            if (mTitle == null || mModel == null) {
                return;
            }
            Rect p = getPaddings();
            int x = p.left, y = p.top + LINE_SPACING;
            for (int i = 0, n = mModel.size(); i < n ; i++) {
                Texture t = mModel.getView(i);
                t.draw(canvas, x, y);
                y += t.getHeight() + LINE_SPACING;
            }
        }
    }

    public GLView getGLView() {
        return this;
    }
}
