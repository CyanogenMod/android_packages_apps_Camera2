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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.android.gallery3d.R;
import com.android.photos.data.PhotoSetLoader;
import com.android.photos.drawables.DrawableFactory;
import com.android.photos.shims.MediaItemsLoader;
import com.android.photos.views.GalleryThumbnailView.GalleryThumbnailAdapter;


public class PhotoSetFragment extends Fragment implements LoaderCallbacks<Cursor> {

    private static final int LOADER_PHOTOSET = 1;

    private GridView mPhotoSetView;
    private View mEmptyView;
    private ThumbnailAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.photo_set, container, false);
        mPhotoSetView = (GridView) root.findViewById(android.R.id.list);
        // TODO: Remove once UI stabilizes
        mPhotoSetView.setColumnWidth(MediaItemsLoader.getThumbnailSize());
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
        // TODO: Switch to PhotoSetLoader
        MediaItemsLoader loader = new MediaItemsLoader(getActivity());
        mAdapter.setDrawableFactory(loader);
        return loader;
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
        private LayoutInflater mInflater;
        private DrawableFactory<Cursor> mDrawableFactory;

        public ThumbnailAdapter(Context context) {
            super(context, null, false);
            mInflater = LayoutInflater.from(context);
        }

        public void setDrawableFactory(DrawableFactory<Cursor> factory) {
            mDrawableFactory = factory;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ImageView iv = (ImageView) view;
            Drawable recycle = iv.getDrawable();
            Drawable drawable = mDrawableFactory.drawableForItem(cursor, recycle);
            if (recycle != drawable) {
                iv.setImageDrawable(drawable);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.photo_set_item, parent, false);
            LayoutParams params = view.getLayoutParams();
            int columnWidth = ((GridView) parent).getColumnWidth();
            params.height = columnWidth;
            view.setLayoutParams(params);
            return view;
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
