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

package com.android.camera.data;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;

import com.android.camera.data.FilmstripItem.VideoClickedCallback;
import com.google.common.base.Optional;

/**
 * A {@link LocalFilmstripDataAdapter} which puts a {@link FilmstripItem} fixed at the last
 * position. It's done by combining a {@link FilmstripItem} and another
 * {@link LocalFilmstripDataAdapter}.
 */
public class FixedLastProxyAdapter extends FilmstripDataAdapterProxy {
    private FilmstripItem mLastData;
    private Listener mListener;

    /**
     * Constructor.
     *
     * @param context A valid Android context.
     * @param wrappedAdapter The {@link LocalFilmstripDataAdapter} to be wrapped.
     * @param lastData The {@link FilmstripItem} to be placed at the last position.
     */
    public FixedLastProxyAdapter(
          Context context,
          LocalFilmstripDataAdapter wrappedAdapter,
          FilmstripItem lastData) {
        super(context, wrappedAdapter);
        if (lastData == null) {
            throw new AssertionError("data is null");
        }
        mLastData = lastData;
    }

    @Override
    public void setListener(Listener listener) {
        super.setListener(listener);
        mListener = listener;
    }

    @Override
    public FilmstripItem getItemAt(int index) {
        int totalNumber = mAdapter.getTotalNumber();

        if (index < totalNumber) {
            return mAdapter.getItemAt(index);
        } else if (index == totalNumber) {
            return mLastData;
        }
        return null;
    }

    @Override
    public void removeAt(int index) {
        if (index < mAdapter.getTotalNumber()) {
            mAdapter.removeAt(index);
        }
    }

    @Override
    public int findByContentUri(Uri uri) {
        return mAdapter.findByContentUri(uri);
    }

    @Override
    public void updateItemAt(final int pos, FilmstripItem item) {
        int totalNumber = mAdapter.getTotalNumber();

        if (pos < totalNumber) {
            mAdapter.updateItemAt(pos, item);
        } else if (pos == totalNumber) {
            mLastData = item;
            if (mListener != null) {
                mListener.onFilmstripItemUpdated(new UpdateReporter() {
                    @Override
                    public boolean isDataRemoved(int index) {
                        return false;
                    }

                    @Override
                    public boolean isDataUpdated(int index) {
                        return (index == pos);
                    }
                });
            }
        }
    }

    @Override
    public int getTotalNumber() {
        return mAdapter.getTotalNumber() + 1;
    }

    @Override
    public View getView(View recycled, int index, VideoClickedCallback videoClickedCallback) {
        int totalNumber = mAdapter.getTotalNumber();

        if (index < totalNumber) {
            return mAdapter.getView(recycled, index, videoClickedCallback);
        } else if (index == totalNumber) {
            mLastData.setSuggestedSize(mSuggestedWidth, mSuggestedHeight);
            return mLastData.getView(Optional.fromNullable(recycled), null, false,
                  videoClickedCallback);
        }
        return null;
    }

    @Override
    public int getItemViewType(int index) {
        int totalNumber = mAdapter.getTotalNumber();

        if (index < totalNumber) {
            return mAdapter.getItemViewType(index);
        } else if (index == totalNumber) {
            return mLastData.getItemViewType().ordinal();
        }
        return -1;
   }

    @Override
    public FilmstripItem getFilmstripItemAt(int index) {
        int totalNumber = mAdapter.getTotalNumber();

        if (index < totalNumber) {
            return mAdapter.getFilmstripItemAt(index);
        } else if (index == totalNumber) {
            return mLastData;
        }
        return null;
    }

    @Override
    public AsyncTask updateMetadataAt(int index) {
        if (index < mAdapter.getTotalNumber()) {
            return mAdapter.updateMetadataAt(index);
        } else {
            MetadataLoader.loadMetadata(mContext, mLastData);
        }
        return null;
    }

    @Override
    public boolean isMetadataUpdatedAt(int index) {
        if (index < mAdapter.getTotalNumber()) {
            return mAdapter.isMetadataUpdatedAt(index);
        } else {
            return mLastData.getMetadata().isLoaded();
        }
    }
}
