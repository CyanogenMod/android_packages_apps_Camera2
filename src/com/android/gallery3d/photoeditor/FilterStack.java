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

package com.android.gallery3d.photoeditor;

import android.graphics.Bitmap;

import com.android.gallery3d.photoeditor.filters.Filter;

import java.util.Stack;

/**
 * A stack of filters to be applied onto a photo.
 */
public class FilterStack {

    /**
     * Listener of stack changes.
     */
    public interface StackListener {

        void onStackChanged(boolean canUndo, boolean canRedo);
    }

    private final Stack<Filter> appliedStack = new Stack<Filter>();
    private final Stack<Filter> redoStack = new Stack<Filter>();

    // Use two photo buffers as in and out in turns to apply filters in the stack.
    private final Photo[] buffers = new Photo[2];
    private final PhotoView photoView;
    private final StackListener stackListener;

    private Photo source;
    private Runnable queuedTopFilterChange;
    private boolean topFilterOutputted;
    private volatile boolean paused;

    public FilterStack(PhotoView photoView, StackListener stackListener) {
        this.photoView = photoView;
        this.stackListener = stackListener;
    }

    private void reallocateBuffer(int target) {
        int other = target ^ 1;
        buffers[target] = Photo.create(buffers[other].width(), buffers[other].height());
    }

    private void invalidate() {
        // In/out buffers need redrawn by re-applying filters on source photo.
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null) {
                buffers[i].clear();
                buffers[i] = null;
            }
        }
        if (source != null) {
            buffers[0] = Photo.create(source.width(), source.height());
            reallocateBuffer(1);

            // Source photo will be displayed if there is no filter stacked.
            Photo photo = source;
            int size = topFilterOutputted ? appliedStack.size() : appliedStack.size() - 1;
            for (int i = 0; i < size && !paused; i++) {
                photo = runFilter(i);
            }
            photoView.setPhoto(photo, topFilterOutputted);
        }
    }

    private void invalidateTopFilter() {
        if (!appliedStack.empty()) {
            photoView.setPhoto(runFilter(appliedStack.size() - 1), true);
            topFilterOutputted = true;
        }
    }

    private Photo runFilter(int filterIndex) {
        int out = getOutBufferIndex(filterIndex);
        Photo input = (filterIndex > 0) ? buffers[out ^ 1] : source;
        if ((input != null) && (buffers[out] != null)) {
            if (!buffers[out].matchDimension(input)) {
                buffers[out].clear();
                reallocateBuffer(out);
            }
            appliedStack.get(filterIndex).process(input, buffers[out]);
            return buffers[out];
        }
        return null;
    }

    private int getOutBufferIndex(int filterIndex) {
        // buffers[0] and buffers[1] are swapped in turns as the in/out buffers for
        // processing stacked filters. For example, the first filter reads buffer[0] and
        // writes buffer[1]; the second filter then reads buffer[1] and writes buffer[0].
        // The returned index should only be used when the applied filter stack isn't empty.
        return (filterIndex + 1) % 2;
    }

    private void callbackDone(final OnDoneCallback callback) {
        // GL thread calls back to report UI thread the task is done.
        photoView.post(new Runnable() {

            @Override
            public void run() {
                callback.onDone();
            }
        });
    }

    private void stackChanged() {
        // GL thread calls back to report UI thread the stack is changed.
        final boolean canUndo = !appliedStack.empty();
        final boolean canRedo = !redoStack.empty();
        photoView.post(new Runnable() {

            @Override
            public void run() {
                stackListener.onStackChanged(canUndo, canRedo);
            }
        });
    }

    public void saveBitmap(final OnDoneBitmapCallback callback) {
        photoView.queue(new Runnable() {

            @Override
            public void run() {
                int filterIndex = appliedStack.size() - (topFilterOutputted ? 1 : 2);
                Photo photo = (filterIndex < 0) ? source : buffers[getOutBufferIndex(filterIndex)];
                final Bitmap bitmap = (photo != null) ? photo.save() : null;
                photoView.post(new Runnable() {

                    @Override
                    public void run() {
                        callback.onDone(bitmap);
                    }
                });
            }
        });
    }

    public void setPhotoSource(final Bitmap bitmap, final OnDoneCallback callback) {
        photoView.queue(new Runnable() {

            @Override
            public void run() {
                source = Photo.create(bitmap);
                invalidate();
                callbackDone(callback);
            }
        });
    }

    private void pushFilterInternal(Filter filter) {
        appliedStack.push(filter);
        topFilterOutputted = false;
        stackChanged();
    }

    public void pushFilter(final Filter filter) {
        photoView.queue(new Runnable() {

            @Override
            public void run() {
                while (!redoStack.empty()) {
                    redoStack.pop().release();
                }
                pushFilterInternal(filter);
            }
        });
    }

    public void undo(final OnDoneCallback callback) {
        photoView.queue(new Runnable() {

            @Override
            public void run() {
                if (!appliedStack.empty()) {
                    redoStack.push(appliedStack.pop());
                    stackChanged();
                    invalidate();
                }
                callbackDone(callback);
            }
        });
    }

    public void redo(final OnDoneCallback callback) {
        photoView.queue(new Runnable() {

            @Override
            public void run() {
                if (!redoStack.empty()) {
                    pushFilterInternal(redoStack.pop());
                    invalidateTopFilter();
                }
                callbackDone(callback);
            }
        });
    }

    public void topFilterChanged(final OnDoneCallback callback) {
        // Remove the outdated top-filter change before queuing a new one.
        if (queuedTopFilterChange != null) {
            photoView.remove(queuedTopFilterChange);
        }
        queuedTopFilterChange = new Runnable() {

            @Override
            public void run() {
                invalidateTopFilter();
                callbackDone(callback);
            }
        };
        photoView.queue(queuedTopFilterChange);
    }

    public void onPause() {
        // Flush pending queued operations and release effect-context before GL context is lost.
        // Use the flag to break from lengthy invalidate() in GL thread for not blocking onPause().
        paused = true;
        photoView.flush();
        photoView.queueEvent(new Runnable() {

            @Override
            public void run() {
                Filter.releaseContext();
                // Textures will be automatically deleted when GL context is lost.
                photoView.setPhoto(null, false);
                source = null;
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = null;
                }
            }
        });
        photoView.onPause();
    }

    public void onResume() {
        photoView.onResume();
        paused = false;
    }
}
