// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.pano.data;

import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a home screen cluster.
 */
public class Cluster {

    private long mId;
    private String mName;
    private CharSequence mDisplayName;
    private int mImportance;
    private int mVisibleCount;
    private boolean mImageCropAllowed;
    private long mCacheTimeMs;
    private Intent mIntent;
    private Uri mBrowseItemsUri;

    private List<ClusterItem> mClusterItems;

    /**
     * An item displayed inside a cluster.
     */
    public static class ClusterItem {
        private Uri mImageUri;

        public ClusterItem(Uri imageUri) {
            mImageUri = imageUri;
        }

        public Uri getImageUri() {
            return mImageUri;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("imageUri: ").append(mImageUri);
            return builder.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mImageUri == null) ? 0 : mImageUri.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ClusterItem other = (ClusterItem) obj;
            if (mImageUri == null) {
                if (other.mImageUri != null)
                    return false;
            } else if (!mImageUri.equals(other.mImageUri))
                return false;
            return true;
        }
    }

    public Cluster() {
        mClusterItems = new ArrayList<ClusterItem>();
        mImageCropAllowed = true;
    }

    public long getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    public int getImportance() {
        return mImportance;
    }

    public int getVisibleCount() {
        return mVisibleCount;
    }

    public boolean isImageCropAllowed() {
        return mImageCropAllowed;
    }

    public long getCacheTimeMs() {
        return mCacheTimeMs;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public Uri getBrowseItemsUri() {
        return mBrowseItemsUri;
    }

    public int getItemCount() {
        return mClusterItems.size();
    }

    public ClusterItem getItem(int position) {
        if (position >= 0 && position < mClusterItems.size()) {
            return mClusterItems.get(position);
        }
        return null;
    }

    public void addClusterItem(ClusterItem item) {
        mClusterItems.add(item);
    }

    public void clearItems() {
        mClusterItems.clear();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (mCacheTimeMs ^ (mCacheTimeMs >>> 32));
        if (mClusterItems == null) {
            result = prime * result;
        } else {
            for (ClusterItem ci : mClusterItems) {
                result = prime * result + ci.hashCode();
            }
        }
        result = prime * result + ((mDisplayName == null) ? 0 : mDisplayName.toString().hashCode());
        result = prime * result + (int) (mId ^ (mId >>> 32));
        result = prime * result + (mImageCropAllowed ? 1231 : 1237);
        result = prime * result + mImportance;
        result = prime * result + ((mIntent == null) ? 0 : mIntent.hashCode());
        result = prime * result + ((mName == null) ? 0 : mName.hashCode());
        result = prime * result + mVisibleCount;
        result = prime * result + ((mBrowseItemsUri == null) ? 0 : mBrowseItemsUri.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Cluster other = (Cluster) obj;
        if (mCacheTimeMs != other.mCacheTimeMs)
            return false;
        if (mClusterItems == null) {
            if (other.mClusterItems != null)
                return false;
        } else if (!mClusterItems.equals(other.mClusterItems))
            return false;
        if (mDisplayName == null) {
            if (other.mDisplayName != null)
                return false;
        } else if (!mDisplayName.equals(other.mDisplayName))
            return false;
        if (mId != other.mId)
            return false;
        if (mImageCropAllowed != other.mImageCropAllowed)
            return false;
        if (mImportance != other.mImportance)
            return false;
        if (mIntent == null) {
            if (other.mIntent != null)
                return false;
        } else if (!mIntent.equals(other.mIntent))
            return false;
        if (mName == null) {
            if (other.mName != null)
                return false;
        } else if (!mName.equals(other.mName))
            return false;
        if (mVisibleCount != other.mVisibleCount)
            return false;
        if (mBrowseItemsUri == null) {
            if (other.mBrowseItemsUri != null)
                return false;
        } else if (!mBrowseItemsUri.equals(other.mBrowseItemsUri)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("id: ").append(mId)
                .append(", name: ").append(mName)
                .append(", displayName: ").append(mDisplayName)
                .append(", importance: ").append(mImportance)
                .append(", visibleCount: ").append(mVisibleCount)
                .append(", imageCropAllowed: ").append(mImageCropAllowed)
                .append(", cacheTimeMs: ").append(mCacheTimeMs)
                .append(", clusterItems: ").append(mClusterItems)
                .append(", intent: ").append(mIntent != null ? mIntent.toUri(0) : "")
                .append(", browseItems: ").append(mBrowseItemsUri != null ? mBrowseItemsUri : "");
        return builder.toString();
    }

    /**
     * Builds cluster objects.
     */
    public static class Builder {
        private long mId;
        private String mName;
        private CharSequence mDisplayName;
        private int mImportance;
        private int mVisibleCount;
        private boolean mImageCropAllowed;
        private long mCacheTimeMs;
        private Intent mIntent;
        private Uri mBrowseItemsUri;

        private List<ClusterItem> mClusterItems;

        public Cluster build() {
            Cluster cluster = new Cluster();
            cluster.mId = mId;
            cluster.mName = mName;
            cluster.mDisplayName = mDisplayName;
            cluster.mImportance = mImportance;
            cluster.mVisibleCount = mVisibleCount;
            cluster.mImageCropAllowed = mImageCropAllowed;
            cluster.mIntent = mIntent;
            cluster.mCacheTimeMs = mCacheTimeMs;
            cluster.mClusterItems.addAll(mClusterItems);
            cluster.mBrowseItemsUri = mBrowseItemsUri;
            return cluster;
        }

        public Builder() {
            mClusterItems = new ArrayList<ClusterItem>();
            mImageCropAllowed = true;
        }

        public Builder id(long id) {
            mId = id;
            return this;
        }

        public Builder name(String name) {
            mName = name;
            return this;
        }

        public Builder displayName(CharSequence displayName) {
            mDisplayName = displayName;
            return this;
        }

        public Builder importance(int importance) {
            mImportance = importance;
            return this;
        }

        public Builder visibleCount(int visibleCount) {
            mVisibleCount = visibleCount;
            return this;
        }

        public Builder imageCropAllowed(boolean allowed) {
            mImageCropAllowed = allowed;
            return this;
        }

        public Builder cacheTimeMs(long cacheTimeMs) {
            mCacheTimeMs = cacheTimeMs;
            return this;
        }

        public Builder intent(Intent intent) {
            mIntent = intent;
            return this;
        }

        public Builder browseItemsUri(Uri uri) {
            mBrowseItemsUri = uri;
            return this;
        }

        public Builder addItem(Uri imageUri) {
            ClusterItem item = new ClusterItem(imageUri);
            mClusterItems.add(item);
            return this;
        }
    }
}
