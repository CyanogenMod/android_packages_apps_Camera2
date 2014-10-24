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

import com.android.camera.data.LocalData.ActionCallback;
import com.android.camera.debug.Log;
import com.android.camera.filmstrip.DataAdapter;
import com.android.camera.filmstrip.ImageData;

/**
 * A {@link LocalDataAdapter} which puts a {@link LocalData} fixed at the first
 * position. It's done by combining a {@link LocalData} and another
 * {@link LocalDataAdapter}.
 */
public class FixedFirstDataAdapter extends AbstractLocalDataAdapterWrapper
        implements DataAdapter.Listener {

    @SuppressWarnings("unused")
    private static final Log.Tag TAG = new Log.Tag("FixedFirstDataAdpt");

    private LocalData mFirstData;
    private Listener mListener;

    /**
     * Constructor.
     *
     * @param context Valid Android context.
     * @param wrappedAdapter The {@link LocalDataAdapter} to be wrapped.
     * @param firstData The {@link LocalData} to be placed at the first
     *            position.
     */
    public FixedFirstDataAdapter(
            Context context,
            LocalDataAdapter wrappedAdapter,
            LocalData firstData) {
        super(context, wrappedAdapter);
        if (firstData == null) {
            throw new AssertionError("data is null");
        }
        mFirstData = firstData;
    }

    @Override
    public LocalData getLocalData(int dataID) {
        if (dataID == 0) {
            return mFirstData;
        }
        return mAdapter.getLocalData(dataID - 1);
    }

    @Override
    public void removeData(int dataID) {
        if (dataID > 0) {
            mAdapter.removeData(dataID - 1);
        }
    }

    @Override
    public int findDataByContentUri(Uri uri) {
        int pos = mAdapter.findDataByContentUri(uri);
        if (pos != -1) {
            return pos + 1;
        }
        return -1;
    }

    @Override
    public void updateData(int pos, LocalData data) {
        if (pos == 0) {
            mFirstData = data;
            if (mListener != null) {
                mListener.onDataUpdated(new UpdateReporter() {
                    @Override
                    public boolean isDataRemoved(int dataID) {
                        return false;
                    }

                    @Override
                    public boolean isDataUpdated(int dataID) {
                        return (dataID == 0);
                    }
                });
            }
        } else {
            mAdapter.updateData(pos - 1, data);
        }
    }

    @Override
    public int getTotalNumber() {
        return (mAdapter.getTotalNumber() + 1);
    }

    @Override
    public View getView(Context context, View recycled, int dataID, ActionCallback actionCallback) {
        if (dataID == 0) {
            return mFirstData.getView(context, recycled, mSuggestedWidth, mSuggestedHeight, 0,
                    null, false, actionCallback);
        }
        return mAdapter.getView(context, recycled, dataID - 1, actionCallback);
    }

    @Override
    public int getItemViewType(int dataId) {
        if (dataId == 0) {
            return mFirstData.getItemViewType().ordinal();
        }
        return mAdapter.getItemViewType(dataId);
    }

    @Override
    public void resizeView(Context context, int dataID, View view, int w, int h) {
        // Do nothing.
    }

    @Override
    public ImageData getImageData(int dataID) {
        if (dataID == 0) {
            return mFirstData;
        }
        return mAdapter.getImageData(dataID - 1);
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
        mAdapter.setListener((listener == null) ? null : this);
        // The first data is always there. Thus, When the listener is set,
        // we should call listener.onDataLoaded().
        if (mListener != null) {
            mListener.onDataLoaded();
        }
    }

    @Override
    public boolean canSwipeInFullScreen(int dataID) {
        if (dataID == 0) {
            return mFirstData.canSwipeInFullScreen();
        }
        return mAdapter.canSwipeInFullScreen(dataID - 1);
    }

    @Override
    public void onDataLoaded() {
        if (mListener == null) {
            return;
        }
        mListener.onDataUpdated(new UpdateReporter() {
            @Override
            public boolean isDataRemoved(int dataID) {
                return false;
            }

            @Override
            public boolean isDataUpdated(int dataID) {
                return (dataID != 0);
            }
        });
    }

    @Override
    public void onDataUpdated(final UpdateReporter reporter) {
        mListener.onDataUpdated(new UpdateReporter() {
            @Override
            public boolean isDataRemoved(int dataID) {
                return (dataID != 0) && reporter.isDataRemoved(dataID - 1);
            }

            @Override
            public boolean isDataUpdated(int dataID) {
                return (dataID != 0) && reporter.isDataUpdated(dataID - 1);
            }
        });
    }

    @Override
    public void onDataInserted(int dataID, ImageData data) {
        mListener.onDataInserted(dataID + 1, data);
    }

    @Override
    public void onDataRemoved(int dataID, ImageData data) {
        mListener.onDataRemoved(dataID + 1, data);
    }

    @Override
    public AsyncTask updateMetadata(int dataId) {
        if (dataId > 0) {
            return mAdapter.updateMetadata(dataId - 1);
        } else {
            MetadataLoader.loadMetadata(mContext, mFirstData);
        }
        return null;
    }

    @Override
    public boolean isMetadataUpdated(int dataId) {
        if (dataId > 0) {
        return mAdapter.isMetadataUpdated(dataId - 1);
        } else {
            return mFirstData.isMetadataUpdated();
        }
    }
}
