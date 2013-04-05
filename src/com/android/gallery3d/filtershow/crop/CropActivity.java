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

package com.android.gallery3d.filtershow.crop;

import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.gallery3d.R;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Activity for cropping an image.
 */
public class CropActivity extends Activity {
    private static final String LOGTAG = "CropActivity";
    private CropExtras mCropExtras = null;
    private LoadBitmapTask mLoadBitmapTask = null;
    private SaveBitmapTask mSaveBitmapTask = null;
    private SetWallpaperTask mSetWallpaperTask = null;
    private Bitmap mOriginalBitmap = null;
    private CropView mCropView = null;
    private int mActiveBackgroundIO = 0;
    private Intent mResultIntent = null;
    private static final int SELECT_PICTURE = 1; // request code for picker
    private static final int DEFAULT_DENSITY = 133;
    private static final int DEFAULT_COMPRESS_QUALITY = 90;
    public static final int MAX_BMAP_IN_INTENT = 990000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mResultIntent = new Intent();
        setResult(RESULT_CANCELED, mResultIntent);
        mCropExtras = getExtrasFromIntent(intent);
        if (mCropExtras != null && mCropExtras.getShowWhenLocked()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setContentView(R.layout.crop_activity);
        mCropView = (CropView) findViewById(R.id.cropView);

        if (intent.getData() != null) {
            startLoadBitmap(intent.getData());
        } else {
            pickImage();
        }
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(R.layout.filtershow_actionbar);

        View saveButton = actionBar.getCustomView();
        saveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startFinishOutput();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }
        super.onDestroy();
    }

    /**
     * Opens a selector in Gallery to chose an image for use when none was given
     * in the CROP intent.
     */
    public void pickImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    /**
     * Callback for pickImage().
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            Uri selectedImageUri = data.getData();
            startLoadBitmap(selectedImageUri);
        }
    }

    /**
     * Gets the crop extras from the intent, or null if none exist.
     */
    public static CropExtras getExtrasFromIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            return new CropExtras(extras.getInt(CropExtras.KEY_OUTPUT_X, 0),
                    extras.getInt(CropExtras.KEY_OUTPUT_Y, 0),
                    extras.getBoolean(CropExtras.KEY_SCALE, true) &&
                            extras.getBoolean(CropExtras.KEY_SCALE_UP_IF_NEEDED, false),
                    extras.getInt(CropExtras.KEY_ASPECT_X, 0),
                    extras.getInt(CropExtras.KEY_ASPECT_Y, 0),
                    extras.getBoolean(CropExtras.KEY_SET_AS_WALLPAPER, false),
                    extras.getBoolean(CropExtras.KEY_RETURN_DATA, false),
                    (Uri) extras.getParcelable(MediaStore.EXTRA_OUTPUT),
                    extras.getString(CropExtras.KEY_OUTPUT_FORMAT),
                    extras.getBoolean(CropExtras.KEY_SHOW_WHEN_LOCKED, false),
                    extras.getFloat(CropExtras.KEY_SPOTLIGHT_X),
                    extras.getFloat(CropExtras.KEY_SPOTLIGHT_Y));
        }
        return null;
    }

    /**
     * Gets screen size metric.
     */
    private int getScreenImageSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        display.getMetrics(metrics);
        int msize = Math.min(size.x, size.y);
        // TODO: WTF
        return (DEFAULT_DENSITY * msize) / metrics.densityDpi + 512;
    }

    /**
     * Method that loads a bitmap in an async task.
     */
    private void startLoadBitmap(Uri uri) {
        mActiveBackgroundIO++;
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.VISIBLE);
        mLoadBitmapTask = new LoadBitmapTask();
        mLoadBitmapTask.execute(uri);
    }

    /**
     * Method called on UI thread with loaded bitmap.
     */
    private void doneLoadBitmap(Bitmap bitmap) {
        mActiveBackgroundIO--;
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        mOriginalBitmap = bitmap;
        // TODO: move these to dimens folder
        if (bitmap != null) {
            mCropView.setup(bitmap, (int) getPixelsFromDip(55), (int) getPixelsFromDip(25));
        } else {
            Log.w(LOGTAG, "could not load image for cropping");
            cannotLoadImage();
            setResult(RESULT_CANCELED, mResultIntent);
            done();
        }
    }

    /**
     * Display toast for image loading failure.
     */
    private void cannotLoadImage() {
        CharSequence text = getString(R.string.cannot_load_image);
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * AsyncTask for loading a bitmap into memory.
     *
     * @see #startLoadBitmap(Uri)
     * @see #doneLoadBitmap(Bitmap)
     */
    private class LoadBitmapTask extends AsyncTask<Uri, Void, Bitmap> {
        int mBitmapSize;
        Context mContext;
        Rect mOriginalBounds;

        public LoadBitmapTask() {
            mBitmapSize = getScreenImageSize();
            Log.v(LOGTAG, "bitmap size: " + mBitmapSize);
            mContext = getApplicationContext();
            mOriginalBounds = new Rect();
        }

        @Override
        protected Bitmap doInBackground(Uri... params) {
            Bitmap bmap = CropLoader.getConstrainedBitmap(params[0], mContext, mBitmapSize,
                    mOriginalBounds);
            return bmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            doneLoadBitmap(result);
            // super.onPostExecute(result);
        }
    }

    private void startSaveBitmap(Bitmap bmap, Uri uri, String format) {
        if (bmap == null || uri == null) {
            throw new IllegalArgumentException("bad argument to startSaveBitmap");
        }
        mActiveBackgroundIO++;
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.VISIBLE);
        mSaveBitmapTask = new SaveBitmapTask(uri, format);
        mSaveBitmapTask.execute(bmap);
    }

    private void doneSaveBitmap(Uri uri) {
        mActiveBackgroundIO--;
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
        if (uri == null) {
            Log.w(LOGTAG, "failed to save bitmap");
            setResult(RESULT_CANCELED, mResultIntent);
            done();
            return;
        }
        done();
    }

    private class SaveBitmapTask extends AsyncTask<Bitmap, Void, Boolean> {

        OutputStream mOutStream = null;
        String mOutputFormat = null;
        Uri mOutUri = null;

        public SaveBitmapTask(Uri uri, String outputFormat) {
            mOutputFormat = outputFormat;
            mOutStream = null;
            mOutUri = uri;
            try {
                mOutStream = getContentResolver().openOutputStream(uri);
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "cannot write output: " + mOutUri.toString(), e);
            }
        }

        @Override
        protected Boolean doInBackground(Bitmap... params) {
            if (mOutStream == null) {
                return false;
            }
            CompressFormat cf = convertExtensionToCompressFormat(getFileExtension(mOutputFormat));
            return params[0].compress(cf, DEFAULT_COMPRESS_QUALITY, mOutStream);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result.booleanValue() == false) {
                Log.w(LOGTAG, "could not compress to output: " + mOutUri.toString());
                doneSaveBitmap(null);
            }
            doneSaveBitmap(mOutUri);
        }
    }

    private void startSetWallpaper(Bitmap bmap) {
        if (bmap == null) {
            throw new IllegalArgumentException("bad argument to startSetWallpaper");
        }
        mActiveBackgroundIO++;
        Toast.makeText(this, R.string.setting_wallpaper, Toast.LENGTH_LONG).show();
        mSetWallpaperTask = new SetWallpaperTask();
        mSetWallpaperTask.execute(bmap);

    }

    private void doneSetWallpaper() {
        mActiveBackgroundIO--;
        done();
    }

    private class SetWallpaperTask extends AsyncTask<Bitmap, Void, Boolean> {
        private final WallpaperManager mWPManager;

        public SetWallpaperTask() {
            mWPManager = WallpaperManager.getInstance(getApplicationContext());
        }

        @Override
        protected Boolean doInBackground(Bitmap... params) {
            try {
                mWPManager.setBitmap(params[0]);
            } catch (IOException e) {
                Log.w(LOGTAG, "fail to set wall paper", e);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            doneSetWallpaper();
        }
    }

    private void startFinishOutput() {
        if (mOriginalBitmap != null && mCropExtras != null) {
            Bitmap cropped = null;
            if (mCropExtras.getExtraOutput() != null) {
                if (cropped == null) {
                    cropped = getCroppedImage(mOriginalBitmap);
                }
                startSaveBitmap(cropped, mCropExtras.getExtraOutput(),
                        mCropExtras.getOutputFormat());
            }
            if (mCropExtras.getSetAsWallpaper()) {
                if (cropped == null) {
                    cropped = getCroppedImage(mOriginalBitmap);
                }
                startSetWallpaper(cropped);
            }
            if (mCropExtras.getReturnData()) {
                if (cropped == null) {
                    cropped = getCroppedImage(mOriginalBitmap);
                }
                int bmapSize = cropped.getRowBytes() * cropped.getHeight();
                if (bmapSize > MAX_BMAP_IN_INTENT) {
                    Log.w(LOGTAG, "Bitmap too large to be returned via intent");
                } else {
                    mResultIntent.putExtra(CropExtras.KEY_DATA, cropped);
                }
            }
            setResult(RESULT_OK, mResultIntent);
        } else {
            setResult(RESULT_CANCELED, mResultIntent);
        }
        done();
    }

    private void done() {
        if (mActiveBackgroundIO == 0) {
            finish();
        }
    }

    private Bitmap getCroppedImage(Bitmap image) {
        RectF imageBounds = new RectF(0, 0, image.getWidth(), image.getHeight());
        RectF crop = getBitmapCrop(imageBounds);
        if (crop == null) {
            return image;
        }
        Rect intCrop = new Rect();
        crop.roundOut(intCrop);
        return Bitmap.createBitmap(image, intCrop.left, intCrop.top, intCrop.width(),
                intCrop.height());
    }

    private RectF getBitmapCrop(RectF imageBounds) {
        RectF crop = new RectF();
        if (!mCropView.getCropBounds(crop, imageBounds)) {
            Log.w(LOGTAG, "could not get crop");
            return null;
        }
        return crop;
    }

    /**
     * Helper method for unit conversions.
     */
    public float getPixelsFromDip(float value) {
        Resources r = getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                r.getDisplayMetrics());
    }

    private static CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equals("png") ? CompressFormat.PNG : CompressFormat.JPEG;
    }

    private static String getFileExtension(String requestFormat) {
        String outputFormat = (requestFormat == null)
                ? "jpg"
                : requestFormat;
        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }

}
