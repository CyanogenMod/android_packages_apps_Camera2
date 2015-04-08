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

package com.android.camera.processing.imagebackend;

import android.net.Uri;

import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Implements the ability for the object to send events to multiple listeners in
 * a thread-safe manner. Also, listeners can also filter messages based on the a
 * specific image result.
 * <p>
 * TODO: Replace this object with a more generic listener class. TODO: Replace
 * the image filter code with something more efficient.
 */
public class ImageProcessorProxyListener implements ImageProcessorListener {

    private final static Log.Tag TAG = new Log.Tag("IProxyListener");

    private final List<ImageProcessorListener> mRegisteredListeners;

    private final HashMap<ImageProcessorListener, Long> mImageFilter;

    /**
     * Wrapper for the log to avoid direct references to Android Log objects
     * that will crash unit tests. Subclasses may override this method for
     * debugging.
     *
     * @param message
     */
    protected void logWrapper(String message) {
        // Uncomment for more verbose messaging.
        // Log.v(TAG, message);
    }

    ImageProcessorProxyListener() {
        mRegisteredListeners = new ArrayList<ImageProcessorListener>();
        mImageFilter = new HashMap<ImageProcessorListener, Long>();
    }

    /**
     * Returns the size of the ImageFilter so that we ensure that there are no
     * reference leaks.
     *
     * @return the number of elements in the mapping between
     *         ImageProcessorListener and their ids.
     */
    @VisibleForTesting
    public int getMapSize() {
        synchronized (mRegisteredListeners) {
            return mImageFilter.size();
        }
    }

    /**
     * Returns the number of ImageProcessorListener held by the system so that
     * we ensure that there are no reference leaks.
     *
     * @return the number of registered ImageProcessorListener
     */
    @VisibleForTesting
    public int getNumRegisteredListeners() {
        synchronized (mRegisteredListeners) {
            return mRegisteredListeners.size();
        }
    }

    /**
     * Register a listener filtered by a particular image object. If image is
     * null, then events from all image processing will be sent to the
     * registered listener.
     *
     * @param listener The listener to be registered.
     * @param image The specific image to filter the events to the listener. If
     *            null, then the listener receives events from all images that
     *            are being processed.
     */
    public void registerListener(ImageProcessorListener listener,
            @Nullable ImageProxy image) {
        synchronized (mRegisteredListeners) {
            logWrapper("There are " + mRegisteredListeners.size()
                    + " listeners before addition");
            if (!mRegisteredListeners.contains(listener)) {
                mRegisteredListeners.add(listener);
                logWrapper("Listener will be overwritten.");
            }

            if (image == null) {
                mImageFilter.put(listener, null);
            } else {
                mImageFilter.put(listener, image.getTimestamp());
            }
            logWrapper("There are " + mRegisteredListeners.size()
                    + " listeners after addition");
        }

        return;
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
                logWrapper("There are " + mRegisteredListeners.size()
                        + " listeners after removal");
            } else {
                logWrapper("Couldn't find listener.  There are " + mRegisteredListeners.size()
                        + " listeners after removal");
            }
        }
    }

    public void onStart(TaskImageContainer.TaskInfo job) {
        final List<ImageProcessorListener> listeners;
        synchronized (mRegisteredListeners) {
            listeners = filteredListeners(job.contentId);
        }

        for (ImageProcessorListener l : listeners) {
            l.onStart(job);
        }
    }

    public void onResultCompressed(TaskImageContainer.TaskInfo job,
            TaskImageContainer.CompressedPayload payload) {
        final List<ImageProcessorListener> listeners;
        synchronized (mRegisteredListeners) {
            listeners = filteredListeners(job.contentId);
        }

        for (ImageProcessorListener l : listeners) {
            l.onResultCompressed(job, payload);
        }
    }

    public void onResultUncompressed(TaskImageContainer.TaskInfo job,
            TaskImageContainer.UncompressedPayload payload) {
        final List<ImageProcessorListener> listeners;
        synchronized (mRegisteredListeners) {
            listeners = filteredListeners(job.contentId);
        }

        for (ImageProcessorListener l : listeners) {
            l.onResultUncompressed(job, payload);
        }
    }

    public void onResultUri(TaskImageContainer.TaskInfo job, Uri uri) {
        final List<ImageProcessorListener> listeners;
        synchronized (mRegisteredListeners) {
            listeners = filteredListeners(job.contentId);
        }

        for (ImageProcessorListener l : listeners) {
            l.onResultUri(job, uri);
        }
    }

}
