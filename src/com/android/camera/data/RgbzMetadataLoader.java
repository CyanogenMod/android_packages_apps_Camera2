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

package com.android.camera.data;

import android.content.Context;
import android.net.Uri;

import com.android.camera.exif.ExifInterface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Asynchronously loads RGBZ data.
 */
public class RgbzMetadataLoader {
  public static interface RgbzMetadataCallback {
    public void onRgbzMetadataLoaded(Boolean isRgbz);
  }

  private static final String EXIF_SOFTWARE_VALUE = "RGBZ";
  private Boolean mIsRgbz = null;
  private ArrayList<RgbzMetadataCallback> mCallbacksWaiting;
  private final Uri mMediaUri;

  public RgbzMetadataLoader(Uri uri) {
    mMediaUri = uri;
  }

  /**
   * Check whether this file is an RGBZ file.
   *
   * @param context The app context.
   * @param callback Will be called with the result.
   */
  public synchronized void getRgbzMetadata(final Context context, RgbzMetadataCallback callback) {
    if (mIsRgbz != null) {
      callback.onRgbzMetadataLoaded(mIsRgbz);
      return;
    }

    if (mCallbacksWaiting == null) {
      mCallbacksWaiting = new ArrayList<RgbzMetadataCallback>();
      (new Thread() {
        @Override
        public void run() {
          boolean isRgbz = false;

          try {
            InputStream input;
            input = context.getContentResolver().openInputStream(mMediaUri);
            isRgbz = isRgbz(input);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          }
          onLoadingDone(isRgbz);
        }
      }).start();

    }
    mCallbacksWaiting.add(callback);
  }

  private synchronized void onLoadingDone(boolean isRgbz) {
    mIsRgbz = isRgbz;
    for (RgbzMetadataCallback cb : mCallbacksWaiting) {
      cb.onRgbzMetadataLoaded(mIsRgbz);
    }
    mCallbacksWaiting = null;
  }

  /**
   * @return Whether the file is an RGBZ file.
   */
  public static boolean isRgbz(InputStream input) {
    ExifInterface exif = new ExifInterface();
    try {
      exif.readExif(input);
      // TODO: Rather than this, check for the presence of the XMP.
      String software = exif.getTagStringValue(ExifInterface.TAG_SOFTWARE);
      return software != null && software.startsWith(EXIF_SOFTWARE_VALUE);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }
}
