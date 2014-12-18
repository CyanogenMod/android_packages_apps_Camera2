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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.CamcorderProfile;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.data.LocalDataQuery.CursorToLocalDataFactory;
import com.android.camera.debug.Log;
import com.android.camera2.R;
import com.bumptech.glide.Glide;

import java.util.Date;
import java.util.List;

public class VideoData extends LocalMediaData {
    private static final Log.Tag TAG = new Log.Tag("PhotoData");
    static final Uri CONTENT_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

    private static final int mSupportedUIActions = ACTION_DEMOTE | ACTION_PROMOTE;
    private static final int mSupportedDataActions =
          DATA_ACTION_DELETE | DATA_ACTION_PLAY | DATA_ACTION_SHARE;


    /** The duration in milliseconds. */
    private final long mDurationInSeconds;
    private final String mSignature;

    public VideoData(long id, String title, String mimeType,
          long dateTakenInMilliSeconds, long dateModifiedInSeconds,
          String path, int width, int height, long sizeInBytes,
          double latitude, double longitude, long durationInSeconds) {
        super(id, title, mimeType, dateTakenInMilliSeconds, dateModifiedInSeconds,
              path, width, height, sizeInBytes, latitude, longitude);
        mDurationInSeconds = durationInSeconds;
        mSignature = mimeType + dateModifiedInSeconds;
    }

    /**
     * We can't trust the media store and we can't afford the performance overhead of
     * synchronously decoding the video header for every item when loading our data set
     * from the media store, so we instead run the metadata loader in the background
     * to decode the video header for each item and prefer whatever values it obtains.
     */
    private int getBestWidth() {
        int metadataWidth = VideoRotationMetadataLoader.getWidth(this);
        if (metadataWidth > 0) {
            return metadataWidth;
        } else {
            return mWidth;
        }
    }

    private int getBestHeight() {
        int metadataHeight = VideoRotationMetadataLoader.getHeight(this);
        if (metadataHeight > 0) {
            return metadataHeight;
        } else {
            return mHeight;
        }
    }

    /**
     * If the metadata loader has determined from the video header that we need to rotate the video
     * 90 or 270 degrees, then we swap the width and height.
     */
    @Override
    public int getWidth() {
        return VideoRotationMetadataLoader.isRotated(this) ? getBestHeight() : getBestWidth();
    }

    @Override
    public int getHeight() {
        return VideoRotationMetadataLoader.isRotated(this) ?  getBestWidth() : getBestHeight();
    }

    @Override
    public String toString() {
        return "Video:" + ",data=" + mPath + ",mimeType=" + mMimeType
              + "," + mWidth + "x" + mHeight + ",date=" + new Date(mDateTakenInMilliSeconds);
    }

    @Override
    public int getViewType() {
        return VIEW_TYPE_REMOVABLE;
    }

    @Override
    public boolean isUIActionSupported(int action) {
        return ((action & mSupportedUIActions) == action);
    }

    @Override
    public boolean isDataActionSupported(int action) {
        return ((action & mSupportedDataActions) == action);
    }

    @Override
    public boolean delete(Context context) {
        ContentResolver cr = context.getContentResolver();
        cr.delete(CONTENT_URI, MediaStore.Video.VideoColumns._ID + "=" + mContentId, null);
        return super.delete(context);
    }

    @Override
    public Uri getUri() {
        Uri baseUri = CONTENT_URI;
        return baseUri.buildUpon().appendPath(String.valueOf(mContentId)).build();
    }

    @Override
    public MediaDetails getMediaDetails(Context context) {
        MediaDetails mediaDetails = super.getMediaDetails(context);
        String duration = MediaDetails.formatDuration(context, mDurationInSeconds);
        mediaDetails.addDetail(MediaDetails.INDEX_DURATION, duration);
        return mediaDetails;
    }

    @Override
    public int getLocalDataType() {
        return LOCAL_VIDEO;
    }

    @Override
    public LocalData refresh(Context context) {
        return new VideoDataFactory().get(context, getUri());
    }

    @Override
    public String getSignature() {
        return mSignature;
    }

    @Override
    protected ImageView fillImageView(Context context, final ImageView v, final int thumbWidth,
          final int thumbHeight, int placeHolderResourceId, LocalDataAdapter adapter,
          boolean isInProgress) {

        //TODO: Figure out why these can be <= 0.
        if (thumbWidth <= 0 || thumbHeight <=0) {
            return v;
        }

        Glide.with(context)
              .loadFromMediaStore(getUri())
              .thumbnail(Glide.with(context)
                    .loadFromMediaStore(getUri())
                    .override(MEDIASTORE_THUMB_WIDTH, MEDIASTORE_THUMB_HEIGHT))
              .placeholder(placeHolderResourceId)
              .fitCenter()
              .override(thumbWidth, thumbHeight)
              .into(v);

        return v;
    }

    @Override
    public View getView(final Context context, View recycled,
          int thumbWidth, int thumbHeight, int placeHolderResourceId,
          LocalDataAdapter adapter, boolean isInProgress,
          final ActionCallback actionCallback) {

        final VideoViewHolder viewHolder;
        final View result;
        if (recycled != null) {
            result = recycled;
            viewHolder = (VideoViewHolder) recycled.getTag(R.id.mediadata_tag_target);
        } else {
            result = LayoutInflater.from(context).inflate(R.layout.filmstrip_video, null);
            result.setTag(R.id.mediadata_tag_viewtype, getItemViewType().ordinal());
            ImageView videoView = (ImageView) result.findViewById(R.id.video_view);
            ImageView playButton = (ImageView) result.findViewById(R.id.play_button);
            viewHolder = new VideoViewHolder(videoView, playButton);
            result.setTag(R.id.mediadata_tag_target, viewHolder);
        }

        fillImageView(context, viewHolder.mVideoView, thumbWidth, thumbHeight,
              placeHolderResourceId, adapter, isInProgress);

        // ImageView for the play icon.
        viewHolder.mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionCallback.playVideo(getUri(), mTitle);
            }
        });

        result.setContentDescription(context.getResources().getString(
              R.string.video_date_content_description,
              getReadableDate(mDateModifiedInSeconds)));

        return result;
    }

    @Override
    public void recycle(View view) {
        super.recycle(view);
        VideoViewHolder videoViewHolder =
              (VideoViewHolder) view.getTag(R.id.mediadata_tag_target);
        Glide.clear(videoViewHolder.mVideoView);
    }

    @Override
    public LocalDataViewType getItemViewType() {
        return LocalDataViewType.VIDEO;
    }

    public static class VideoDataFactory implements CursorToLocalDataFactory {
        public static final int COL_ID = 0;
        public static final int COL_TITLE = 1;
        public static final int COL_MIME_TYPE = 2;
        public static final int COL_DATE_TAKEN = 3;
        public static final int COL_DATE_MODIFIED = 4;
        public static final int COL_DATA = 5;
        public static final int COL_WIDTH = 6;
        public static final int COL_HEIGHT = 7;
        public static final int COL_SIZE = 8;
        public static final int COL_LATITUDE = 9;
        public static final int COL_LONGITUDE = 10;
        public static final int COL_DURATION = 11;

        private static final String QUERY_ORDER = MediaStore.Video.VideoColumns.DATE_TAKEN
              + " DESC, " + MediaStore.Video.VideoColumns._ID + " DESC";
        /**
         * These values should be kept in sync with column IDs (COL_*) above.
         */
        private static final String[] QUERY_PROJECTION = {
              MediaStore.Video.VideoColumns._ID,           // 0, int
              MediaStore.Video.VideoColumns.TITLE,         // 1, string
              MediaStore.Video.VideoColumns.MIME_TYPE,     // 2, string
              MediaStore.Video.VideoColumns.DATE_TAKEN,    // 3, int
              MediaStore.Video.VideoColumns.DATE_MODIFIED, // 4, int
              MediaStore.Video.VideoColumns.DATA,          // 5, string
              MediaStore.Video.VideoColumns.WIDTH,         // 6, int
              MediaStore.Video.VideoColumns.HEIGHT,        // 7, int
              MediaStore.Video.VideoColumns.SIZE,          // 8 long
              MediaStore.Video.VideoColumns.LATITUDE,      // 9 double
              MediaStore.Video.VideoColumns.LONGITUDE,     // 10 double
              MediaStore.Video.VideoColumns.DURATION       // 11 long
        };

        @Override
        public VideoData get(Cursor c) {
            long id = c.getLong(COL_ID);
            String title = c.getString(COL_TITLE);
            String mimeType = c.getString(COL_MIME_TYPE);
            long dateTakenInMilliSeconds = c.getLong(COL_DATE_TAKEN);
            long dateModifiedInSeconds = c.getLong(COL_DATE_MODIFIED);
            String path = c.getString(COL_DATA);
            int width = c.getInt(COL_WIDTH);
            int height = c.getInt(COL_HEIGHT);

            // If the media store doesn't contain a width and a height, use the width and height
            // of the default camera mode instead. When the metadata loader runs, it will set the
            // correct values.
            if (width == 0 || height == 0) {
                Log.w(TAG, "failed to retrieve width and height from the media store, defaulting " +
                      " to camera profile");
                CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                width = profile.videoFrameWidth;
                height = profile.videoFrameHeight;
            }

            long sizeInBytes = c.getLong(COL_SIZE);
            double latitude = c.getDouble(COL_LATITUDE);
            double longitude = c.getDouble(COL_LONGITUDE);
            long durationInSeconds = c.getLong(COL_DURATION) / 1000;
            return new VideoData(id, title, mimeType, dateTakenInMilliSeconds,
                  dateModifiedInSeconds, path, width, height, sizeInBytes,
                  latitude, longitude, durationInSeconds);
        }

        /** Query for a single video data item */
        public VideoData get(Context context, Uri uri) {
            VideoData newData = null;
            Cursor c = context.getContentResolver().query(uri, QUERY_PROJECTION, null,
                  null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    newData = get(c);
                }
                c.close();
            }

            return newData;
        }

        /** Query for all the video data items */
        public static List<LocalData> queryAll(ContentResolver cr) {
            return queryAll(cr, VideoData.CONTENT_URI, LocalMediaData.QUERY_ALL_MEDIA_ID);
        }

        /** Query for all the video data items */
        public static List<LocalData> queryAll(ContentResolver cr, Uri uri, long lastId) {
            return LocalDataQuery.forCameraPath(cr, uri, QUERY_PROJECTION, lastId, QUERY_ORDER,
                  new VideoDataFactory());
        }

        /** Query for a single data item */
        public static LocalData queryContentUri(ContentResolver cr, Uri uri) {
            // TODO: Consider refactoring this, this approach may be slow.
            List<LocalData> videos = queryAll(cr, uri, QUERY_ALL_MEDIA_ID);
            if (videos.isEmpty()) {
                return null;
            }
            return videos.get(0);
        }
    }

    private static class VideoViewHolder {
        private final ImageView mVideoView;
        private final ImageView mPlayButton;

        public VideoViewHolder(ImageView videoView, ImageView playButton) {
            mVideoView = videoView;
            mPlayButton = playButton;
        }
    }
}
