// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.canvas.data;

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

    private List<ClusterItem> mClusterItems;

    /**
     * An item displayed inside a cluster.
     */
    public static class ClusterItem {
        private Uri mImageUri;

        ClusterItem(Uri imageUri) {
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

    public int getItemCount() {
        return mClusterItems.size();
    }

    public ClusterItem getItem(int position) {
        if (position >= 0 && position < mClusterItems.size()) {
            return mClusterItems.get(position);
        }
        return null;
    }

    void addClusterItem(ClusterItem item) {
        mClusterItems.add(item);
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
                .append(", intent: ").append(mIntent.toUri(0));
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

        public Builder addItem(Uri imageUri) {
            ClusterItem item = new ClusterItem(imageUri);
            mClusterItems.add(item);
            return this;
        }
    }
}
