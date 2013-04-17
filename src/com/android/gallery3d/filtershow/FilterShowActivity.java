/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.filtershow.cache.CachingPipeline;
import com.android.gallery3d.filtershow.cache.FilteringPipeline;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.filtershow.editors.BasicEditor;
import com.android.gallery3d.filtershow.editors.EditorCrop;
import com.android.gallery3d.filtershow.editors.EditorDraw;
import com.android.gallery3d.filtershow.editors.EditorFlip;
import com.android.gallery3d.filtershow.editors.EditorInfo;
import com.android.gallery3d.filtershow.editors.EditorManager;
import com.android.gallery3d.filtershow.editors.EditorRedEye;
import com.android.gallery3d.filtershow.editors.EditorRotate;
import com.android.gallery3d.filtershow.editors.EditorStraighten;
import com.android.gallery3d.filtershow.editors.EditorTinyPlanet;
import com.android.gallery3d.filtershow.editors.ImageOnlyEditor;
import com.android.gallery3d.filtershow.filters.*;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.ImageCrop;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.ImageTinyPlanet;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.provider.SharedImageProvider;
import com.android.gallery3d.filtershow.state.StateAdapter;
import com.android.gallery3d.filtershow.state.StatePanel;
import com.android.gallery3d.filtershow.tools.BitmapTask;
import com.android.gallery3d.filtershow.tools.SaveCopyTask;
import com.android.gallery3d.filtershow.ui.FilterIconButton;
import com.android.gallery3d.filtershow.ui.FramedTextButton;
import com.android.gallery3d.filtershow.ui.Spline;
import com.android.gallery3d.util.GalleryUtils;
import com.android.photos.data.GalleryBitmapPool;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Vector;

public class FilterShowActivity extends FragmentActivity implements OnItemClickListener,
        OnShareTargetSelectedListener {

    private String mPanelFragmentTag = "StatePanel";

    // fields for supporting crop action
    public static final String CROP_ACTION = "com.android.camera.action.CROP";
    private CropExtras mCropExtras = null;
    private String mAction = "";
    MasterImage mMasterImage = null;

    private static final long LIMIT_SUPPORTS_HIGHRES = 134217728; // 128Mb

    public static final String TINY_PLANET_ACTION = "com.android.camera.action.TINY_PLANET";
    public static final String LAUNCH_FULLSCREEN = "launch-fullscreen";
    public static final int MAX_BMAP_IN_INTENT = 990000;
    private final PanelController mPanelController = new PanelController();
    private ImageLoader mImageLoader = null;
    private ImageShow mImageShow = null;
    private ImageTinyPlanet mImageTinyPlanet = null;

    private View mSaveButton = null;

    private EditorPlaceHolder mEditorPlaceHolder = new EditorPlaceHolder(this);

    private static final int SELECT_PICTURE = 1;
    private static final String LOGTAG = "FilterShowActivity";
    protected static final boolean ANIMATE_PANELS = true;
    private static int mImageBorderSize = 4; // in percent

    private boolean mShowingTinyPlanet = false;
    private boolean mShowingHistoryPanel = false;
    private boolean mShowingImageStatePanel = false;

    private final Vector<ImageShow> mImageViews = new Vector<ImageShow>();

    private ShareActionProvider mShareActionProvider;
    private File mSharedOutputFile = null;

    private boolean mSharingImage = false;

    private WeakReference<ProgressDialog> mSavingProgressDialog;

    private LoadBitmapTask mLoadBitmapTask;
    private FilterIconButton mNullFxFilter;
    private FilterIconButton mNullBorderFilter;
    private int mIconSeedSize = 140;

    private View mImageCategoryPanel = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        clearGalleryBitmapPool();

        CachingPipeline.createRenderscriptContext(this);
        setupMasterImage();
        setDefaultValues();
        fillEditors();

        loadXML();
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            mShowingImageStatePanel = true;
        }
        if (mShowingHistoryPanel) {
            findViewById(R.id.historyPanel).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.historyPanel).setVisibility(View.GONE);
        }
        if (mShowingImageStatePanel && (savedInstanceState == null)) {
            loadImageStatePanel();
        }

        setDefaultPreset();

        processIntent();
    }

    private void loadXML() {
        setContentView(R.layout.filtershow_activity);

        ((ViewStub) findViewById(R.id.stateCategoryStub)).inflate();
        ((ViewStub) findViewById(R.id.editorPanelStub)).inflate();
        ((ViewStub) findViewById(R.id.historyPanelStub)).inflate();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(R.layout.filtershow_actionbar);

        mSaveButton = actionBar.getCustomView();
        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                saveImage();
            }
        });

        mImageShow = (ImageShow) findViewById(R.id.imageShow);
        mImageTinyPlanet = (ImageTinyPlanet) findViewById(R.id.imageTinyPlanet);
        mImageViews.add(mImageShow);
        mImageViews.add(mImageTinyPlanet);

        setupEditors();

        mEditorPlaceHolder.hide();

        mImageShow.setImageLoader(mImageLoader);
        mImageTinyPlanet.setImageLoader(mImageLoader);

        mPanelController.clear();
        mPanelController.setActivity(this);
        mPanelController.setEditorPlaceHolder(mEditorPlaceHolder);

        mPanelController.addImageView(findViewById(R.id.imageShow));
        mPanelController.addImageView(findViewById(R.id.imageTinyPlanet));

        mPanelController.addPanel(R.id.fxButton, R.id.fxList, 0);
        mPanelController.addPanel(R.id.borderButton, R.id.bordersList, 1);
        mPanelController.addPanel(R.id.geometryButton, R.id.geometryList, 2);
        mPanelController.addPanel(R.id.colorsButton, R.id.colorsFxList, 3);

        fillFx((LinearLayout) findViewById(R.id.listFilters), R.id.fxButton);
        setupBorders();
        fillGeometry();
        fillFilters();

        mPanelController.addView(findViewById(R.id.applyEffect));

        setupHistoryPanel();
        setupStatePanel();

        mImageCategoryPanel = findViewById(R.id.imageCategoryPanel);
    }

    public void hideCategoryPanel() {
        mImageCategoryPanel.setVisibility(View.GONE);
    }

    public void showCategoryPanel() {
        mImageCategoryPanel.setVisibility(View.VISIBLE);
    }

    public void setupHistoryPanel() {
        findViewById(R.id.resetOperationsButton).setOnClickListener(
                createOnClickResetOperationsButton());
        ListView operationsList = (ListView) findViewById(R.id.operationsList);
        operationsList.setAdapter(mMasterImage.getHistory());
        operationsList.setOnItemClickListener(this);
    }

    public void setupStatePanel() {
        mImageLoader.setAdapter(mMasterImage.getHistory());
        mPanelController.setRowPanel(findViewById(R.id.secondRowPanel));
        mPanelController.setUtilityPanel(this, findViewById(R.id.filterButtonsList));
        mPanelController.setCurrentPanel(R.id.fxButton);
    }

    private void fillPanel(Vector<FilterRepresentation> representations, int layoutId, int buttonId) {
        ImageButton button = (ImageButton) findViewById(buttonId);
        LinearLayout layout = (LinearLayout) findViewById(layoutId);

        for (FilterRepresentation representation : representations) {
            setupFilterRepresentationButton(representation, layout, button);
        }
    }

    private void fillFilters() {
        Vector<FilterRepresentation> filtersRepresentations = new Vector<FilterRepresentation>();
        FiltersManager filtersManager = FiltersManager.getManager();
        filtersManager.addEffects(filtersRepresentations);
        fillPanel(filtersRepresentations, R.id.listColorsFx, R.id.colorsButton);
    }

    private void fillGeometry() {
        Vector<FilterRepresentation> filtersRepresentations = new Vector<FilterRepresentation>();
        FiltersManager filtersManager = FiltersManager.getManager();

        GeometryMetadata geo = new GeometryMetadata();
        int[] editorsId = geo.getEditorIds();
        for (int i = 0; i < editorsId.length; i++) {
            int editorId = editorsId[i];
            GeometryMetadata geometry = new GeometryMetadata(geo);
            geometry.setEditorId(editorId);
            EditorInfo editorInfo = (EditorInfo) mEditorPlaceHolder.getEditor(editorId);
            geometry.setTextId(editorInfo.getTextId());
            geometry.setOverlayId(editorInfo.getOverlayId());
            geometry.setOverlayOnly(editorInfo.getOverlayOnly());
            filtersRepresentations.add(geometry);
        }

        filtersManager.addTools(filtersRepresentations);
        fillPanel(filtersRepresentations, R.id.listGeometry, R.id.geometryButton);
    }

    private void processIntent() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra(LAUNCH_FULLSCREEN, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        mAction = intent.getAction();

        if (intent.getData() != null) {
            startLoadBitmap(intent.getData());
        } else {
            pickImage();
        }

        // Handle behavior for various actions
        if (mAction.equalsIgnoreCase(CROP_ACTION)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mCropExtras = new CropExtras(extras.getInt(CropExtras.KEY_OUTPUT_X, 0),
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

                if (mCropExtras.getShowWhenLocked()) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
                }
                mImageShow.getImagePreset().mGeoData.setCropExtras(mCropExtras);

                // FIXME: moving to editors breaks the crop action
                EditorCrop crop = (EditorCrop) mEditorPlaceHolder.getEditor(EditorCrop.ID);

                crop.setExtras(mCropExtras);
                String s = getString(R.string.Fixed);
                crop.setAspectString(s);
                crop.setCropActionFlag(true);
                mPanelController.setFixedAspect(mCropExtras.getAspectX() > 0
                        && mCropExtras.getAspectY() > 0);
            }
        }
    }

    private void setupEditors() {
        mEditorPlaceHolder.setContainer((FrameLayout) findViewById(R.id.editorContainer));
        EditorManager.addEditors(mEditorPlaceHolder);
        mEditorPlaceHolder.setOldViews(mImageViews);
        mEditorPlaceHolder.setImageLoader(mImageLoader);
    }

    private void fillEditors() {
        mEditorPlaceHolder.addEditor(new EditorDraw());
        mEditorPlaceHolder.addEditor(new BasicEditor());
        mEditorPlaceHolder.addEditor(new ImageOnlyEditor());
        mEditorPlaceHolder.addEditor(new EditorTinyPlanet());
        mEditorPlaceHolder.addEditor(new EditorRedEye());
        mEditorPlaceHolder.addEditor(new EditorCrop());
        mEditorPlaceHolder.addEditor(new EditorFlip());
        mEditorPlaceHolder.addEditor(new EditorRotate());
        mEditorPlaceHolder.addEditor(new EditorStraighten());
    }

    private void setDefaultValues() {
        ImageFilter.setActivityForMemoryToasts(this);

        Resources res = getResources();
        FiltersManager.setResources(res);

        ImageShow.setDefaultBackgroundColor(res.getColor(R.color.background_screen));
        // TODO: get those values from XML.
        FramedTextButton.setTextSize((int) getPixelsFromDip(14));
        FramedTextButton.setTrianglePadding((int) getPixelsFromDip(4));
        FramedTextButton.setTriangleSize((int) getPixelsFromDip(10));
        ImageShow.setTextSize((int) getPixelsFromDip(12));
        ImageShow.setTextPadding((int) getPixelsFromDip(10));
        ImageShow.setOriginalTextMargin((int) getPixelsFromDip(4));
        ImageShow.setOriginalTextSize((int) getPixelsFromDip(18));
        ImageShow.setOriginalText(res.getString(R.string.original_picture_text));
        mIconSeedSize = res.getDimensionPixelSize(R.dimen.thumbnail_size);
        // TODO: pick correct value
        // MasterImage.setIconSeedSize(mIconSeedSize);

        Drawable curveHandle = res.getDrawable(R.drawable.camera_crop);
        int curveHandleSize = (int) res.getDimension(R.dimen.crop_indicator_size);
        Spline.setCurveHandle(curveHandle, curveHandleSize);
        Spline.setCurveWidth((int) getPixelsFromDip(3));

        ImageCrop.setAspectTextSize((int) getPixelsFromDip(18));
        ImageCrop.setTouchTolerance((int) getPixelsFromDip(25));
        ImageCrop.setMinCropSize((int) getPixelsFromDip(55));
    }

    private void startLoadBitmap(Uri uri) {
        final View filters = findViewById(R.id.filtersPanel);
        final View loading = findViewById(R.id.loading);
        final View imageShow = findViewById(R.id.imageShow);
        imageShow.setVisibility(View.INVISIBLE);
        filters.setVisibility(View.INVISIBLE);
        loading.setVisibility(View.VISIBLE);

        View tinyPlanetView = findViewById(EditorTinyPlanet.ID);
        if (tinyPlanetView != null) {
            mShowingTinyPlanet = false;
            tinyPlanetView.setVisibility(View.GONE);
        }
        mLoadBitmapTask = new LoadBitmapTask(tinyPlanetView);
        mLoadBitmapTask.execute(uri);
    }

    private void setupBorders() {
        LinearLayout list = (LinearLayout) findViewById(R.id.listBorders);
        Vector<FilterRepresentation> borders = new Vector<FilterRepresentation>();
        ImageButton borderButton = (ImageButton) findViewById(R.id.borderButton);

        // The "no border" implementation
        borders.add(new FilterImageBorderRepresentation(0));

        // Google-build borders
        FiltersManager.getManager().addBorders(borders);

        // Regular borders
        borders.add(new FilterImageBorderRepresentation(R.drawable.filtershow_border_4x5));
        borders.add(new FilterImageBorderRepresentation(R.drawable.filtershow_border_brush));
        borders.add(new FilterImageBorderRepresentation(R.drawable.filtershow_border_grunge));
        borders.add(new FilterImageBorderRepresentation(R.drawable.filtershow_border_sumi_e));
        borders.add(new FilterImageBorderRepresentation(R.drawable.filtershow_border_tape));
        borders.add(new FilterColorBorderRepresentation(Color.BLACK, mImageBorderSize, 0));
        borders.add(new FilterColorBorderRepresentation(Color.BLACK, mImageBorderSize,
                mImageBorderSize));
        borders.add(new FilterColorBorderRepresentation(Color.WHITE, mImageBorderSize, 0));
        borders.add(new FilterColorBorderRepresentation(Color.WHITE, mImageBorderSize,
                mImageBorderSize));
        int creamColor = Color.argb(255, 237, 237, 227);
        borders.add(new FilterColorBorderRepresentation(creamColor, mImageBorderSize, 0));
        borders.add(new FilterColorBorderRepresentation(creamColor, mImageBorderSize,
                mImageBorderSize));
        for (int i = 0; i < borders.size(); i++) {
            FilterRepresentation filter = borders.elementAt(i);
            filter.setName(getString(R.string.borders));
            if (i == 0) {
                filter.setName(getString(R.string.none));
            }
            FilterIconButton b = setupFilterRepresentationButton(filter, list, borderButton);
            if (i == 0) {
                mNullBorderFilter = b;
                mNullBorderFilter.setSelected(true);
            }
        }
    }

    private class LoadBitmapTask extends AsyncTask<Uri, Boolean, Boolean> {
        View mTinyPlanetButton;
        int mBitmapSize;

        public LoadBitmapTask(View button) {
            mTinyPlanetButton = button;
            mBitmapSize = getScreenImageSize();
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            if (!mImageLoader.loadBitmap(params[0], mBitmapSize)) {
                return false;
            }
            publishProgress(mImageLoader.queryLightCycle360());
            return true;
        }

        @Override
        protected void onProgressUpdate(Boolean... values) {
            super.onProgressUpdate(values);
            if (isCancelled()) {
                return;
            }
            if (values[0]) {
                mShowingTinyPlanet = true;
                mTinyPlanetButton.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {

            if (isCancelled()) {
                return;
            }

            if (!result) {
                cannotLoadImage();
            }

            final View loading = findViewById(R.id.loading);
            loading.setVisibility(View.GONE);
            final View filters = findViewById(R.id.filtersPanel);
            filters.setVisibility(View.VISIBLE);
            if (PanelController.useAnimationsLayer()) {
                float y = filters.getY();
                filters.setY(y + filters.getHeight());
                filters.animate().setDuration(600).y(y).withLayer().start();
            }
            final View imageShow = findViewById(R.id.imageShow);
            imageShow.setVisibility(View.VISIBLE);

            Bitmap largeBitmap = mImageLoader.getOriginalBitmapLarge();
            FilteringPipeline pipeline = FilteringPipeline.getPipeline();
            pipeline.setOriginal(largeBitmap);
            float previewScale = (float) largeBitmap.getWidth() / (float) mImageLoader.getOriginalBounds().width();
            pipeline.setPreviewScaleFactor(previewScale);
            Bitmap highresBitmap = mImageLoader.getOriginalBitmapHighres();
            if (highresBitmap != null) {
                float highResPreviewScale = (float) highresBitmap.getWidth() / (float) mImageLoader.getOriginalBounds().width();
                pipeline.setHighResPreviewScaleFactor(highResPreviewScale);
            }
            pipeline.turnOnPipeline(true);
            MasterImage.getImage().setOriginalGeometry(largeBitmap);
            MasterImage.getImage().getHistory().setOriginalBitmap(mImageLoader.getOriginalBitmapSmall());
            mLoadBitmapTask = null;

            if (mAction == CROP_ACTION) {
                mPanelController.showComponent(findViewById(EditorCrop.ID));
            } else if (mAction == TINY_PLANET_ACTION) {
                mPanelController.showComponent(findViewById(EditorTinyPlanet.ID));
            }
            super.onPostExecute(result);
        }

    }

    private void clearGalleryBitmapPool() {
        (new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // Free memory held in Gallery's Bitmap pool.  May be O(n) for n bitmaps.
                GalleryBitmapPool.getInstance().clear();
                return null;
            }
        }).execute();
    }

    @Override
    protected void onDestroy() {
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
        }
        // TODO:  Using singletons is a bad design choice for many of these
        // due static reference leaks and in general.  Please refactor.
        FilteringPipeline.getPipeline().turnOnPipeline(false);
        MasterImage.reset();
        FilteringPipeline.reset();
        ImageFilter.resetStatics();
        FiltersManager.getPreviewManager().freeRSFilterScripts();
        FiltersManager.getManager().freeRSFilterScripts();
        FiltersManager.getHighresManager().freeRSFilterScripts();
        FiltersManager.reset();
        CachingPipeline.destroyRenderScriptContext();
        super.onDestroy();
    }

    private int translateMainPanel(View viewPanel) {
        int accessoryPanelWidth = viewPanel.getWidth();
        if (accessoryPanelWidth == 0) {
            // TODO: fixes this by using a fragment. Currently,
            // the first time we get called the panel hasn't been
            // layed out yet, so we get a size zero.
            accessoryPanelWidth = (int) getPixelsFromDip(200);
        }
        int mainViewWidth = findViewById(R.id.mainView).getWidth();
        int mainPanelWidth = mImageShow.getDisplayedImageBounds().width();
        if (mainPanelWidth == 0) {
            mainPanelWidth = mainViewWidth;
        }
        int filtersPanelWidth = findViewById(R.id.filtersPanel).getWidth();
        if (mainPanelWidth < filtersPanelWidth) {
            mainPanelWidth = filtersPanelWidth;
        }
        int leftOver = mainViewWidth - mainPanelWidth - accessoryPanelWidth;
        if (leftOver < 0) {
            return -accessoryPanelWidth;
        }
        return 0;
    }

    private int getScreenImageSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        display.getMetrics(metrics);
        int msize = Math.min(size.x, size.y);
        return (133 * msize) / metrics.densityDpi;
    }

    private void showSavingProgress(String albumName) {
        ProgressDialog progress;
        if (mSavingProgressDialog != null) {
            progress = mSavingProgressDialog.get();
            if (progress != null) {
                progress.show();
                return;
            }
        }
        // TODO: Allow cancellation of the saving process
        String progressText;
        if (albumName == null) {
            progressText = getString(R.string.saving_image);
        } else {
            progressText = getString(R.string.filtershow_saving_image, albumName);
        }
        progress = ProgressDialog.show(this, "", progressText, true, false);
        mSavingProgressDialog = new WeakReference<ProgressDialog>(progress);
    }

    private void hideSavingProgress() {
        if (mSavingProgressDialog != null) {
            ProgressDialog progress = mSavingProgressDialog.get();
            if (progress != null)
                progress.dismiss();
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
        hideSavingProgress();
        finish();
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider arg0, Intent arg1) {
        // First, let's tell the SharedImageProvider that it will need to wait
        // for the image
        Uri uri = Uri.withAppendedPath(SharedImageProvider.CONTENT_URI,
                Uri.encode(mSharedOutputFile.getAbsolutePath()));
        ContentValues values = new ContentValues();
        values.put(SharedImageProvider.PREPARE, true);
        getContentResolver().insert(uri, values);
        mSharingImage = true;

        // Process and save the image in the background.
        showSavingProgress(null);
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

        MenuItem undoItem = menu.findItem(R.id.undoButton);
        MenuItem redoItem = menu.findItem(R.id.redoButton);
        MenuItem resetItem = menu.findItem(R.id.resetHistoryButton);
        mMasterImage.getHistory().setMenuItems(undoItem, redoItem, resetItem);
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.undoButton: {
                mPanelController.resetParameters();
                HistoryAdapter adapter = mMasterImage.getHistory();
                int position = adapter.undo();
                mMasterImage.onHistoryItemClick(position);
                mImageShow.showToast("Undo");
                invalidateViews();
                return true;
            }
            case R.id.redoButton: {
                HistoryAdapter adapter = mMasterImage.getHistory();
                int position = adapter.redo();
                mMasterImage.onHistoryItemClick(position);
                mImageShow.showToast("Redo");
                invalidateViews();
                return true;
            }
            case R.id.resetHistoryButton: {
                resetHistory();
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

    public void enableSave(boolean enable) {
        if (mSaveButton != null)
            mSaveButton.setEnabled(enable);
    }

    public FilterIconButton setupFilterRepresentationButton(FilterRepresentation representation, LinearLayout panel, View button) {
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        FilterIconButton icon = (FilterIconButton) inflater.inflate(R.layout.filtericonbutton,
                panel, false);
        if (representation.getTextId() != 0) {
            representation.setName(getString(representation.getTextId()));
        }
        String text = representation.getName();
        icon.setup(text, mPanelController, panel);
        icon.setFilterRepresentation(representation);
        icon.setId(representation.getEditorId());
        mPanelController.addComponent(button, icon);
        panel.addView(icon);
        return icon;
    }

    private void fillFx(LinearLayout listFilters, int buttonId) {
        // TODO: use listview
        // TODO: load the filters straight from the filesystem

        FilterFxRepresentation[] fxArray = new FilterFxRepresentation[18];
        int p = 0;

        int[] drawid = {
                R.drawable.filtershow_fx_0005_punch,
//                R.drawable.filtershow_fx_0000_vintage,
//                R.drawable.filtershow_fx_0004_bw_contrast,
                R.drawable.filtershow_fx_0002_bleach,
                R.drawable.filtershow_fx_0001_instant,
                R.drawable.filtershow_fx_0007_washout,
                R.drawable.filtershow_fx_0003_blue_crush,
                R.drawable.filtershow_fx_0008_washout_color,
                R.drawable.filtershow_fx_0006_x_process
        };

        int[] fxNameid = {
                R.string.ffx_punch,
//                R.string.ffx_vintage,
//                R.string.ffx_bw_contrast,
                R.string.ffx_bleach,
                R.string.ffx_instant,
                R.string.ffx_washout,
                R.string.ffx_blue_crush,
                R.string.ffx_washout_color,
                R.string.ffx_x_process
        };

        for (int i = 0; i < drawid.length; i++) {
            FilterFxRepresentation fx = new FilterFxRepresentation(getString(fxNameid[i]), drawid[i], fxNameid[i]);
            fxArray[p++] = fx;
        }

        ImageButton button = (ImageButton) findViewById(buttonId);

        FilterFxRepresentation nullFx = new FilterFxRepresentation(getString(R.string.none), 0, R.string.none);
        mNullFxFilter = setupFilterRepresentationButton(nullFx, listFilters, button);
        mNullFxFilter.setSelected(true);

        Vector<FilterRepresentation> filtersRepresentations = new Vector<FilterRepresentation>();
        FiltersManager.getManager().addLooks(filtersRepresentations);
        for (FilterRepresentation representation : filtersRepresentations) {
            setupFilterRepresentationButton(representation, listFilters, button);
        }

        for (int i = 0; i < p; i++) {
            setupFilterRepresentationButton(fxArray[i], listFilters, button);
        }
    }

    public void setDefaultPreset() {
        // Default preset (original)
        ImagePreset preset = new ImagePreset(getString(R.string.history_original)); // empty
        preset.setImageLoader(mImageLoader);

        mMasterImage.setPreset(preset, true);
    }

    // //////////////////////////////////////////////////////////////////////////////
    // Some utility functions
    // TODO: finish the cleanup.

    public void invalidateViews() {
        for (ImageShow views : mImageViews) {
            views.invalidate();
            views.updateImage();
        }
    }

    public void hideImageViews() {
        for (View view : mImageViews) {
            view.setVisibility(View.GONE);
        }
        mEditorPlaceHolder.hide();
    }

    // //////////////////////////////////////////////////////////////////////////////
    // imageState panel...

    public boolean isShowingHistoryPanel() {
        return mShowingHistoryPanel;
    }

    private void toggleImageStatePanel() {
        invalidateOptionsMenu();
    }

    private void loadImageStatePanel() {
        StatePanel statePanel = new StatePanel();
        if (findViewById(R.id.state_panel_container) != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.state_panel_container, statePanel, mPanelFragmentTag);
            transaction.commit();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        setDefaultValues();
        loadXML();
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            mShowingImageStatePanel = true;
        } else if (mShowingImageStatePanel) {
            toggleImageStatePanel();
        }
        if (mShowingImageStatePanel) {
            loadImageStatePanel();
        }
        if (mShowingHistoryPanel) {
            toggleHistoryPanel();
        }
        if (mShowingTinyPlanet == false) {
            View tinyPlanetView = findViewById(EditorTinyPlanet.ID);
            if (tinyPlanetView != null) {
                tinyPlanetView.setVisibility(View.GONE);
            }
        }
        final View loading = findViewById(R.id.loading);
        loading.setVisibility(View.GONE);
    }

    public void setupMasterImage() {
        mImageLoader = new ImageLoader(this, getApplicationContext());

        HistoryAdapter mHistoryAdapter = new HistoryAdapter(
                this, R.layout.filtershow_history_operation_row,
                R.id.rowTextView);

        StateAdapter mImageStateAdapter = new StateAdapter(this, 0);
        MasterImage.reset();
        mMasterImage = MasterImage.getImage();
        mMasterImage.setHistoryAdapter(mHistoryAdapter);
        mMasterImage.setStateAdapter(mImageStateAdapter);
        mMasterImage.setActivity(this);
        mMasterImage.setImageLoader(mImageLoader);

        if (Runtime.getRuntime().maxMemory() > LIMIT_SUPPORTS_HIGHRES) {
            mMasterImage.setSupportsHighRes(true);
        } else {
            mMasterImage.setSupportsHighRes(false);
        }
    }

    // //////////////////////////////////////////////////////////////////////////////
    // history panel...

    public void toggleHistoryPanel() {
        final View view = findViewById(R.id.mainPanel);
        final View viewList = findViewById(R.id.historyPanel);

        int translate = translateMainPanel(viewList);
        if (!mShowingHistoryPanel) {
            mShowingHistoryPanel = true;
            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT) {
                // If portrait, always remove the state panel
                mShowingImageStatePanel = false;
                if (PanelController.useAnimationsLayer()) {
                    view.animate().setDuration(200).x(translate)
                            .withLayer().withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            viewList.setAlpha(0);
                            viewList.setVisibility(View.VISIBLE);
                            viewList.animate().setDuration(100)
                                    .alpha(1.0f).start();
                        }
                    }).start();
                } else {
                    view.setX(translate);
                    viewList.setAlpha(0);
                    viewList.setVisibility(View.VISIBLE);
                    viewList.animate().setDuration(100)
                            .alpha(1.0f).start();
                }
            } else {
                findViewById(R.id.filtersPanel).setVisibility(View.GONE);
                viewList.setVisibility(View.VISIBLE);
            }
        } else {
            mShowingHistoryPanel = false;
            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT) {
                viewList.setVisibility(View.INVISIBLE);
                if (PanelController.useAnimationsLayer()) {
                    view.animate().setDuration(200).x(0).withLayer()
                            .start();
                } else {
                    view.setX(0);
                }
            } else {
                viewList.setVisibility(View.GONE);
                findViewById(R.id.filtersPanel).setVisibility(View.VISIBLE);
            }
        }
        invalidateOptionsMenu();
    }

    public void dispatchNullFilterClick() {
        mNullFxFilter.onClick(mNullFxFilter);
        mNullBorderFilter.onClick(mNullBorderFilter);
    }

    void resetHistory() {
        dispatchNullFilterClick();
        HistoryAdapter adapter = mMasterImage.getHistory();
        adapter.reset();
        ImagePreset original = new ImagePreset(adapter.getItem(0));
        mMasterImage.setPreset(original, true);
        mPanelController.resetParameters();
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

    @Override
    public void onBackPressed() {
        if (mPanelController.onBackPressed()) {
            if (detectSpecialExitCases()) {
                saveImage();
            } else if(!mImageShow.hasModifications()) {
                done();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.unsaved).setTitle(R.string.save_before_exit);
                builder.setPositiveButton(R.string.save_and_exit, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        saveImage();
                    }
                });
                builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        done();
                    }
                });
                builder.show();
            }
        }
    }

    public PanelController getPanelController() {
        return mPanelController;
    }

    public void cannotLoadImage() {
        CharSequence text = getString(R.string.cannot_load_image);
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
        finish();
    }

    // //////////////////////////////////////////////////////////////////////////////

    public float getPixelsFromDip(float value) {
        Resources r = getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                r.getDisplayMetrics());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        mMasterImage.onHistoryItemClick(position);
        invalidateViews();
    }

    public void pickImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                startLoadBitmap(selectedImageUri);
            }
        }
    }

    private boolean mSaveToExtraUri = false;
    private boolean mSaveAsWallpaper = false;
    private boolean mReturnAsExtra = false;
    private boolean mOutputted = false;

    public void saveImage() {
        handleSpecialExitCases();
        if (!mOutputted) {
            if (mImageShow.hasModifications()) {
                // Get the name of the album, to which the image will be saved
                File saveDir = SaveCopyTask.getFinalSaveDirectory(this, mImageLoader.getUri());
                int bucketId = GalleryUtils.getBucketId(saveDir.getPath());
                String albumName = LocalAlbum.getLocalizedName(getResources(), bucketId, null);
                showSavingProgress(albumName);
                mImageShow.saveImage(this, null);
            } else {
                done();
            }
        }
    }

    public boolean detectSpecialExitCases() {
        return mCropExtras != null && (mCropExtras.getExtraOutput() != null
                || mCropExtras.getSetAsWallpaper() || mCropExtras.getReturnData());
    }

    public void handleSpecialExitCases() {
        if (mCropExtras != null) {
            if (mCropExtras.getExtraOutput() != null) {
                mSaveToExtraUri = true;
                mOutputted = true;
            }
            if (mCropExtras.getSetAsWallpaper()) {
                mSaveAsWallpaper = true;
                mOutputted = true;
            }
            if (mCropExtras.getReturnData()) {
                mReturnAsExtra = true;
                mOutputted = true;
            }
            if (mOutputted) {
                mImageShow.getImagePreset().mGeoData.setUseCropExtrasFlag(true);
                showSavingProgress(null);
                mImageShow.returnFilteredResult(this);
            }
        }
    }

    public void onFilteredResult(Bitmap filtered) {
        Intent intent = new Intent();
        intent.putExtra(CropExtras.KEY_CROPPED_RECT, mImageShow.getImageCropBounds());
        if (mSaveToExtraUri) {
            mImageShow.saveToUri(filtered, mCropExtras.getExtraOutput(),
                    mCropExtras.getOutputFormat(), this);
        }
        if (mSaveAsWallpaper) {
            setWallpaperInBackground(filtered);
        }
        if (mReturnAsExtra) {
            if (filtered != null) {
                int bmapSize = filtered.getRowBytes() * filtered.getHeight();
                /*
                 * Max size of Binder transaction buffer is 1Mb, so constrain
                 * Bitmap to be somewhat less than this, otherwise we get
                 * TransactionTooLargeExceptions.
                 */
                if (bmapSize > MAX_BMAP_IN_INTENT) {
                    Log.w(LOGTAG, "Bitmap too large to be returned via intent");
                } else {
                    intent.putExtra(CropExtras.KEY_DATA, filtered);
                }
            }
        }
        setResult(RESULT_OK, intent);
        if (!mSaveToExtraUri) {
            done();
        }
    }

    void setWallpaperInBackground(final Bitmap bmap) {
        Toast.makeText(this, R.string.setting_wallpaper, Toast.LENGTH_LONG).show();
        BitmapTask.Callbacks<FilterShowActivity> cb = new BitmapTask.Callbacks<FilterShowActivity>() {
            @Override
            public void onComplete(Bitmap result) {}

            @Override
            public void onCancel() {}

            @Override
            public Bitmap onExecute(FilterShowActivity param) {
                try {
                    WallpaperManager.getInstance(param).setBitmap(bmap);
                } catch (IOException e) {
                    Log.w(LOGTAG, "fail to set wall paper", e);
                }
                return null;
            }
        };
        (new BitmapTask<FilterShowActivity>(cb)).execute(this);
    }

    public void done() {
        if (mOutputted) {
            hideSavingProgress();
        }
        finish();
    }

    static {
        System.loadLibrary("jni_filtershow_filters");
    }

}
