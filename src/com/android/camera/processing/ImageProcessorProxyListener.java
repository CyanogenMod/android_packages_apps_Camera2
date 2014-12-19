/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.processing;

import android.media.Image;
import android.net.Uri;

import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implements the ability for the object to send events to multiple listeners in a thread-safe
 * manner. Also, listeners can also filter messages based on the a specific image result. TODO:
 * Replace this object with a more generic listener classes.
 */
public class ImageProcessorProxyListener implements ImageProcessorListener {

    private final static Log.Tag TAG = new Log.Tag("IProxyListener");

    private List<ImageProcessorListener> mRegisteredListeners = null;

    private HashMap<ImageProcessorListener, Long> mImageFilter = null;

    ImageProcessorProxyListener() {
        mRegisteredListeners = new ArrayList<ImageProcessorListener>();
        mImageFilter = new HashMap<ImageProcessorListener, Long>();
    }

    // TODO: Return the state of the paired thing in processing
    public List<TaskImageContainer.TaskImage> registerListener(ImageProcessorListener listener,
            ImageProxy image) {
        synchronized (mRegisteredListeners) {
            mRegisteredListeners.add(listener);
            if (image == null) {
                mImageFilter.put(listener, null);
            } else {
                mImageFilter.put(listener, image.getTimestamp());
            }
        }

        // TODO: return an array that encapsulated the current jobs that are
        // running.
        return null;
    }

    private List<ImageProcessorListener> filteredListeners(long imageId) {
        List<ImageProcessorListener> filteredList = new ArrayList<ImageProcessorListener>();

        for (ImageProcessorListener l : mRegisteredListeners) {
            if (mImageFilter.get(l) == null || mImageFilter.get(l) == imageId) {
                filteredList.add(l);
            }
        }

        return filteredList;
    }

    public void unregisterListener(ImageProcessorListener listener) {
        synchronized (mRegisteredListeners) {
            if (mRegisteredListeners.contains(listener)) {
                mRegisteredListeners.remove(listener);
                mImageFilter.remove(listener);
                Log.e(TAG, "There are " + mRegisteredListeners.size() + " listeners after removal");
            } else {
                Log.e(TAG, "Couldn't find listener.  There are " + mRegisteredListeners.size()
                        + " listeners after removal");
            }
        }
    }

    public void onStart(TaskImageContainer.TaskInfo job) {
        synchronized (mRegisteredListeners) {
            for (ImageProcessorListener l : filteredListeners(job.contentId)) {
                l.onStart(job);
            }
        }
    }

    public void onResultCompressed(TaskImageContainer.TaskInfo job,
            TaskImageContainer.CompressedPayload payload) {
        synchronized (mRegisteredListeners) {
            for (ImageProcessorListener l : filteredListeners(job.contentId)) {
                l.onResultCompressed(job, payload);
            }
        }
    }

    public void onResultUncompressed(TaskImageContainer.TaskInfo job,
            TaskImageContainer.UncompressedPayload payload) {
        synchronized (mRegisteredListeners) {
            for (ImageProcessorListener l : filteredListeners(job.contentId)) {
                l.onResultUncompressed(job, payload);
            }
        }
    }

    public void onResultUri(TaskImageContainer.TaskInfo job, Uri uri) {
        synchronized (mRegisteredListeners) {
            for (ImageProcessorListener l : filteredListeners(job.contentId)) {
                l.onResultUri(job, uri);
            }
        }
    }

}
