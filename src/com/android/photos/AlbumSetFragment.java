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

package com.android.photos;

import android.app.Fragment;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.photos.data.AlbumSetLoader;
import com.android.photos.drawables.DataUriThumbnailDrawable;

import java.util.Date;


public class AlbumSetFragment extends Fragment implements OnItemClickListener {
    private GridView mAlbumSetView;
    private View mEmptyView;
    private CursorAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.album_set, container, false);
        mAlbumSetView = (GridView) root.findViewById(android.R.id.list);
        mEmptyView = root.findViewById(android.R.id.empty);
        mEmptyView.setVisibility(View.GONE);
        mAdapter = new AlbumSetCursorAdapter(getActivity());
        mAlbumSetView.setAdapter(mAdapter);
        mAlbumSetView.setOnItemClickListener(this);
        mAdapter.swapCursor(AlbumSetLoader.MOCK);
        return root;
    }

    @Override
    public void onItemClick(AdapterView<?> av, View v, int pos, long id) {
        Cursor c = (Cursor) av.getItemAtPosition(pos);
        int albumId = c.getInt(AlbumSetLoader.INDEX_ID);
        // TODO launch an activity showing the photos in the album
        Toast.makeText(v.getContext(), "Clicked " + albumId, Toast.LENGTH_SHORT).show();
    }

    private static class AlbumSetCursorAdapter extends CursorAdapter {

        private Date mDate = new Date(); // Used for converting timestamps for display

        public AlbumSetCursorAdapter(Context context) {
            super(context, null, false);
        }

        @Override
        public void bindView(View v, Context context, Cursor cursor) {
            TextView titleTextView = (TextView) v.findViewById(
                    R.id.album_set_item_title);
            titleTextView.setText(cursor.getString(AlbumSetLoader.INDEX_TITLE));

            TextView dateTextView = (TextView) v.findViewById(
                    R.id.album_set_item_date);
            mDate.setTime(cursor.getLong(AlbumSetLoader.INDEX_TIMESTAMP));
            dateTextView.setText(DateFormat.getMediumDateFormat(context).format(mDate));

            ProgressBar uploadProgressBar = (ProgressBar) v.findViewById(
                    R.id.album_set_item_upload_progress);
            if (cursor.getInt(AlbumSetLoader.INDEX_COUNT_PENDING_UPLOAD) > 0) {
                uploadProgressBar.setVisibility(View.VISIBLE);
                uploadProgressBar.setProgress(50);
            } else {
                uploadProgressBar.setVisibility(View.INVISIBLE);
            }

            // TODO show the thumbnail
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = LayoutInflater.from(context).inflate(
                    R.layout.album_set_item, parent, false);
            ImageView thumbImageView = (ImageView) v.findViewById(
                    R.id.album_set_item_image);
            thumbImageView.setImageResource(android.R.color.darker_gray);
            return v;
        }
    }
}
