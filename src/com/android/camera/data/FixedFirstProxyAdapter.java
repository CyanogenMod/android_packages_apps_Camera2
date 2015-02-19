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
import com.android.camera.debug.Log;
import com.android.camera.filmstrip.FilmstripDataAdapter;
import com.google.common.base.Optional;

/**
 * A {@link LocalFilmstripDataAdapter} which puts a {@link FilmstripItem} fixed at the first
 * position. It's done by combining a {@link FilmstripItem} and another
 * {@link LocalFilmstripDataAdapter}.
 */
public class FixedFirstProxyAdapter extends FilmstripDataAdapterProxy
        implements FilmstripDataAdapter.Listener {
    @SuppressWarnings("unused")
    private static final Log.Tag TAG = new Log.Tag("FixedFirstDataAdpt");

    private FilmstripItem mFirstData;
    private Listener mListener;

    /**
     * Constructor.
     *
     * @param context Valid Android context.
     * @param wrappedAdapter The {@link LocalFilmstripDataAdapter} to be wrapped.
     * @param firstData The {@link FilmstripItem} to be placed at the first
     *            position.
     */
    public FixedFirstProxyAdapter(
          Context context,
          LocalFilmstripDataAdapter wrappedAdapter,
          FilmstripItem firstData) {
        super(context, wrappedAdapter);
        if (firstData == null) {
            throw new AssertionError("data is null");
        }
        mFirstData = firstData;
    }

    @Override
    public FilmstripItem getItemAt(int index) {
        if (index == 0) {
            return mFirstData;
        }
        return mAdapter.getItemAt(index - 1);
    }

    @Override
    public void removeAt(int index) {
        if (index > 0) {
            mAdapter.removeAt(index - 1);
        }
    }

    @Override
    public int findByContentUri(Uri uri) {
        int pos = mAdapter.findByContentUri(uri);
        if (pos != -1) {
            return pos + 1;
        }
        return -1;
    }

    @Override
    public void updateItemAt(int pos, FilmstripItem item) {
        if (pos == 0) {
            mFirstData = item;
            if (mListener != null) {
                mListener.onFilmstripItemUpdated(new UpdateReporter() {
                    @Override
                    public boolean isDataRemoved(int index) {
                        return false;
                    }

                    @Override
                    public boolean isDataUpdated(int index) {
                        return (index == 0);
                    }
                });
            }
        } else {
            mAdapter.updateItemAt(pos - 1, item);
        }
    }

    @Override
    public int getTotalNumber() {
        return (mAdapter.getTotalNumber() + 1);
    }

    @Override
    public View getView(View recycled, int index, VideoClickedCallback videoClickedCallback) {
        if (index == 0) {
            mFirstData.setSuggestedSize(mSuggestedWidth, mSuggestedHeight);
            return mFirstData.getView(Optional.fromNullable(recycled), null, false,
                  videoClickedCallback);
        }
        return mAdapter.getView(recycled, index - 1, videoClickedCallback);
    }

    @Override
    public int getItemViewType(int index) {
        if (index == 0) {
            return mFirstData.getItemViewType().ordinal();
        }
        return mAdapter.getItemViewType(index);
    }

    @Override
    public FilmstripItem getFilmstripItemAt(int index) {
        if (index == 0) {
            return mFirstData;
        }
        return mAdapter.getFilmstripItemAt(index - 1);
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
        mAdapter.setListener((listener == null) ? null : this);
        // The first data is always there. Thus, When the listener is set,
        // we should call listener.onFilmstripItemLoaded().
        if (mListener != null) {
            mListener.onFilmstripItemLoaded();
        }
    }

    @Override
    public void onFilmstripItemLoaded() {
        if (mListener == null) {
            return;
        }
        mListener.onFilmstripItemUpdated(new UpdateReporter() {
            @Override
            public boolean isDataRemoved(int index) {
                return false;
            }

            @Override
            public boolean isDataUpdated(int index) {
                return (index != 0);
            }
        });
    }

    @Override
    public void onFilmstripItemUpdated(final UpdateReporter reporter) {
        mListener.onFilmstripItemUpdated(new UpdateReporter() {
            @Override
            public boolean isDataRemoved(int index) {
                return (index != 0) && reporter.isDataRemoved(index - 1);
            }

            @Override
            public boolean isDataUpdated(int index) {
                return (index != 0) && reporter.isDataUpdated(index - 1);
            }
        });
    }

    @Override
    public void onFilmstripItemInserted(int index, FilmstripItem item) {
        mListener.onFilmstripItemInserted(index + 1, item);
    }

    @Override
    public void onFilmstripItemRemoved(int index, FilmstripItem item) {
        mListener.onFilmstripItemRemoved(index + 1, item);
    }

    @Override
    public AsyncTask updateMetadataAt(int index) {
        if (index > 0) {
            return mAdapter.updateMetadataAt(index - 1);
        } else {
            MetadataLoader.loadMetadata(mContext, mFirstData);
        }
        return null;
    }

    @Override
    public boolean isMetadataUpdatedAt(int index) {
        if (index > 0) {
            return mAdapter.isMetadataUpdatedAt(index - 1);
        } else {
            return mFirstData.getMetadata().isLoaded();
        }
    }
}
