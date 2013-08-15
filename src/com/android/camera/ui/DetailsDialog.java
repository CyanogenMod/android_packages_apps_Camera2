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

package com.android.camera.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.camera.data.MediaDetails;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.Map.Entry;

/**
 * Displays details (such as Exif) of a local media item.
 */
public class DetailsDialog {

    /**
     * Creates a dialog for showing media data.
     *
     * @param context the Android context.
     * @param mediaDetails the media details to display.
     * @return A dialog that can be made visible to show the media details.
     */
    public static Dialog create(Context context, MediaDetails mediaDetails) {
        ListView detailsList = (ListView) LayoutInflater.from(context).inflate(
                R.layout.details_list, null, false);
        detailsList.setAdapter(new DetailsAdapter(context, mediaDetails));

        final AlertDialog.Builder builder =
                new AlertDialog.Builder(context);
        return builder.setTitle(R.string.details).setView(detailsList)
                .setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                }).create();
    }

    /**
     * An adapter for feeding a details list view with the contents of a
     * {@link MediaDetails} instance.
     */
    private static class DetailsAdapter extends BaseAdapter {
        private final Context mContext;
        private final MediaDetails mMediaDetails;
        private final ArrayList<String> mItems;
        private int mLocationIndex;
        private int mWidthIndex = -1;
        private int mHeightIndex = -1;

        public DetailsAdapter(Context context, MediaDetails details) {
            mContext = context;
            mMediaDetails = details;
            mItems = new ArrayList<String>(details.size());
            mLocationIndex = -1;
            setDetails(context, details);
        }

        private void setDetails(Context context, MediaDetails details) {
            boolean resolutionIsValid = true;
            String path = null;
            for (Entry<Integer, Object> detail : details) {
                String value;
                switch (detail.getKey()) {
                    // TODO: Resolve address asynchronously.
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
                        // TODO: camera doesn't fill in the complete values,
                        // show more information when it is fixed.
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
                    case MediaDetails.INDEX_WIDTH:
                        mWidthIndex = mItems.size();
                        value = detail.getValue().toString();
                        if (value.equalsIgnoreCase("0")) {
                            value = context.getString(R.string.unknown);
                            resolutionIsValid = false;
                        }
                        break;
                    case MediaDetails.INDEX_HEIGHT: {
                        mHeightIndex = mItems.size();
                        value = detail.getValue().toString();
                        if (value.equalsIgnoreCase("0")) {
                            value = context.getString(R.string.unknown);
                            resolutionIsValid = false;
                        }
                        break;
                    }
                    case MediaDetails.INDEX_PATH:
                        // Get the path and then fall through to the default
                        // case
                        path = detail.getValue().toString();
                    default: {
                        Object valueObj = detail.getValue();
                        // This shouldn't happen, log its key to help us
                        // diagnose the problem.
                        if (valueObj == null) {
                            fail("%s's value is Null",
                                    getDetailsName(context,
                                            detail.getKey()));
                        }
                        value = valueObj.toString();
                    }
                }
                int key = detail.getKey();
                if (details.hasUnit(key)) {
                    value = String.format("%s: %s %s", getDetailsName(
                            context, key), value, context.getString(details.getUnit(key)));
                } else {
                    value = String.format("%s: %s", getDetailsName(
                            context, key), value);
                }
                mItems.add(value);
                if (!resolutionIsValid) {
                    resolveResolution(path);
                }
            }
        }

        public void resolveResolution(String path) {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null)
                return;
            onResolutionAvailable(bitmap.getWidth(), bitmap.getHeight());
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mMediaDetails.getDetail(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView == null) {
                tv = (TextView) LayoutInflater.from(mContext).inflate(
                        R.layout.details, parent, false);
            } else {
                tv = (TextView) convertView;
            }
            tv.setText(mItems.get(position));
            return tv;
        }

        public void onResolutionAvailable(int width, int height) {
            if (width == 0 || height == 0)
                return;
            // Update the resolution with the new width and height
            String widthString = String.format("%s: %d",
                    getDetailsName(
                            mContext, MediaDetails.INDEX_WIDTH), width);
            String heightString = String.format("%s: %d",
                    getDetailsName(
                            mContext, MediaDetails.INDEX_HEIGHT), height);
            mItems.set(mWidthIndex, String.valueOf(widthString));
            mItems.set(mHeightIndex, String.valueOf(heightString));
            notifyDataSetChanged();
        }
    }

    public static String getDetailsName(Context context, int key) {
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

    /**
     * Throw an assertion error wit the given message.
     *
     * @param message the message, can contain placeholders.
     * @param args if he message contains placeholders, these values will be
     *            used to fill them.
     */
    private static void fail(String message, Object... args) {
        throw new AssertionError(
                args.length == 0 ? message : String.format(message, args));
    }
}
