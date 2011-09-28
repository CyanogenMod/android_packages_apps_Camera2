/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.gallery3d.app;

import com.android.gallery3d.R;
import com.android.gallery3d.common.BlobCache;
import com.android.gallery3d.util.CacheManager;
import com.android.gallery3d.util.GalleryUtils;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class MoviePlayer implements
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    @SuppressWarnings("unused")
    private static final String TAG = "MoviePlayer";

    private static final String KEY_VIDEO_POSITION = "video-position";
    private static final String KEY_RESUMEABLE_TIME = "resumeable-timeout";

    // Copied from MediaPlaybackService in the Music Player app.
    private static final String SERVICECMD = "com.android.music.musicservicecommand";
    private static final String CMDNAME = "command";
    private static final String CMDPAUSE = "pause";

    // If we resume the acitivty with in RESUMEABLE_TIMEOUT, we will keep playing.
    // Otherwise, we pause the player.
    private static final long RESUMEABLE_TIMEOUT = 3 * 60 * 1000; // 3 mins

    private Context mContext;
    private final VideoView mVideoView;
    private final View mProgressView;
    private final Bookmarker mBookmarker;
    private final Uri mUri;
    private final Handler mHandler = new Handler();
    private final AudioBecomingNoisyReceiver mAudioBecomingNoisyReceiver;
    private final ActionBar mActionBar;
    private final MediaController mMediaController;

    private long mResumeableTime = Long.MAX_VALUE;
    private int mVideoPosition = 0;
    private boolean mHasPaused = false;

    private final Runnable mPlayingChecker = new Runnable() {
        @Override
        public void run() {
            if (mVideoView.isPlaying()) {
                mProgressView.setVisibility(View.GONE);
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };

    public MoviePlayer(View rootView, final MovieActivity movieActivity, Uri videoUri,
            Bundle savedInstance) {
        mContext = movieActivity.getApplicationContext();
        mVideoView = (VideoView) rootView.findViewById(R.id.surface_view);
        mProgressView = rootView.findViewById(R.id.progress_indicator);
        mBookmarker = new Bookmarker(movieActivity);
        mActionBar = movieActivity.getActionBar();
        mUri = videoUri;

        // For streams that we expect to be slow to start up, show a
        // progress spinner until playback starts.
        String scheme = mUri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme)) {
            mHandler.postDelayed(mPlayingChecker, 250);
        } else {
            mProgressView.setVisibility(View.GONE);
        }

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setVideoURI(mUri);

        mMediaController = new MediaController(movieActivity) {
            @Override
            public void show() {
                showSystemUi(true);
                mActionBar.show();
                super.show();
            }

            @Override
            public void hide() {
                super.hide();
                mActionBar.hide();
                showSystemUi(false);
            }
        };

        mMediaController.show();

        // When the user touches the screen or uses some hard key, the framework
        // will change system ui visibility from invisible to visible. We show
        // the media control at this point.
        mVideoView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                    mMediaController.show();
                }
            }
        });

        mMediaController.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        movieActivity.onBackPressed();
                    }
                    return true;
                }
                return false;
            }
        });
        mVideoView.setMediaController(mMediaController);

        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mAudioBecomingNoisyReceiver.register();

        // make the video view handle keys for seeking and pausing
        mVideoView.requestFocus();

        Intent i = new Intent(SERVICECMD);
        i.putExtra(CMDNAME, CMDPAUSE);
        movieActivity.sendBroadcast(i);

        if (savedInstance != null) { // this is a resumed activity
            mVideoPosition = savedInstance.getInt(KEY_VIDEO_POSITION, 0);
            mResumeableTime = savedInstance.getLong(KEY_RESUMEABLE_TIME, Long.MAX_VALUE);
            mVideoView.start();
            mVideoView.suspend();
            mHasPaused = true;
        } else {
            final Integer bookmark = mBookmarker.getBookmark(mUri);
            if (bookmark != null) {
                showResumeDialog(movieActivity, bookmark);
            } else {
                mVideoView.start();
            }
        }
    }

    private void showSystemUi(boolean visible) {
        int flag = visible ? 0 : View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        mVideoView.setSystemUiVisibility(flag);
        mMediaController.setSystemUiVisibility(flag);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_VIDEO_POSITION, mVideoPosition);
        outState.putLong(KEY_RESUMEABLE_TIME, mResumeableTime);
    }

    private void showResumeDialog(Context context, final int bookmark) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.resume_playing_title);
        builder.setMessage(String.format(
                context.getString(R.string.resume_playing_message),
                GalleryUtils.formatDuration(context, bookmark / 1000)));
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                onCompletion();
            }
        });
        builder.setPositiveButton(
                R.string.resume_playing_resume, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mVideoView.seekTo(bookmark);
                mVideoView.start();
            }
        });
        builder.setNegativeButton(
                R.string.resume_playing_restart, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mVideoView.start();
            }
        });
        builder.show();
    }

    public void onPause() {
        mHasPaused = true;
        mHandler.removeCallbacksAndMessages(null);
        mVideoPosition = mVideoView.getCurrentPosition();
        mBookmarker.setBookmark(mUri, mVideoPosition, mVideoView.getDuration());
        mVideoView.suspend();
        mResumeableTime = System.currentTimeMillis() + RESUMEABLE_TIMEOUT;
    }

    public void onResume() {
        if (mHasPaused) {
            mVideoView.seekTo(mVideoPosition);
            mVideoView.resume();

            // If we have slept for too long, pause the play
            if (System.currentTimeMillis() > mResumeableTime) {
                mMediaController.show();
                mVideoView.pause();
            }
        }
    }

    public void onDestroy() {
        mVideoView.stopPlayback();
        mAudioBecomingNoisyReceiver.unregister();
    }

    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        mHandler.removeCallbacksAndMessages(null);
        mProgressView.setVisibility(View.GONE);
        return false;
    }

    public void onCompletion(MediaPlayer mp) {
        onCompletion();
    }

    public void onCompletion() {
    }

    private class AudioBecomingNoisyReceiver extends BroadcastReceiver {

        public void register() {
            mContext.registerReceiver(this,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mVideoView.isPlaying()) {
                mVideoView.pause();
          }
        }
    }
}

class Bookmarker {
    private static final String TAG = "Bookmarker";

    private static final String BOOKMARK_CACHE_FILE = "bookmark";
    private static final int BOOKMARK_CACHE_MAX_ENTRIES = 100;
    private static final int BOOKMARK_CACHE_MAX_BYTES = 10 * 1024;
    private static final int BOOKMARK_CACHE_VERSION = 1;

    private static final int HALF_MINUTE = 30 * 1000;
    private static final int TWO_MINUTES = 4 * HALF_MINUTE;

    private final Context mContext;

    public Bookmarker(Context context) {
        mContext = context;
    }

    public void setBookmark(Uri uri, int bookmark, int duration) {
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(uri.toString());
            dos.writeInt(bookmark);
            dos.writeInt(duration);
            dos.flush();
            cache.insert(uri.hashCode(), bos.toByteArray());
        } catch (Throwable t) {
            Log.w(TAG, "setBookmark failed", t);
        }
    }

    public Integer getBookmark(Uri uri) {
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            byte[] data = cache.lookup(uri.hashCode());
            if (data == null) return null;

            DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(data));

            String uriString = dis.readUTF(dis);
            int bookmark = dis.readInt();
            int duration = dis.readInt();

            if (!uriString.equals(uri.toString())) {
                return null;
            }

            if ((bookmark < HALF_MINUTE) || (duration < TWO_MINUTES)
                    || (bookmark > (duration - HALF_MINUTE))) {
                return null;
            }
            return Integer.valueOf(bookmark);
        } catch (Throwable t) {
            Log.w(TAG, "getBookmark failed", t);
        }
        return null;
    }
}
