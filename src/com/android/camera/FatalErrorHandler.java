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

package com.android.camera;

import android.hardware.camera2.CameraDevice;

import com.android.camera2.R;

/**
 * Handles fatal application errors.
 * <p>
 * Usage:
 * 
 * <pre>
 * if (unrecoverableErrorDetected) {
 *     fatalErrorHandler.handleFatalError(Reason.CANNOT_CONNECT_TO_CAMERA);
 * }
 * </pre>
 */
public interface FatalErrorHandler {
    public static enum Reason {
        CANNOT_CONNECT_TO_CAMERA(
                R.string.error_cannot_connect_camera,
                R.string.feedback_description_camera_access,
                true),
        CAMERA_HAL_FAILED(
                R.string.error_cannot_connect_camera,
                R.string.feedback_description_camera_access,
                true),
        CAMERA_DISABLED_BY_SECURITY_POLICY(
                R.string.error_camera_disabled,
                R.string.feedback_description_camera_access,
                true),
        MEDIA_STORAGE_FAILURE(
                R.string.error_media_storage_failure,
                R.string.feedback_description_save_photo,
                false);

        private final int mDialogMsgId;
        private final int mFeedbackMsgId;
        private final boolean mFinishActivity;

        /**
         * @param dialogMsgId The resource ID of string to display in the fatal
         *            error dialog.
         * @param feedbackMsgId The resource ID of default string to display in
         *            the feedback dialog, if the user chooses to submit
         *            feedback from the dialog.
         * @param finishActivity Whether the activity should be finished as a
         *            result of this error.
         */
        Reason(int dialogMsgId, int feedbackMsgId, boolean finishActivity) {
            mDialogMsgId = dialogMsgId;
            mFeedbackMsgId = feedbackMsgId;
            mFinishActivity = finishActivity;
        }

        /**
         * @return The resource ID of the string to display in the fatal error
         *         dialog.
         */
        public int getFeedbackMsgId() {
            return mFeedbackMsgId;
        }

        /**
         * @return The resource ID of the default string to display in the
         *         feedback dialog, if the user chooses to submit feedback from
         *         the dialog.
         */
        public int getDialogMsgId() {
            return mDialogMsgId;
        }

        /**
         * @return Whether the activity should be finished as a result of this
         *         error.
         */
        public boolean doesFinishActivity() {
            return mFinishActivity;
        }

        /**
         * Creates a new Reason based on an error code for
         * {@link CameraDevice.StateCallback#onError}.
         *
         * @param error The error code. One of
         *            CameraDevice.StateCallback.ERROR_*
         * @return The appropriate Reason.
         */
        public static Reason fromCamera2CameraDeviceStateCallbackError(int error) {
            // TODO Use a more descriptive reason to distinguish between
            // different types of errors.
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                default:
                    return CANNOT_CONNECT_TO_CAMERA;
            }
        }
    }

    /**
     * Handles Media Storage Failures - ie. images aren't being saved to disk.
     */
    public void onMediaStorageFailure();

    /**
     * Handles error where the camera cannot be opened.
     */
    public void onCameraOpenFailure();

    /**
     * Handles error where the camera cannot be reconnected.
     */
    public void onCameraReconnectFailure();

    /**
     * Handles generic error where the camera is unavailable. Only use this if
     * you are unsure what caused the error, such as a reconnection or open.
     * failure
     */
    public void onGenericCameraAccessFailure();

    /**
     * Handles error where the camera is disabled due to security.
     */
    public void onCameraDisabledFailure();


    /**
     * Handles a fatal error, e.g. by displaying the appropriate dialog and
     * exiting the activity.
     * @deprecated use specific implementations above instead
     */
    @Deprecated
    public void handleFatalError(Reason reason);
}
