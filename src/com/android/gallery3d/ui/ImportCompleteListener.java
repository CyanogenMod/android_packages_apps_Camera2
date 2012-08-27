/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumPage;
import com.android.gallery3d.util.MediaSetUtils;

public class ImportCompleteListener implements MenuExecutor.ProgressListener {
    private AbstractGalleryActivity mActivity;
    private PowerManager.WakeLock mWakeLock;

    public ImportCompleteListener(AbstractGalleryActivity galleryActivity) {
        mActivity = galleryActivity;
        PowerManager pm =
                (PowerManager) ((Activity) mActivity).getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Gallery Album Import");
    }

    @Override
    public void onProgressComplete(int result) {
        int message;
        if (result == MenuExecutor.EXECUTION_RESULT_SUCCESS) {
            message = R.string.import_complete;
            goToImportedAlbum();
        } else {
            message = R.string.import_fail;
        }
        Toast.makeText(mActivity.getAndroidContext(), message, Toast.LENGTH_LONG).show();
        mWakeLock.release();
    }

    @Override
    public void onProgressUpdate(int index) {
    }

    @Override
    public void onProgressStart() {
        mWakeLock.acquire();
    }

    private void goToImportedAlbum() {
        String pathOfImportedAlbum = "/local/all/" + MediaSetUtils.IMPORTED_BUCKET_ID;
        Bundle data = new Bundle();
        data.putString(AlbumPage.KEY_MEDIA_PATH, pathOfImportedAlbum);
        mActivity.getStateManager().startState(AlbumPage.class, data);
    }

    @Override
    public void onConfirmDialogDismissed(boolean confirmed) {
    }

    @Override
    public void onConfirmDialogShown() {
    }
}
