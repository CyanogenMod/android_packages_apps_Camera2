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

package com.android.camera.captureintent.state;

import com.google.common.base.Optional;

import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.CaptureIntentConfig;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;
import com.android.camera.debug.Log;
import com.android.camera.util.CameraUtil;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents a state that the module is saving the picture to disk.
 */
public class StateSavingPicture extends StateImpl {
    private static final Log.Tag TAG = new Log.Tag("StateSavePic");

    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final byte[] mPictureData;

    public static StateSavingPicture from(
            StateReviewingPicture reviewingPicture,
            RefCountBase<ResourceConstructed> resourceConstructed,
            byte[] pictureData) {
        return new StateSavingPicture(reviewingPicture, resourceConstructed, pictureData);
    }

    private StateSavingPicture(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            byte[] pictureData) {
        super(previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();  // Will be balanced in onLeave().
        mPictureData = pictureData;
    }

    @Override
    public Optional<State> onEnter() {
        /**
         * The caller may pass an extra EXTRA_OUTPUT to control where this
         * image will be written. If the EXTRA_OUTPUT is not present, then
         * a small sized image is returned as a Bitmap object in the extra
         * field. This is useful for applications that only need a small
         * image. If the EXTRA_OUTPUT is present, then the full-sized image
         * will be written to the Uri value of EXTRA_OUTPUT.
         */
        Optional<Uri> saveUri = Optional.absent();
        final Bundle myExtras = mResourceConstructed.get().getIntent().getExtras();
        if (myExtras != null) {
            saveUri = Optional.of((Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT));
            String cropValue = myExtras.getString("crop");
        }

        if (saveUri.isPresent()) {
            OutputStream outputStream = null;
            try {
                outputStream = mResourceConstructed.get().getContext().getContentResolver()
                        .openOutputStream(saveUri.get());
                outputStream.write(mPictureData);
                outputStream.close();

                Log.v(TAG, "saved result to URI: " + saveUri);
                return Optional.of((State) StateIntentCompleted.from(
                        this, mResourceConstructed, new Intent()));
            } catch (IOException ex) {
                Log.e(TAG, "exception while saving result to URI: " + saveUri, ex);
            } finally {
                CameraUtil.closeSilently(outputStream);
            }
        } else {
            /** Inline the bitmap into capture intent result */
            final Bitmap bitmap = CameraUtil.makeBitmap(
                    mPictureData, CaptureIntentConfig.INLINE_BITMAP_MAX_PIXEL_NUM);
            return Optional.of((State) StateIntentCompleted.from(
                    this, mResourceConstructed,
                    new Intent("inline-data").putExtra("data", bitmap)));
        }
        return Optional.of((State) StateIntentCompleted.from(this, mResourceConstructed));
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
    }
}