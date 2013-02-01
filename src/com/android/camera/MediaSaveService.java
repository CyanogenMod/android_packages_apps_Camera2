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

package com.android.camera;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/*
 * Service for saving images in the background thread.
 */
public class MediaSaveService extends Service {
    private static final int SAVE_TASK_LIMIT = 3;
    private static final String TAG = MediaSaveService.class.getSimpleName();

    private final IBinder mBinder = new LocalBinder();
    private int mTaskNumber;
    private Listener mListener;

    interface Listener {
        public void onQueueAvailable();
        public void onQueueFull();
    }

    interface OnMediaSavedListener {
        public void onMediaSaved(Uri uri);
    }

    class LocalBinder extends Binder {
        public MediaSaveService getService() {
            return MediaSaveService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flag, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void onCreate() {
        mTaskNumber = 0;
    }

    public boolean isQueueFull() {
        return (mTaskNumber >= SAVE_TASK_LIMIT);
    }

    // Runs in main thread
    public void addImage(final byte[] data, String title, long date, Location loc,
            int width, int height, int orientation,
            OnMediaSavedListener l, ContentResolver resolver) {
        if (isQueueFull()) {
            Log.e(TAG, "Cannot add image when the queue is full");
            return;
        }
        SaveTask t = new SaveTask(data, title, date, (loc == null) ? null : new Location(loc),
                width, height, orientation, resolver, l);

        mTaskNumber++;
        if (isQueueFull()) {
            onQueueFull();
        }
        t.execute();
    }

    public void setListener(Listener l) {
        mListener = l;
        if (l == null) return;
        if (isQueueFull()) {
            l.onQueueFull();
        } else {
            l.onQueueAvailable();
        }
    }

    private void onQueueFull() {
        if (mListener != null) mListener.onQueueFull();
    }

    private void onQueueAvailable() {
        if (mListener != null) mListener.onQueueAvailable();
    }

    private class SaveTask extends AsyncTask <Void, Void, Uri> {
        private byte[] data;
        private String title;
        private long date;
        private Location loc;
        private int width, height;
        private int orientation;
        private ContentResolver resolver;
        private OnMediaSavedListener listener;

        public SaveTask(byte[] data, String title, long date, Location loc,
                int width, int height, int orientation, ContentResolver resolver,
                OnMediaSavedListener listener) {
            this.data = data;
            this.title = title;
            this.date = date;
            this.loc = loc;
            this.width = width;
            this.height = height;
            this.orientation = orientation;
            this.resolver = resolver;
            this.listener = listener;
        }

        @Override
        protected void onPreExecute() {
            // do nothing.
        }

        @Override
        protected Uri doInBackground(Void... v) {
            return Storage.addImage(
                    resolver, title, date, loc, orientation, data, width, height);
        }

        @Override
        protected void onPostExecute(Uri uri) {
            listener.onMediaSaved(uri);
            mTaskNumber--;
            if (mTaskNumber == SAVE_TASK_LIMIT - 1) onQueueAvailable();
        }
    }
}
