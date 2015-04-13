/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.session;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * Internal interface that e.g. a capture session can use to update about the
 * status of session.
 */
public interface SessionNotifier {
    /** A new task has been queued. */
    public void notifyTaskQueued(final Uri uri);

    /** A task has finished processing. */
    public void notifyTaskDone(final Uri uri);

    /** A task has failed to process. */
    public void notifyTaskFailed(final Uri uri, final int failureMessageId,
                                 boolean removeFromFilmstrip);

    /** A task has been canceled. */
    public void notifyTaskCanceled(final Uri uri);

    /** A task has progressed. */
    public void notifyTaskProgress(final Uri uri, final int progressPercent);

    /** A task's current progress message has changed. */
    public void notifyTaskProgressText(final Uri uri, final int messageId);

    /** The underlying session data has been updated. */
    public void notifySessionUpdated(final Uri uri);

    /** The capture indicator should be updated. */
    public void notifySessionCaptureIndicatorAvailable(final Bitmap indicator,
            final int rotationDegrees);

    /** Notify that the full size thumbnail is available. */
    public void notifySessionThumbnailAvailable(final Bitmap thumbnail);

    /** Notify that the compressed picture data is available. */
    public void notifySessionPictureDataAvailable(final byte[] pictureData, final int orientation);
}
