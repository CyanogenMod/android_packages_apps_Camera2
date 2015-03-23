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
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.data.FilmstripItemAttributes.Attributes;
import com.android.camera.debug.Log;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.bumptech.glide.Glide;
import com.google.common.base.Optional;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * Backing data for a single video displayed in the filmstrip.
 */
public class VideoItem extends FilmstripItemBase<VideoItemData> {
    private static class VideoViewHolder {
        private final ImageView mVideoView;
        private final ImageView mPlayButton;

        public VideoViewHolder(ImageView videoView, ImageView playButton) {
            mVideoView = videoView;
            mPlayButton = playButton;
        }
    }

    private static final Log.Tag TAG = new Log.Tag("VideoItem");

    private static final FilmstripItemAttributes VIDEO_ITEM_ATTRIBUTES =
          new FilmstripItemAttributes.Builder()
                .with(Attributes.CAN_SHARE)
                .with(Attributes.CAN_PLAY)
                .with(Attributes.CAN_DELETE)
                .with(Attributes.CAN_SWIPE_AWAY)
                .with(Attributes.HAS_DETAILED_CAPTURE_INFO)
                .with(Attributes.IS_VIDEO)
                .build();

    private final VideoItemFactory mVideoItemFactory;

    private Size mCachedSize;

    public VideoItem(Context context, GlideFilmstripManager manager, VideoItemData data,
          VideoItemFactory videoItemFactory) {
        super(context, manager, data, VIDEO_ITEM_ATTRIBUTES);
        mVideoItemFactory = videoItemFactory;
    }

    /**
     * We can't trust the media store and we can't afford the performance overhead of
     * synchronously decoding the video header for every item when loading our data set
     * from the media store, so we instead run the metadata loader in the background
     * to decode the video header for each item and prefer whatever values it obtains.
     */
    private int getBestWidth() {
        int metadataWidth = mMetaData.getVideoWidth();
        if (metadataWidth > 0) {
            return metadataWidth;
        } else {
            return mData.getDimensions().getWidth();
        }
    }

    private int getBestHeight() {
        int metadataHeight = mMetaData.getVideoHeight();
        if (metadataHeight > 0) {
            return metadataHeight;
        } else {
            return mData.getDimensions().getHeight();
        }
    }

    /**
     * If the metadata loader has determined from the video header that we need to rotate the video
     * 90 or 270 degrees, then we swap the width and height.
     */
    public int getWidth() {
        return mMetaData.isVideoRotated() ? getBestHeight() : getBestWidth();
    }

    public int getHeight() {
        return mMetaData.isVideoRotated() ?  getBestWidth() : getBestHeight();
    }

    @Override
    public Size getDimensions() {
        int width = getWidth();
        int height = getHeight();
        if (mCachedSize == null ||
                width != mCachedSize.getWidth() || height != mCachedSize.getHeight()) {
            mCachedSize = new Size(width, height);
        }
        return mCachedSize;
    }

    @Override
    public boolean delete() {
        ContentResolver cr = mContext.getContentResolver();
        cr.delete(VideoDataQuery.CONTENT_URI,
              MediaStore.Video.VideoColumns._ID + "=" + mData.getContentId(), null);
        return super.delete();
    }

    @Override
    public Optional<MediaDetails> getMediaDetails() {
        Optional<MediaDetails> optionalDetails = super.getMediaDetails();
        if (optionalDetails.isPresent()) {
            MediaDetails mediaDetails = optionalDetails.get();
            String duration = MediaDetails.formatDuration(mContext,
                    TimeUnit.MILLISECONDS.toSeconds(mData.getVideoDurationMillis()));
            mediaDetails.addDetail(MediaDetails.INDEX_DURATION, duration);
        }
        return optionalDetails;
    }

    @Override
    public FilmstripItem refresh() {
        return mVideoItemFactory.get(mData.getUri());
    }

    @Override
    public View getView(Optional<View> optionalView,
          LocalFilmstripDataAdapter adapter, boolean isInProgress,
          final VideoClickedCallback videoClickedCallback) {

        View view;
        VideoViewHolder viewHolder;

        if (optionalView.isPresent()) {
            view = optionalView.get();
            viewHolder = getViewHolder(view);
        } else {
            view = LayoutInflater.from(mContext).inflate(R.layout.filmstrip_video, null);
            view.setTag(R.id.mediadata_tag_viewtype, getItemViewType().ordinal());
            ImageView videoView = (ImageView) view.findViewById(R.id.video_view);
            ImageView playButton = (ImageView) view.findViewById(R.id.play_button);

            viewHolder = new VideoViewHolder(videoView, playButton);
            view.setTag(R.id.mediadata_tag_target, viewHolder);
        }

        if (viewHolder != null) {
            // ImageView for the play icon.
            viewHolder.mPlayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    videoClickedCallback.playVideo(mData.getUri(), mData.getTitle());
                }
            });

            view.setContentDescription(mContext.getResources().getString(
                  R.string.video_date_content_description,
                  mDateFormatter.format(mData.getLastModifiedDate())));

            renderTiny(viewHolder);
        } else {
            Log.w(TAG, "getView called with a view that is not compatible with VideoItem.");
        }

        return view;
    }

    @Override
    public void renderTiny(@Nonnull View view) {
        renderTiny(getViewHolder(view));
    }

    @Override
    public void renderThumbnail(@Nonnull View view) {
        mGlideManager.loadScreen(mData.getUri(), generateSignature(mData), mSuggestedSize)
              .thumbnail(mGlideManager.loadMediaStoreThumb(mData.getUri(),
                    generateSignature(mData)))
              .into(getViewHolder(view).mVideoView);
    }

    @Override
    public void renderFullRes(@Nonnull View view) { }

    @Override
    public void recycle(@Nonnull View view) {
        VideoViewHolder holder = getViewHolder(view);
        if (holder != null) {
            Glide.clear(getViewHolder(view).mVideoView);
        }
    }

    @Override
    public FilmstripItemType getItemViewType() {
        return FilmstripItemType.VIDEO;
    }

    @Override
    public Optional<Bitmap> generateThumbnail(int boundingWidthPx, int boundingHeightPx) {
        return Optional.fromNullable(FilmstripItemUtils.loadVideoThumbnail(
                getData().getFilePath()));
    }

    @Override
    public String toString() {
        return "VideoItem: " + mData.toString();
    }

    private void renderTiny(@Nonnull VideoViewHolder viewHolder) {
        mGlideManager.loadMediaStoreThumb(mData.getUri(), generateSignature(mData))
              .into(viewHolder.mVideoView);
    }

    private VideoViewHolder getViewHolder(@Nonnull View view) {
        Object container = view.getTag(R.id.mediadata_tag_target);
        if (container instanceof VideoViewHolder) {
            return (VideoViewHolder) container;
        }

        return null;
    }
}
