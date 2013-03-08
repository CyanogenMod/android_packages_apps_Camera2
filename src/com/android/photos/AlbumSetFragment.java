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
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
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
import com.android.photos.shims.LoaderCompatShim;
import com.android.photos.shims.MediaSetLoader;

import java.util.Date;


public class AlbumSetFragment extends Fragment implements OnItemClickListener,
    LoaderCallbacks<Cursor> {

    private GridView mAlbumSetView;
    private View mEmptyView;
    private AlbumSetCursorAdapter mAdapter;

    private static final int LOADER_ALBUMSET = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new AlbumSetCursorAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.album_set, container, false);
        mAlbumSetView = (GridView) root.findViewById(android.R.id.list);
        mEmptyView = root.findViewById(android.R.id.empty);
        mEmptyView.setVisibility(View.GONE);
        mAlbumSetView.setAdapter(mAdapter);
        mAlbumSetView.setOnItemClickListener(this);
        getLoaderManager().initLoader(LOADER_ALBUMSET, null, this);
        updateEmptyStatus();
        return root;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // TODO: Switch to AlbumSetLoader
        MediaSetLoader loader = new MediaSetLoader(getActivity());
        mAdapter.setDrawableFactory(loader);
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader,
            Cursor data) {
        mAdapter.swapCursor(data);
        updateEmptyStatus();
    }

    private void updateEmptyStatus() {
        boolean empty = (mAdapter == null || mAdapter.getCount() == 0);
        mAlbumSetView.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public void onItemClick(AdapterView<?> av, View v, int pos, long id) {
        Cursor c = (Cursor) av.getItemAtPosition(pos);
        int albumId = c.getInt(AlbumSetLoader.INDEX_ID);
        // TODO launch an activity showing the photos in the album
        Toast.makeText(v.getContext(), "Clicked " + albumId, Toast.LENGTH_SHORT).show();
    }

    private static class AlbumSetCursorAdapter extends CursorAdapter {

        private LoaderCompatShim<Cursor> mDrawableFactory;

        public void setDrawableFactory(LoaderCompatShim<Cursor> factory) {
            mDrawableFactory = factory;
        }
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
            long timestamp = cursor.getLong(AlbumSetLoader.INDEX_TIMESTAMP);
            if (timestamp > 0) {
                mDate.setTime(timestamp);
                dateTextView.setText(DateFormat.getMediumDateFormat(context).format(mDate));
            } else {
                dateTextView.setText(null);
            }

            ProgressBar uploadProgressBar = (ProgressBar) v.findViewById(
                    R.id.album_set_item_upload_progress);
            if (cursor.getInt(AlbumSetLoader.INDEX_COUNT_PENDING_UPLOAD) > 0) {
                uploadProgressBar.setVisibility(View.VISIBLE);
                uploadProgressBar.setProgress(50);
            } else {
                uploadProgressBar.setVisibility(View.INVISIBLE);
            }

            ImageView thumbImageView = (ImageView) v.findViewById(
                    R.id.album_set_item_image);
            Drawable recycle = thumbImageView.getDrawable();
            Drawable drawable = mDrawableFactory.drawableForItem(cursor, recycle);
            if (recycle != drawable) {
                thumbImageView.setImageDrawable(drawable);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(
                    R.layout.album_set_item, parent, false);
        }
    }
}
