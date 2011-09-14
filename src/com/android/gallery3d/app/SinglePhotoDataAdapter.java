/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.PhotoView.ImageData;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;

public class SinglePhotoDataAdapter extends TileImageViewAdapter
        implements PhotoPage.Model {

    private static final String TAG = "SinglePhotoDataAdapter";
    private static final int SIZE_BACKUP = 1024;
    private static final int MSG_UPDATE_IMAGE = 1;

    private MediaItem mItem;
    private boolean mHasFullImage;
    private Future<?> mTask;
    private Handler mHandler;

    private PhotoView mPhotoView;
    private ThreadPool mThreadPool;

    public SinglePhotoDataAdapter(
            GalleryActivity activity, PhotoView view, MediaItem item) {
        mItem = Utils.checkNotNull(item);
        mHasFullImage = (item.getSupportedOperations() &
                MediaItem.SUPPORT_FULL_IMAGE) != 0;
        mPhotoView = Utils.checkNotNull(view);
        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            @SuppressWarnings("unchecked")
            public void handleMessage(Message message) {
                Utils.assertTrue(message.what == MSG_UPDATE_IMAGE);
                if (mHasFullImage) {
                    onDecodeLargeComplete((ImageBundle) message.obj);
                } else {
                    onDecodeThumbComplete((Future<Bitmap>) message.obj);
                }
            }
        };
        mThreadPool = activity.getThreadPool();
    }

    private static class ImageBundle {
        public final BitmapRegionDecoder decoder;
        public final Bitmap backupImage;

        public ImageBundle(BitmapRegionDecoder decoder, Bitmap backupImage) {
            this.decoder = decoder;
            this.backupImage = backupImage;
        }
    }

    private FutureListener<BitmapRegionDecoder> mLargeListener =
            new FutureListener<BitmapRegionDecoder>() {
        public void onFutureDone(Future<BitmapRegionDecoder> future) {
            BitmapRegionDecoder decoder = future.get();
            if (decoder == null) return;
            int width = decoder.getWidth();
            int height = decoder.getHeight();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = BitmapUtils.computeSampleSize(
                    (float) SIZE_BACKUP / Math.max(width, height));
            Bitmap bitmap = decoder.decodeRegion(new Rect(0, 0, width, height), options);
            mHandler.sendMessage(mHandler.obtainMessage(
                    MSG_UPDATE_IMAGE, new ImageBundle(decoder, bitmap)));
        }
    };

    private FutureListener<Bitmap> mThumbListener =
            new FutureListener<Bitmap>() {
        public void onFutureDone(Future<Bitmap> future) {
            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_UPDATE_IMAGE, future));
        }
    };

    public boolean isEmpty() {
        return false;
    }

    public int getImageRotation() {
        return mItem.getRotation();
    }

    private void onDecodeLargeComplete(ImageBundle bundle) {
        try {
            setBackupImage(bundle.backupImage,
                    bundle.decoder.getWidth(), bundle.decoder.getHeight());
            setRegionDecoder(bundle.decoder);
            mPhotoView.notifyImageInvalidated(0);
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode large", t);
        }
    }

    private void onDecodeThumbComplete(Future<Bitmap> future) {
        try {
            Bitmap backup = future.get();
            if (backup == null) return;
            setBackupImage(backup, backup.getWidth(), backup.getHeight());
            mPhotoView.notifyOnNewImage();
            mPhotoView.notifyImageInvalidated(0); // the current image
        } catch (Throwable t) {
            Log.w(TAG, "fail to decode thumb", t);
        }
    }

    public void resume() {
        if (mTask == null) {
            if (mHasFullImage) {
                mTask = mThreadPool.submit(
                        mItem.requestLargeImage(), mLargeListener);
            } else {
                mTask = mThreadPool.submit(
                        mItem.requestImage(MediaItem.TYPE_THUMBNAIL),
                        mThumbListener);
            }
        }
    }

    public void pause() {
        Future<?> task = mTask;
        task.cancel();
        task.waitDone();
        if (task.get() == null) {
            mTask = null;
        }
    }

    public ImageData getNextImage() {
        return null;
    }

    public ImageData getPreviousImage() {
        return null;
    }

    public void next() {
        throw new UnsupportedOperationException();
    }

    public void previous() {
        throw new UnsupportedOperationException();
    }

    public void jumpTo(int index) {
        throw new UnsupportedOperationException();
    }

    public MediaItem getCurrentMediaItem() {
        return mItem;
    }

    public int getCurrentIndex() {
        return 0;
    }

    public void setCurrentPhoto(Path path, int indexHint) {
        // ignore
    }
}
