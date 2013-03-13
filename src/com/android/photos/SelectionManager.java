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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.provider.MediaStore.Files.FileColumns;
import android.widget.ShareActionProvider;

import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.util.GalleryUtils;

import java.util.ArrayList;

public class SelectionManager {
    private Activity mActivity;
    private NfcAdapter mNfcAdapter;
    private SelectedUriSource mUriSource;
    private Intent mShareIntent = new Intent();

    public interface SelectedUriSource {
        public ArrayList<Uri> getSelectedShareableUris();
    }

    public interface Client {
        public void setSelectionManager(SelectionManager manager);
    }

    public SelectionManager(Activity activity) {
        mActivity = activity;
        if (ApiHelper.AT_LEAST_16) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(mActivity);
            mNfcAdapter.setBeamPushUrisCallback(new CreateBeamUrisCallback() {
                @Override
                public Uri[] createBeamUris(NfcEvent arg0) {
                 // This will have been preceded by a call to onItemSelectedStateChange
                    if (mCachedShareableUris == null) return null;
                    return mCachedShareableUris.toArray(
                            new Uri[mCachedShareableUris.size()]);
                }
            }, mActivity);
        }
    }

    public void setSelectedUriSource(SelectedUriSource source) {
        mUriSource = source;
    }

    private int mSelectedTotalCount = 0;
    private int mSelectedShareableCount = 0;
    private int mSelectedShareableImageCount = 0;
    private int mSelectedShareableVideoCount = 0;
    private int mSelectedDeletableCount = 0;

    private ArrayList<Uri> mCachedShareableUris = null;

    public void onItemSelectedStateChanged(ShareActionProvider share,
            int itemType, int itemSupportedOperations, boolean selected) {
        int increment = selected ? 1 : -1;

        mSelectedTotalCount += increment;
        mCachedShareableUris = null;

        if ((itemSupportedOperations & MediaObject.SUPPORT_DELETE) > 0) {
            mSelectedDeletableCount += increment;
        }
        if ((itemSupportedOperations & MediaObject.SUPPORT_SHARE) > 0) {
            mSelectedShareableCount += increment;
            if (itemType == FileColumns.MEDIA_TYPE_IMAGE) {
                mSelectedShareableImageCount += increment;
            } else if (itemType == FileColumns.MEDIA_TYPE_VIDEO) {
                mSelectedShareableVideoCount += increment;
            }
        }

        mShareIntent.removeExtra(Intent.EXTRA_STREAM);
        if (mSelectedShareableCount == 0) {
            mShareIntent.setAction(null).setType(null);
        } else if (mSelectedShareableCount >= 1) {
            mCachedShareableUris = mUriSource.getSelectedShareableUris();
            if (mCachedShareableUris.size() == 0) {
                mShareIntent.setAction(null).setType(null);
            } else {
                if (mSelectedShareableImageCount == mSelectedShareableCount) {
                    mShareIntent.setType(GalleryUtils.MIME_TYPE_IMAGE);
                } else if (mSelectedShareableVideoCount == mSelectedShareableCount) {
                    mShareIntent.setType(GalleryUtils.MIME_TYPE_VIDEO);
                } else {
                    mShareIntent.setType(GalleryUtils.MIME_TYPE_ALL);
                }
                if (mCachedShareableUris.size() == 1) {
                    mShareIntent.setAction(Intent.ACTION_SEND);
                    mShareIntent.putExtra(Intent.EXTRA_STREAM, mCachedShareableUris.get(0));
                } else {
                    mShareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                    mShareIntent.putExtra(Intent.EXTRA_STREAM, mCachedShareableUris);
                }
            }
        }
        share.setShareIntent(mShareIntent);

        // TODO update editability, etc.
    }

    public int getSupportedOperations() {
        if (mSelectedTotalCount == 0) {
            return 0;
        }
        int supported = 0;
        if (mSelectedDeletableCount == mSelectedTotalCount) {
            supported |= MediaObject.SUPPORT_DELETE;
        }
        if (mSelectedShareableCount > 0) {
            supported |= MediaObject.SUPPORT_SHARE;
        }
        return supported;
    }

    public void onClearSelection() {
        mSelectedTotalCount = 0;
        mSelectedShareableCount = 0;
        mSelectedShareableImageCount = 0;
        mSelectedShareableVideoCount = 0;
        mSelectedDeletableCount = 0;
        mCachedShareableUris = null;
        mShareIntent.removeExtra(Intent.EXTRA_STREAM);
        mShareIntent.setAction(null).setType(null);
    }
}
