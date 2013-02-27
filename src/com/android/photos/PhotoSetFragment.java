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
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.android.gallery3d.R;
import com.android.photos.data.PhotoSetLoader;
import com.android.photos.drawables.AutoThumbnailDrawable;
import com.android.photos.views.GalleryThumbnailView;
import com.android.photos.views.GalleryThumbnailView.GalleryThumbnailAdapter;


public class PhotoSetFragment extends Fragment implements LoaderCallbacks<Cursor> {

    private static final int LOADER_PHOTOSET = 1;

    private GalleryThumbnailView mPhotoSetView;
    private View mEmptyView;
    private ThumbnailAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.photo_set, container, false);
        mPhotoSetView = (GalleryThumbnailView) root.findViewById(android.R.id.list);
        mEmptyView = root.findViewById(android.R.id.empty);
        mEmptyView.setVisibility(View.GONE);
        mAdapter = new ThumbnailAdapter(getActivity());
        mPhotoSetView.setAdapter(mAdapter);
        getLoaderManager().initLoader(LOADER_PHOTOSET, null, this);
        updateEmptyStatus();
        return root;
    }

    private void updateEmptyStatus() {
        boolean empty = (mAdapter == null || mAdapter.getCount() == 0);
        mPhotoSetView.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new PhotoSetLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader,
            Cursor data) {
        mAdapter.swapCursor(data);
        updateEmptyStatus();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private static class ThumbnailAdapter extends CursorAdapter implements GalleryThumbnailAdapter {

        public ThumbnailAdapter(Context context) {
            super(context, null, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ImageView iv = (ImageView) view;
            AutoThumbnailDrawable drawable = (AutoThumbnailDrawable) iv.getDrawable();
            int width = cursor.getInt(PhotoSetLoader.INDEX_WIDTH);
            int height = cursor.getInt(PhotoSetLoader.INDEX_HEIGHT);
            String path = cursor.getString(PhotoSetLoader.INDEX_DATA);
            drawable.setImage(path, width, height);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            ImageView iv = new ImageView(context);
            AutoThumbnailDrawable drawable = new AutoThumbnailDrawable();
            iv.setImageDrawable(drawable);
            int padding = (int) Math.ceil(2 * context.getResources().getDisplayMetrics().density);
            iv.setPadding(padding, padding, padding, padding);
            return iv;
        }

        @Override
        public float getIntrinsicAspectRatio(int position) {
            Cursor cursor = getItem(position);
            float width = cursor.getInt(PhotoSetLoader.INDEX_WIDTH);
            float height = cursor.getInt(PhotoSetLoader.INDEX_HEIGHT);
            return width / height;
        }

        @Override
        public Cursor getItem(int position) {
            return (Cursor) super.getItem(position);
        }
    }
}
