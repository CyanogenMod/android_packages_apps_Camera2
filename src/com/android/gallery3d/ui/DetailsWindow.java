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
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ReverseGeocoder;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Address;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;

import java.util.ArrayList;
import java.util.Map.Entry;

// TODO: Add scroll bar to this window.
public class DetailsWindow extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "DetailsWindow";
    private static final int MSG_REFRESH_LOCATION = 1;
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
    private Future<Address> mAddressLookupJob;
    private Handler mHandler;
    private Icon mCloseButton;
    private int mMaxDetailLength;
    private CloseListener mListener;

    private ScrollView mScrollView;
    private DetailsPanel mDetailPanel = new DetailsPanel();

    public interface DetailsSource {
        public int size();
        public int findIndex(int indexHint);
        public MediaDetails getDetails();
    }

    public interface CloseListener {
        public void onClose();
    }

    public DetailsWindow(GalleryActivity activity, DetailsSource source) {
        mContext = activity;
        mSource = source;
        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_REFRESH_LOCATION:
                        mModel.updateLocation((Address) msg.obj);
                        invalidate();
                        break;
                }
            }
        };
        Context context = activity.getAndroidContext();
        ResourceTexture icon = new ResourceTexture(context, R.drawable.ic_menu_cancel_holo_light);
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

        reloadDetails(0);
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
        int height = MeasureSpec.getSize(heightSpec);
        MeasureHelper.getInstance(this)
                .setPreferredContentSize(PREFERRED_WIDTH, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void onLayout(boolean sizeChange, int l, int t, int r, int b) {
        mCloseButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
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
        setVisibility(GLView.VISIBLE);
        requestLayout();
    }

    public void hide() {
        setVisibility(GLView.INVISIBLE);
        requestLayout();
    }

    public void pause() {
        Future<Address> lookupJob = mAddressLookupJob;
        if (lookupJob != null) {
            lookupJob.cancel();
            lookupJob.waitDone();
        }
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

    private class AddressLookupJob implements Job<Address> {
        double[] mLatlng;
        protected AddressLookupJob(double[] latlng) {
            mLatlng = latlng;
        }

        public Address run(JobContext jc) {
            ReverseGeocoder geocoder = new ReverseGeocoder(mContext.getAndroidContext());
            return geocoder.lookupAddress(mLatlng[0], mLatlng[1], true);
        }
    }

    private class MyDataModel {
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
                        value = getLocationText((double[]) detail.getValue());
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
                                getName(context, detail.getKey()));
                        value = valueObj.toString();
                    }
                }
                int key = detail.getKey();
                if (details.hasUnit(key)) {
                    value = String.format("%s : %s %s", getName(context, key), value,
                            context.getString(details.getUnit(key)));
                } else {
                    value = String.format("%s : %s", getName(context, key), value);
                }
                Texture label = MultiLineTexture.newInstance(
                        value, mMaxDetailLength, FONT_SIZE, FONT_COLOR);
                mItems.add(label);
            }
        }

        private String getLocationText(double[] latlng) {
            String text = GalleryUtils.formatLatitudeLongitude("(%f,%f)", latlng[0], latlng[1]);
            mAddressLookupJob = mContext.getThreadPool().submit(
                    new AddressLookupJob(latlng),
                    new FutureListener<Address>() {
                        public void onFutureDone(Future<Address> future) {
                            mAddressLookupJob = null;
                            if (!future.isCancelled()) {
                                mHandler.sendMessage(mHandler.obtainMessage(
                                        MSG_REFRESH_LOCATION, future.get()));
                            }
                        }
                    });
            mLocationIndex = mItems.size();
            return text;
        }

        public void updateLocation(Address address) {
            int index = mLocationIndex;
            if (address != null && index >=0 && index < mItems.size()) {
                Context context = mContext.getAndroidContext();
                String parts[] = {
                    address.getAdminArea(),
                    address.getSubAdminArea(),
                    address.getLocality(),
                    address.getSubLocality(),
                    address.getThoroughfare(),
                    address.getSubThoroughfare(),
                    address.getPremises(),
                    address.getPostalCode(),
                    address.getCountryName()
                };

                String addressText = "";
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i] == null || parts[i].isEmpty()) continue;
                    if (!addressText.isEmpty()) {
                        addressText += ", ";
                    }
                    addressText += parts[i];
                }
                String text = String.format("%s : %s", getName(context,
                        MediaDetails.INDEX_LOCATION), addressText);
                mItems.set(index, MultiLineTexture.newInstance(
                        text, mMaxDetailLength, FONT_SIZE, FONT_COLOR));
            }
        }

        public Texture getView(int index) {
            return mItems.get(index);
        }

        public int size() {
            return mItems.size();
        }
    }

    private static String getName(Context context, int key) {
        switch (key) {
            case MediaDetails.INDEX_TITLE:
                return context.getString(R.string.title);
            case MediaDetails.INDEX_DESCRIPTION:
                return context.getString(R.string.description);
            case MediaDetails.INDEX_DATETIME:
                return context.getString(R.string.time);
            case MediaDetails.INDEX_LOCATION:
                return context.getString(R.string.location);
            case MediaDetails.INDEX_PATH:
                return context.getString(R.string.path);
            case MediaDetails.INDEX_WIDTH:
                return context.getString(R.string.width);
            case MediaDetails.INDEX_HEIGHT:
                return context.getString(R.string.height);
            case MediaDetails.INDEX_ORIENTATION:
                return context.getString(R.string.orientation);
            case MediaDetails.INDEX_DURATION:
                return context.getString(R.string.duration);
            case MediaDetails.INDEX_MIMETYPE:
                return context.getString(R.string.mimetype);
            case MediaDetails.INDEX_SIZE:
                return context.getString(R.string.file_size);
            case MediaDetails.INDEX_MAKE:
                return context.getString(R.string.maker);
            case MediaDetails.INDEX_MODEL:
                return context.getString(R.string.model);
            case MediaDetails.INDEX_FLASH:
                return context.getString(R.string.flash);
            case MediaDetails.INDEX_APERTURE:
                return context.getString(R.string.aperture);
            case MediaDetails.INDEX_FOCAL_LENGTH:
                return context.getString(R.string.focal_length);
            case MediaDetails.INDEX_WHITE_BALANCE:
                return context.getString(R.string.white_balance);
            case MediaDetails.INDEX_EXPOSURE_TIME:
                return context.getString(R.string.exposure_time);
            case MediaDetails.INDEX_ISO:
                return context.getString(R.string.iso);
            default:
                return "Unknown key" + key;
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

            int h = getPaddings().top + LINE_SPACING;
            for (int i = 0, n = mModel.size(); i < n; ++i) {
                h += mModel.getView(i).getHeight() + LINE_SPACING;
            }

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
}
