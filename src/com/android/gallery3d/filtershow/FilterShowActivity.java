
package com.android.gallery3d.filtershow;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.*;
import com.android.gallery3d.filtershow.imageshow.ImageBorder;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.ImageSmallFilter;
import com.android.gallery3d.filtershow.imageshow.ImageStraighten;
import com.android.gallery3d.filtershow.presets.*;
import com.android.gallery3d.filtershow.provider.SharedImageProvider;
import com.android.gallery3d.filtershow.tools.SaveCopyTask;
import com.android.gallery3d.filtershow.ui.ImageCurves;
import com.android.gallery3d.R;

import android.net.Uri;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.widget.Toast;

@TargetApi(16)
public class FilterShowActivity extends Activity implements OnItemClickListener,
        OnShareTargetSelectedListener {

    private ImageLoader mImageLoader = null;
    private ImageShow mImageShow = null;
    private ImageCurves mImageCurves = null;
    private ImageBorder mImageBorders = null;
    private ImageStraighten mImageStraighten = null;

    private View mListFx = null;
    private View mListBorders = null;
    private View mListGeometry = null;
    private View mListColors = null;

    private ImageButton mFxButton = null;
    private ImageButton mBorderButton = null;
    private ImageButton mGeometryButton = null;
    private ImageButton mColorsButton = null;

    private ImageButton mVignetteButton = null;
    private ImageButton mCurvesButtonRGB = null;
    private ImageButton mSharpenButton = null;

    private ImageButton mContrastButton = null;
    private ImageButton mSaturationButton = null;
    private ImageButton mTintButton = null;
    private ImageButton mVibranceButton = null;
    private ImageButton mExposureButton = null;
    private ImageButton mShadowRecoveryButton = null;

    private static final int SELECT_PICTURE = 1;
    private static final String LOGTAG = "FilterShowActivity";
    protected static final boolean ANIMATE_PANELS = false;

    private boolean mShowingHistoryPanel = false;
    private boolean mShowingImageStatePanel = false;

    private Vector<ImageShow> mImageViews = new Vector<ImageShow>();
    private Vector<View> mListViews = new Vector<View>();
    private Vector<ImageButton> mBottomPanelButtons = new Vector<ImageButton>();
    private Vector<ImageButton> mColorsPanelButtons = new Vector<ImageButton>();

    private ShareActionProvider mShareActionProvider;
    private File mSharedOutputFile = null;

    private boolean mSharingImage = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.filtershow_activity);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(R.layout.filtershow_actionbar);

        actionBar.getCustomView().setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                saveImage();
            }
        });

        mImageLoader = new ImageLoader(getApplicationContext());

        LinearLayout listFilters = (LinearLayout) findViewById(R.id.listFilters);
        LinearLayout listBorders = (LinearLayout) findViewById(R.id.listBorders);

        mImageShow = (ImageShow) findViewById(R.id.imageShow);
        mImageCurves = (ImageCurves) findViewById(R.id.imageCurves);
        mImageBorders = (ImageBorder) findViewById(R.id.imageBorder);
        mImageStraighten = (ImageStraighten) findViewById(R.id.imageStraighten);

        mImageViews.add(mImageShow);
        mImageViews.add(mImageCurves);
        mImageViews.add(mImageBorders);
        mImageViews.add(mImageStraighten);

        mListFx = findViewById(R.id.fxList);
        mListBorders = findViewById(R.id.bordersList);
        mListGeometry = findViewById(R.id.gemoetryList);
        mListColors = findViewById(R.id.colorsFxList);
        mListViews.add(mListFx);
        mListViews.add(mListBorders);
        mListViews.add(mListGeometry);
        mListViews.add(mListColors);

        mFxButton = (ImageButton) findViewById(R.id.fxButton);
        mBorderButton = (ImageButton) findViewById(R.id.borderButton);
        mGeometryButton = (ImageButton) findViewById(R.id.geometryButton);
        mColorsButton = (ImageButton) findViewById(R.id.colorsButton);
        mBottomPanelButtons.add(mFxButton);
        mBottomPanelButtons.add(mBorderButton);
        mBottomPanelButtons.add(mGeometryButton);
        mBottomPanelButtons.add(mColorsButton);
        mFxButton.setSelected(true);

        mVignetteButton = (ImageButton) findViewById(R.id.vignetteButton);
        mCurvesButtonRGB = (ImageButton) findViewById(R.id.curvesButtonRGB);
        mSharpenButton = (ImageButton) findViewById(R.id.sharpenButton);
        mVibranceButton = (ImageButton) findViewById(R.id.vibranceButton);
        mContrastButton = (ImageButton) findViewById(R.id.contrastButton);
        mSaturationButton = (ImageButton) findViewById(R.id.saturationButton);
        mTintButton = (ImageButton) findViewById(R.id.tintButton);
        mExposureButton = (ImageButton) findViewById(R.id.exposureButton);
        mShadowRecoveryButton = (ImageButton) findViewById(R.id.shadowRecoveryButton);

        mColorsPanelButtons.add(mVignetteButton);
        mColorsPanelButtons.add(mCurvesButtonRGB);
        mColorsPanelButtons.add(mSharpenButton);
        mColorsPanelButtons.add(mContrastButton);
        mColorsPanelButtons.add(mSaturationButton);
        mColorsPanelButtons.add(mTintButton);
        mColorsPanelButtons.add(mVibranceButton);
        mColorsPanelButtons.add(mExposureButton);
        mColorsPanelButtons.add(mShadowRecoveryButton);

        // TODO: instead of click listeners, make the activity the single
        // listener and do a dispatch in the listener callback method.
        findViewById(R.id.showOriginalButton).setOnTouchListener(createOnTouchShowOriginalButton());
        findViewById(R.id.straightenButton).setOnClickListener(createOnClickStraightenButton());
        findViewById(R.id.cropButton).setOnClickListener(createOnClickCropButton());
        findViewById(R.id.rotateButton).setOnClickListener(createOnClickRotateButton());
        findViewById(R.id.flipButton).setOnClickListener(createOnClickFlipButton());

        mVignetteButton.setOnClickListener(createOnClickVignetteButton());
        mCurvesButtonRGB.setOnClickListener(createOnClickCurvesRGBButton());

        mSharpenButton.setOnClickListener(createOnClickSharpenButton());
        mContrastButton.setOnClickListener(createOnClickContrastButton());
        mSaturationButton.setOnClickListener(createOnClickSaturationButton());

        mTintButton.setOnClickListener(createOnClickTintButton());
        mVibranceButton.setOnClickListener(createOnClickVibranceButton());
        mExposureButton.setOnClickListener(createOnClickExposureButton());
        mShadowRecoveryButton.setOnClickListener(createOnClickShadowRecoveryButton());

        mFxButton.setOnClickListener(createOnClickFxButton());
        mBorderButton.setOnClickListener(createOnClickBorderButton());
        mGeometryButton.setOnClickListener(createOnClickGeometryButton());
        mColorsButton.setOnClickListener(createOnClickColorsButton());

        findViewById(R.id.resetOperationsButton).setOnClickListener(
                createOnClickResetOperationsButton());

        ListView operationsList = (ListView) findViewById(R.id.operationsList);
        operationsList.setAdapter(mImageShow.getHistoryAdapter());
        operationsList.setOnItemClickListener(this);
        ListView imageStateList = (ListView) findViewById(R.id.imageStateList);
        imageStateList.setAdapter(mImageShow.getImageStateAdapter());
        mImageLoader.setAdapter((HistoryAdapter) mImageShow.getHistoryAdapter());

        fillListImages(listFilters);
        fillListBorders(listBorders);

        mImageShow.setImageLoader(mImageLoader);
        mImageCurves.setImageLoader(mImageLoader);
        mImageCurves.setMaster(mImageShow);
        mImageBorders.setImageLoader(mImageLoader);
        mImageBorders.setMaster(mImageShow);
        mImageStraighten.setImageLoader(mImageLoader);
        mImageStraighten.setMaster(mImageShow);

        Intent intent = getIntent();
        String data = intent.getDataString();
        if (data != null) {
            Uri uri = Uri.parse(data);
            mImageLoader.loadBitmap(uri);
        } else {
            pickImage();
        }
    }

    public void completeSaveImage(Uri saveUri) {
        if (mSharingImage && mSharedOutputFile != null) {
            // Image saved, we unblock the content provider
            Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                    Uri.encode(mSharedOutputFile.getAbsolutePath()));
            ContentValues values = new ContentValues();
            values.put(SharedImageProvider.PREPARE, false);
            getContentResolver().insert(uri, values);
        }
        setResult(RESULT_OK, new Intent().setData(saveUri));
        finish();
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider arg0, Intent arg1) {
        // First, let's tell the SharedImageProvider that it will need to wait for the image
        Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                Uri.encode(mSharedOutputFile.getAbsolutePath()));
        ContentValues values = new ContentValues();
        values.put(SharedImageProvider.PREPARE, true);
        getContentResolver().insert(uri, values);
        mSharingImage = true;

        // Process and save the image in the background.
        mImageShow.saveImage(this, mSharedOutputFile);
        return true;
    }

    private Intent getDefaultShareIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType(SharedImageProvider.MIME_TYPE);
        mSharedOutputFile = SaveCopyTask.getNewFile(this, mImageLoader.getUri());
        Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                Uri.encode(mSharedOutputFile.getAbsolutePath()));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        return intent;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filtershow_activity_menu, menu);
        MenuItem showHistory = menu.findItem(R.id.operationsButton);
        if (mShowingHistoryPanel) {
            showHistory.setTitle(R.string.hide_history_panel);
        } else {
            showHistory.setTitle(R.string.show_history_panel);
        }
        MenuItem showState = menu.findItem(R.id.showImageStateButton);
        if (mShowingImageStatePanel) {
            showState.setTitle(R.string.hide_imagestate_panel);
        } else {
            showState.setTitle(R.string.show_imagestate_panel);
        }
        mShareActionProvider = (ShareActionProvider) menu.findItem(R.id.menu_share)
                .getActionProvider();
        mShareActionProvider.setShareIntent(getDefaultShareIntent());
        mShareActionProvider.setOnShareTargetSelectedListener(this);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.undoButton: {
                HistoryAdapter adapter = (HistoryAdapter) mImageShow
                        .getHistoryAdapter();
                int position = adapter.undo();
                mImageShow.onItemClick(position);
                mImageShow.showToast("Undo");
                invalidateViews();
                return true;
            }
            case R.id.redoButton: {
                HistoryAdapter adapter = (HistoryAdapter) mImageShow
                        .getHistoryAdapter();
                int position = adapter.redo();
                mImageShow.onItemClick(position);
                mImageShow.showToast("Redo");
                invalidateViews();
                return true;
            }
            case R.id.showImageStateButton: {
                toggleImageStatePanel();
                return true;
            }
            case R.id.operationsButton: {
                toggleHistoryPanel();
                return true;
            }
            case android.R.id.home: {
                saveImage();
                return true;
            }
        }
        return false;
    }

    private void fillListImages(LinearLayout listFilters) {
        // TODO: use listview
        // TODO: load the filters straight from the filesystem
        ImagePreset[] preset = new ImagePreset[9];
        int p = 0;
        preset[p++] = new ImagePreset();
        preset[p++] = new ImagePresetSaturated();
        preset[p++] = new ImagePresetOld();
        preset[p++] = new ImagePresetXProcessing();
        preset[p++] = new ImagePresetBW();
        preset[p++] = new ImagePresetBWRed();
        preset[p++] = new ImagePresetBWGreen();
        preset[p++] = new ImagePresetBWBlue();

        for (int i = 0; i < p; i++) {
            ImageSmallFilter filter = new ImageSmallFilter(getBaseContext());
            preset[i].setIsFx(true);
            filter.setImagePreset(preset[i]);
            filter.setController(this);
            filter.setImageLoader(mImageLoader);
            listFilters.addView(filter);
        }

        // Default preset (original)
        mImageShow.setImagePreset(preset[0]);
    }

    private void fillListBorders(LinearLayout listBorders) {
        // TODO: use listview
        // TODO: load the borders straight from the filesystem
        int p = 0;
        ImageFilter[] borders = new ImageFilter[8];
        borders[p++] = new ImageFilterBorder(null);

        Drawable npd3 = getResources().getDrawable(R.drawable.filtershow_border_film3);
        borders[p++] = new ImageFilterBorder(npd3);
        Drawable npd = getResources().getDrawable(
                R.drawable.filtershow_border_scratch3);
        borders[p++] = new ImageFilterBorder(npd);
        Drawable npd2 = getResources().getDrawable(R.drawable.filtershow_border_black);
        borders[p++] = new ImageFilterBorder(npd2);
        Drawable npd6 = getResources().getDrawable(
                R.drawable.filtershow_border_rounded_black);
        borders[p++] = new ImageFilterBorder(npd6);
        Drawable npd4 = getResources().getDrawable(R.drawable.filtershow_border_white);
        borders[p++] = new ImageFilterBorder(npd4);
        Drawable npd5 = getResources().getDrawable(
                R.drawable.filtershow_border_rounded_white);
        borders[p++] = new ImageFilterBorder(npd5);

        for (int i = 0; i < p; i++) {
            ImageSmallFilter filter = new ImageSmallFilter(getBaseContext());
            filter.setImageFilter(borders[i]);
            filter.setController(this);
            filter.setImageLoader(mImageLoader);
            filter.setShowTitle(false);
            listBorders.addView(filter);
        }
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Some utility functions

    public void showOriginalViews(boolean value) {
        for (ImageShow views : mImageViews) {
            views.showOriginal(value);
        }
    }

    public void invalidateViews() {
        for (ImageShow views : mImageViews) {
            views.invalidate();
        }
    }

    public void hideListViews() {
        for (View view : mListViews) {
            view.setVisibility(View.GONE);
        }
    }

    public void hideImageViews() {
        mImageShow.setShowControls(false); // reset
        for (View view : mImageViews) {
            view.setVisibility(View.GONE);
        }
    }

    public void unselectBottomPanelButtons() {
        for (ImageButton button : mBottomPanelButtons) {
            button.setSelected(false);
        }
    }

    public void unselectPanelButtons(Vector<ImageButton> buttons) {
        for (ImageButton button : buttons) {
            button.setSelected(false);
        }
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Click handlers for the top row buttons

    private OnTouchListener createOnTouchShowOriginalButton() {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean show = false;
                if ((event.getActionMasked() != MotionEvent.ACTION_UP)
                        || (event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    show = true;
                }
                showOriginalViews(show);
                return true;
            }
        };
    }

    // //////////////////////////////////////////////////////////////////////////////
    // imageState panel...

    private void toggleImageStatePanel() {
        final View view = findViewById(R.id.mainPanel);
        final View viewList = findViewById(R.id.imageStatePanel);

        if (mShowingHistoryPanel) {
            findViewById(R.id.historyPanel).setVisibility(View.INVISIBLE);
            mShowingHistoryPanel = false;
        }

        if (!mShowingImageStatePanel) {
            mShowingImageStatePanel = true;
            view.animate().setDuration(200).x(-viewList.getWidth())
                    .withLayer().withEndAction(new Runnable() {
                        public void run() {
                            viewList.setAlpha(0);
                            viewList.setVisibility(View.VISIBLE);
                            viewList.animate().setDuration(100)
                                    .alpha(1.0f).start();
                        }
                    }).start();
        } else {
            mShowingImageStatePanel = false;
            viewList.setVisibility(View.INVISIBLE);
            view.animate().setDuration(200).x(0).withLayer()
                    .start();
        }
        invalidateOptionsMenu();
    }

    // //////////////////////////////////////////////////////////////////////////////
    // history panel...

    private void toggleHistoryPanel() {
        final View view = findViewById(R.id.mainPanel);
        final View viewList = findViewById(R.id.historyPanel);

        if (mShowingImageStatePanel) {
            findViewById(R.id.imageStatePanel).setVisibility(View.INVISIBLE);
            mShowingImageStatePanel = false;
        }

        if (!mShowingHistoryPanel) {
            mShowingHistoryPanel = true;
            view.animate().setDuration(200).x(-viewList.getWidth())
                    .withLayer().withEndAction(new Runnable() {
                        public void run() {
                            viewList.setAlpha(0);
                            viewList.setVisibility(View.VISIBLE);
                            viewList.animate().setDuration(100)
                                    .alpha(1.0f).start();
                        }
                    }).start();
        } else {
            mShowingHistoryPanel = false;
            viewList.setVisibility(View.INVISIBLE);
            view.animate().setDuration(200).x(0).withLayer()
                    .start();
        }
        invalidateOptionsMenu();
    }

    private void resetHistory() {
        HistoryAdapter adapter = (HistoryAdapter) mImageShow
                .getHistoryAdapter();
        adapter.reset();
        ImagePreset original = new ImagePreset(adapter.getItem(0));
        mImageShow.setImagePreset(original);
        invalidateViews();
    }

    // reset button in the history panel.
    private OnClickListener createOnClickResetOperationsButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetHistory();
            }
        };
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Now, let's deal with the bottom panel.

    private OnClickListener createOnClickFxButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                hideListViews();
                unselectBottomPanelButtons();
                mImageShow.setVisibility(View.VISIBLE);
                mListFx.setVisibility(View.VISIBLE);
                mFxButton.setSelected(true);
            }
        };
    }

    private OnClickListener createOnClickBorderButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                hideListViews();
                unselectBottomPanelButtons();
                mImageBorders.setVisibility(View.VISIBLE);
                mListBorders.setVisibility(View.VISIBLE);
                mBorderButton.setSelected(true);
            }
        };
    }

    private OnClickListener createOnClickGeometryButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                hideListViews();
                unselectBottomPanelButtons();
                mImageStraighten.setVisibility(View.VISIBLE);
                mListGeometry.setVisibility(View.VISIBLE);
                mGeometryButton.setSelected(true);

                if (ANIMATE_PANELS) {
                    mListGeometry.setX(mListGeometry.getWidth());
                    mListGeometry.animate().setDuration(200).x(0).withLayer().start();
                }
            }
        };
    }

    private OnClickListener createOnClickColorsButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                hideListViews();
                unselectBottomPanelButtons();
                mListColors.setVisibility(View.VISIBLE);
                mImageShow.setVisibility(View.VISIBLE);
                mColorsButton.setSelected(true);

                if (ANIMATE_PANELS) {
                    View view = findViewById(R.id.listColorsFx);
                    view.setX(mListColors.getWidth());
                    view.animate().setDuration(200).x(0).withLayer().start();
                }
            }
        };
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Geometry sub-panel

    private OnClickListener createOnClickStraightenButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageStraighten.setVisibility(View.VISIBLE);
                mImageStraighten.showToast("Straighten", true);
            }
        };
    }

    private OnClickListener createOnClickCropButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.showToast("Crop", true);
            }
        };
    }

    private OnClickListener createOnClickRotateButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.showToast("Rotate", true);
            }
        };
    }

    private OnClickListener createOnClickFlipButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.showToast("Flip", true);
            }
        };
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Filters sub-panel

    private OnClickListener createOnClickVignetteButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.setShowControls(true);
                ImagePreset preset = mImageShow.getImagePreset();
                ImageFilter filter = preset.getFilter("Vignette");
                if (filter == null) {
                    ImageFilterVignette vignette = new ImageFilterVignette();
                    ImagePreset copy = new ImagePreset(preset);
                    copy.add(vignette);
                    copy.setHistoryName(vignette.getName());
                    copy.setIsFx(false);
                    filter = copy.getFilter("Vignette");
                    mImageShow.setImagePreset(copy);
                }
                mImageShow.setCurrentFilter(filter);
                unselectPanelButtons(mColorsPanelButtons);
                mVignetteButton.setSelected(true);
                invalidateViews();
            }
        };
    }

    private OnClickListener createOnClickCurvesRGBButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageCurves.setVisibility(View.VISIBLE);
                unselectPanelButtons(mColorsPanelButtons);
                mCurvesButtonRGB.setSelected(true);
                mImageCurves.setUseRed(true);
                mImageCurves.setUseGreen(true);
                mImageCurves.setUseBlue(true);
                mImageCurves.reloadCurve();
            }
        };
    }

    private OnClickListener createOnClickSharpenButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                unselectPanelButtons(mColorsPanelButtons);
                mSharpenButton.setSelected(true);
                mImageShow.showToast("Sharpen", true);
                mImageShow.setCurrentFilter(null);
            }
        };
    }

    private OnClickListener createOnClickContrastButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.setShowControls(true);
                ImagePreset preset = mImageShow.getImagePreset();
                ImageFilter filter = preset.getFilter("Contrast");
                if (filter == null) {
                    ImageFilterContrast contrast = new ImageFilterContrast();
                    ImagePreset copy = new ImagePreset(preset);
                    copy.add(contrast);
                    copy.setHistoryName(contrast.getName());
                    copy.setIsFx(false);
                    filter = copy.getFilter("Contrast");
                    mImageShow.setImagePreset(copy);
                }
                mImageShow.setCurrentFilter(filter);
                unselectPanelButtons(mColorsPanelButtons);
                mContrastButton.setSelected(true);
                invalidateViews();
            }
        };
    }

    private OnClickListener createOnClickSaturationButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.setShowControls(true);
                ImagePreset preset = mImageShow.getImagePreset();
                ImageFilter filter = preset.getFilter("Saturated");
                if (filter == null) {
                    ImageFilterSaturated sat = new ImageFilterSaturated();
                    ImagePreset copy = new ImagePreset(preset);
                    copy.add(sat);
                    copy.setHistoryName(sat.getName());
                    copy.setIsFx(false);
                    filter = copy.getFilter("Saturated");
                    mImageShow.setImagePreset(copy);
                }
                mImageShow.setCurrentFilter(filter);
                unselectPanelButtons(mColorsPanelButtons);
                mSaturationButton.setSelected(true);
                invalidateViews();
            }
        };
    }

    private OnClickListener createOnClickTintButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.setShowControls(true);
                ImagePreset preset = mImageShow.getImagePreset();
                ImageFilter filter = preset.getFilter("Hue");
                if (filter == null) {
                    ImageFilterHue contrast = new ImageFilterHue();
                    ImagePreset copy = new ImagePreset(preset);
                    copy.add(contrast);
                    copy.setHistoryName(contrast.getName());
                    copy.setIsFx(false);
                    filter = copy.getFilter("Hue");
                    mImageShow.setImagePreset(copy);
                }
                mImageShow.setCurrentFilter(filter);
                unselectPanelButtons(mColorsPanelButtons);
                mTintButton.setSelected(true);
                invalidateViews();
            }
        };
    }

    private OnClickListener createOnClickVibranceButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.setShowControls(true);
                ImagePreset preset = mImageShow.getImagePreset();
                ImageFilter filter = preset.getFilter("Hue");
                if (filter == null) {
                    ImageFilterHue contrast = new ImageFilterHue();
                    ImagePreset copy = new ImagePreset(preset);
                    copy.add(contrast);
                    copy.setHistoryName(contrast.getName());
                    copy.setIsFx(false);
                    filter = copy.getFilter("Hue");
                    mImageShow.setImagePreset(copy);
                }
                mImageShow.setCurrentFilter(filter);
                unselectPanelButtons(mColorsPanelButtons);
                mVibranceButton.setSelected(true);
                invalidateViews();
            }
        };
    }

    private OnClickListener createOnClickExposureButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.setShowControls(true);
                ImagePreset preset = mImageShow.getImagePreset();
                ImageFilter filter = preset.getFilter("Exposure");
                if (filter == null) {
                    ImageFilterExposure bright = new ImageFilterExposure();
                    ImagePreset copy = new ImagePreset(preset);
                    copy.add(bright);
                    copy.setHistoryName(bright.getName());
                    copy.setIsFx(false);
                    filter = copy.getFilter("Exposure");
                    mImageShow.setImagePreset(copy);
                }
                mImageShow.setCurrentFilter(filter);
                unselectPanelButtons(mColorsPanelButtons);
                mExposureButton.setSelected(true);
                invalidateViews();
            }
        };
    }

    private OnClickListener createOnClickShadowRecoveryButton() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideImageViews();
                mImageShow.setVisibility(View.VISIBLE);
                mImageShow.setShowControls(true);
                ImagePreset preset = mImageShow.getImagePreset();
                ImageFilter filter = preset.getFilter("Hue");
                if (filter == null) {
                    ImageFilterHue contrast = new ImageFilterHue();
                    ImagePreset copy = new ImagePreset(preset);
                    copy.add(contrast);
                    copy.setHistoryName(contrast.getName());
                    copy.setIsFx(false);
                    filter = copy.getFilter("Hue");
                    mImageShow.setImagePreset(copy);
                }
                mImageShow.setCurrentFilter(filter);
                unselectPanelButtons(mColorsPanelButtons);
                mShadowRecoveryButton.setSelected(true);
                invalidateViews();
            }
        };
    }

    // //////////////////////////////////////////////////////////////////////////////

    public float getPixelsFromDip(float value) {
        Resources r = getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                r.getDisplayMetrics());
    }

    public void useImagePreset(ImagePreset preset) {
        if (preset == null) {
            return;
        }
        ImagePreset copy = new ImagePreset(preset);
        mImageShow.setImagePreset(copy);
        if (preset.isFx()) {
            // if it's an FX we rest the curve adjustment too
            mImageCurves.resetCurve();
        }
        invalidateViews();
    }

    public void useImageFilter(ImageFilter imageFilter) {
        if (imageFilter == null) {
            return;
        }
        ImagePreset oldPreset = mImageShow.getImagePreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        // TODO: use a numerical constant instead.
        if (imageFilter.getName().equalsIgnoreCase("Border")) {
            copy.remove("Border");
            copy.setHistoryName("Border");
        }
        copy.add(imageFilter);
        mImageShow.setImagePreset(copy);
        invalidateViews();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        mImageShow.onItemClick(position);
        invalidateViews();
    }

    public void pickImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(LOGTAG, "onActivityResult");
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                mImageLoader.loadBitmap(selectedImageUri);
            }
        }
    }

    public void saveImage() {
        Toast toast = Toast.makeText(getBaseContext(), getString(R.string.saving_image),
                Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        mImageShow.saveImage(this, null);
    }

    static {
        System.loadLibrary("jni_filtershow_filters");
    }

}
