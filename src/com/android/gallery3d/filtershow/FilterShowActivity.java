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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;

import android.widget.Toast;
import com.android.gallery3d.R;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.filtershow.cache.CachingPipeline;
import com.android.gallery3d.filtershow.cache.FilteringPipeline;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.category.*;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.filtershow.editors.*;
import com.android.gallery3d.filtershow.filters.*;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.ImageCrop;
import com.android.gallery3d.filtershow.imageshow.ImageShow;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;
import com.android.gallery3d.filtershow.provider.SharedImageProvider;
import com.android.gallery3d.filtershow.state.StateAdapter;
import com.android.gallery3d.filtershow.tools.BitmapTask;
import com.android.gallery3d.filtershow.tools.SaveCopyTask;
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

    // fields for supporting crop action
    public static final String CROP_ACTION = "com.android.camera.action.CROP";
    private CropExtras mCropExtras = null;
    private String mAction = "";
    MasterImage mMasterImage = null;

    private static final long LIMIT_SUPPORTS_HIGHRES = 134217728; // 128Mb

    public static final String TINY_PLANET_ACTION = "com.android.camera.action.TINY_PLANET";
    public static final String LAUNCH_FULLSCREEN = "launch-fullscreen";
    public static final int MAX_BMAP_IN_INTENT = 990000;
    private ImageLoader mImageLoader = null;
    private ImageShow mImageShow = null;

    private View mSaveButton = null;

    private EditorPlaceHolder mEditorPlaceHolder = new EditorPlaceHolder(this);

    private static final int SELECT_PICTURE = 1;
    private static final String LOGTAG = "FilterShowActivity";
    protected static final boolean ANIMATE_PANELS = true;

    private boolean mShowingTinyPlanet = false;
    private boolean mShowingImageStatePanel = false;

    private final Vector<ImageShow> mImageViews = new Vector<ImageShow>();

    private ShareActionProvider mShareActionProvider;
    private File mSharedOutputFile = null;

    private boolean mSharingImage = false;

    private WeakReference<ProgressDialog> mSavingProgressDialog;

    private LoadBitmapTask mLoadBitmapTask;
    private boolean mLoading = true;

    private CategoryAdapter mCategoryLooksAdapter = null;
    private CategoryAdapter mCategoryBordersAdapter = null;
    private CategoryAdapter mCategoryGeometryAdapter = null;
    private CategoryAdapter mCategoryFiltersAdapter = null;
    private int mCurrentPanel = MainPanel.LOOKS;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean onlyUsePortrait = getResources().getBoolean(R.bool.only_use_portrait);
        if (onlyUsePortrait) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        MasterImage.setMaster(mMasterImage);

        clearGalleryBitmapPool();

        CachingPipeline.createRenderscriptContext(this);
        setupMasterImage();
        setDefaultValues();
        fillEditors();

        loadXML();
        loadMainPanel();

        setDefaultPreset();

        processIntent();
    }

    public boolean isShowingImageStatePanel() {
        return mShowingImageStatePanel;
    }

    public void loadMainPanel() {
        if (findViewById(R.id.main_panel_container) == null) {
            return;
        }
        MainPanel panel = new MainPanel();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_panel_container, panel, MainPanel.FRAGMENT_TAG);
        transaction.commit();
    }

    public void loadEditorPanel(FilterRepresentation representation,
                                final Editor currentEditor) {
        if (representation.getEditorId() == ImageOnlyEditor.ID) {
            currentEditor.getImageShow().select();
            currentEditor.reflectCurrentFilter();
            return;
        }
        final int currentId = currentEditor.getID();
        Runnable showEditor = new Runnable() {
            @Override
            public void run() {
                EditorPanel panel = new EditorPanel();
                panel.setEditor(currentId);
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG));
                transaction.replace(R.id.main_panel_container, panel, MainPanel.FRAGMENT_TAG);
                transaction.commit();
            }
        };
        Fragment main = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        boolean doAnimation = false;
        if (mShowingImageStatePanel
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            doAnimation = true;
        }
        if (doAnimation && main != null && main instanceof MainPanel) {
            MainPanel mainPanel = (MainPanel) main;
            View container = mainPanel.getView().findViewById(R.id.category_panel_container);
            View bottom = mainPanel.getView().findViewById(R.id.bottom_panel);
            int panelHeight = container.getHeight() + bottom.getHeight();
            mainPanel.getView().animate().translationY(panelHeight).withEndAction(showEditor).start();
        } else {
            showEditor.run();
        }
    }

    private void loadXML() {
        setContentView(R.layout.filtershow_activity);

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
        mImageViews.add(mImageShow);

        setupEditors();

        mEditorPlaceHolder.hide();

        mImageShow.setImageLoader(mImageLoader);

        fillFx();
        fillBorders();
        fillGeometry();
        fillFilters();

        setupStatePanel();
    }

    public void setupStatePanel() {
        mImageLoader.setAdapter(mMasterImage.getHistory());
    }

    private void fillFilters() {
        Vector<FilterRepresentation> filtersRepresentations = new Vector<FilterRepresentation>();
        FiltersManager filtersManager = FiltersManager.getManager();
        filtersManager.addEffects(filtersRepresentations);

        mCategoryFiltersAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : filtersRepresentations) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryFiltersAdapter.add(new Action(this, representation));
        }
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
            if (geometry.getTextId() != 0) {
                geometry.setName(getString(geometry.getTextId()));
            }
            filtersRepresentations.add(geometry);
        }

        filtersManager.addTools(filtersRepresentations);

        mCategoryGeometryAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : filtersRepresentations) {
            mCategoryGeometryAdapter.add(new Action(this, representation));
        }
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

        CategoryView.setMargin((int) getPixelsFromDip(8));
        CategoryView.setTextSize((int) getPixelsFromDip(16));

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

        Drawable curveHandle = res.getDrawable(R.drawable.camera_crop);
        int curveHandleSize = (int) res.getDimension(R.dimen.crop_indicator_size);
        Spline.setCurveHandle(curveHandle, curveHandleSize);
        Spline.setCurveWidth((int) getPixelsFromDip(3));

        ImageCrop.setAspectTextSize((int) getPixelsFromDip(18));
        ImageCrop.setTouchTolerance((int) getPixelsFromDip(25));
        ImageCrop.setMinCropSize((int) getPixelsFromDip(55));
    }

    private void startLoadBitmap(Uri uri) {
        mLoading = true;
        final View loading = findViewById(R.id.loading);
        final View imageShow = findViewById(R.id.imageShow);
        imageShow.setVisibility(View.INVISIBLE);
        loading.setVisibility(View.VISIBLE);
        mShowingTinyPlanet = false;
        mLoadBitmapTask = new LoadBitmapTask();
        mLoadBitmapTask.execute(uri);
    }

    private void fillBorders() {
        Vector<FilterRepresentation> borders = new Vector<FilterRepresentation>();

        // The "no border" implementation
        borders.add(new FilterImageBorderRepresentation(0));

        // Google-build borders
        FiltersManager.getManager().addBorders(this, borders);

        for (int i = 0; i < borders.size(); i++) {
            FilterRepresentation filter = borders.elementAt(i);
            if (i == 0) {
                filter.setName(getString(R.string.none));
            }
        }

        mCategoryBordersAdapter = new CategoryAdapter(this);
        for (FilterRepresentation representation : borders) {
            if (representation.getTextId() != 0) {
                representation.setName(getString(representation.getTextId()));
            }
            mCategoryBordersAdapter.add(new Action(this, representation, Action.FULL_VIEW));
        }
    }

    public CategoryAdapter getCategoryLooksAdapter() {
        return mCategoryLooksAdapter;
    }

    public CategoryAdapter getCategoryBordersAdapter() {
        return mCategoryBordersAdapter;
    }

    public CategoryAdapter getCategoryGeometryAdapter() {
        return mCategoryGeometryAdapter;
    }

    public CategoryAdapter getCategoryFiltersAdapter() {
        return mCategoryFiltersAdapter;
    }

    public void removeFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        copy.removeFilter(filterRepresentation);
        MasterImage.getImage().setPreset(copy, true);
        if (MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            FilterRepresentation lastRepresentation = copy.getLastRepresentation();
            MasterImage.getImage().setCurrentFilterRepresentation(lastRepresentation);
        }
    }

    public void useFilterRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation == null) {
            return;
        }
        if (MasterImage.getImage().getCurrentFilterRepresentation() == filterRepresentation) {
            return;
        }
        ImagePreset oldPreset = MasterImage.getImage().getPreset();
        ImagePreset copy = new ImagePreset(oldPreset);
        FilterRepresentation representation = copy.getRepresentation(filterRepresentation);
        if (representation == null) {
            copy.addFilter(filterRepresentation);
        } else {
            if (filterRepresentation.allowsMultipleInstances()) {
                representation.updateTempParametersFrom(filterRepresentation);
                copy.setHistoryName(filterRepresentation.getName());
                representation.synchronizeRepresentation();
            }
            filterRepresentation = representation;
        }
        MasterImage.getImage().setPreset(copy, true);
        MasterImage.getImage().setCurrentFilterRepresentation(filterRepresentation);
    }

    public void showRepresentation(FilterRepresentation representation) {
        if (representation == null) {
            return;
        }
        useFilterRepresentation(representation);

        // show representation
        Editor mCurrentEditor = mEditorPlaceHolder.showEditor(representation.getEditorId());
        loadEditorPanel(representation, mCurrentEditor);
    }

    public Editor getEditor(int editorID) {
        return mEditorPlaceHolder.getEditor(editorID);
    }

    public void setCurrentPanel(int currentPanel) {
        mCurrentPanel = currentPanel;
    }

    public int getCurrentPanel() {
        return mCurrentPanel;
    }

    private class LoadBitmapTask extends AsyncTask<Uri, Boolean, Boolean> {
        int mBitmapSize;

        public LoadBitmapTask() {
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
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            MasterImage.setMaster(mMasterImage);
            if (isCancelled()) {
                return;
            }

            if (!result) {
                cannotLoadImage();
            }

            final View loading = findViewById(R.id.loading);
            loading.setVisibility(View.GONE);
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
            if (!mShowingTinyPlanet) {
                mCategoryFiltersAdapter.removeTinyPlanet();
            }
            pipeline.turnOnPipeline(true);
            MasterImage.getImage().setOriginalGeometry(largeBitmap);
            mCategoryLooksAdapter.imageLoaded();
            mCategoryBordersAdapter.imageLoaded();
            mCategoryGeometryAdapter.imageLoaded();
            mCategoryFiltersAdapter.imageLoaded();
            mLoadBitmapTask = null;

            if (mAction == TINY_PLANET_ACTION) {
                showRepresentation(mCategoryFiltersAdapter.getTinyPlanet());
            }
            mLoading = false;
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
        // TODO:  refactor, don't use so many singletons.
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
        rsPause();
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        rsResume();
        if (mShareActionProvider != null) {
            mShareActionProvider.setOnShareTargetSelectedListener(this);
        }
    }

    private void rsResume() {
        ImageFilter.setActivityForMemoryToasts(this);
        MasterImage.setMaster(mMasterImage);
        if (CachingPipeline.getRenderScriptContext() == null) {
            CachingPipeline.createRenderscriptContext(this);
        }
        FiltersManager.setResources(getResources());
        if (!mLoading) {
            Bitmap largeBitmap = mImageLoader.getOriginalBitmapLarge();
            FilteringPipeline pipeline = FilteringPipeline.getPipeline();
            pipeline.setOriginal(largeBitmap);
            float previewScale = (float) largeBitmap.getWidth() /
                    (float) mImageLoader.getOriginalBounds().width();
            pipeline.setPreviewScaleFactor(previewScale);
            Bitmap highresBitmap = mImageLoader.getOriginalBitmapHighres();
            if (highresBitmap != null) {
                float highResPreviewScale = (float) highresBitmap.getWidth() /
                        (float) mImageLoader.getOriginalBounds().width();
                pipeline.setHighResPreviewScaleFactor(highResPreviewScale);
            }
            pipeline.turnOnPipeline(true);
            MasterImage.getImage().setOriginalGeometry(largeBitmap);
        }
    }

    private void rsPause() {
        FilteringPipeline.getPipeline().turnOnPipeline(false);
        FilteringPipeline.reset();
        ImageFilter.resetStatics();
        FiltersManager.getPreviewManager().freeRSFilterScripts();
        FiltersManager.getManager().freeRSFilterScripts();
        FiltersManager.getHighresManager().freeRSFilterScripts();
        FiltersManager.reset();
        CachingPipeline.destroyRenderScriptContext();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.undoButton: {
                HistoryAdapter adapter = mMasterImage.getHistory();
                int position = adapter.undo();
                mMasterImage.onHistoryItemClick(position);
                mImageShow.showToast("Undo");
                backToMain();
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

    private void fillFx() {
        FilterFxRepresentation nullFx =
                new FilterFxRepresentation(getString(R.string.none), 0, R.string.none);
        Vector<FilterRepresentation> filtersRepresentations = new Vector<FilterRepresentation>();
        FiltersManager.getManager().addLooks(this, filtersRepresentations);

        mCategoryLooksAdapter = new CategoryAdapter(this);
        int verticalItemHeight = (int) getResources().getDimension(R.dimen.action_item_height);
        mCategoryLooksAdapter.setItemHeight(verticalItemHeight);
        mCategoryLooksAdapter.add(new Action(this, nullFx, Action.FULL_VIEW));
        for (FilterRepresentation representation : filtersRepresentations) {
            mCategoryLooksAdapter.add(new Action(this, representation, Action.FULL_VIEW));
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

    public void toggleImageStatePanel() {
        invalidateOptionsMenu();
        mShowingImageStatePanel = !mShowingImageStatePanel;
        Fragment panel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (panel != null) {
            if (panel instanceof EditorPanel) {
                EditorPanel editorPanel = (EditorPanel) panel;
                editorPanel.showImageStatePanel(mShowingImageStatePanel);
            } else if (panel instanceof MainPanel) {
                MainPanel mainPanel = (MainPanel) panel;
                mainPanel.showImageStatePanel(mShowingImageStatePanel);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        setDefaultValues();
        loadXML();
        loadMainPanel();

        // mLoadBitmapTask==null implies you have looked at the intent
        if (!mShowingTinyPlanet && (mLoadBitmapTask == null)) {
            mCategoryFiltersAdapter.removeTinyPlanet();
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

    void resetHistory() {
        HistoryAdapter adapter = mMasterImage.getHistory();
        adapter.reset();
        ImagePreset original = new ImagePreset(adapter.getItem(0));
        mMasterImage.setPreset(original, true);
        invalidateViews();
        backToMain();
    }

    public void showDefaultImageView() {
        mEditorPlaceHolder.hide();
        mImageShow.setVisibility(View.VISIBLE);
        MasterImage.getImage().setCurrentFilter(null);
        MasterImage.getImage().setCurrentFilterRepresentation(null);
    }

    public void backToMain() {
        Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (currentPanel instanceof MainPanel) {
            return;
        }
        loadMainPanel();
        showDefaultImageView();
    }

    @Override
    public void onBackPressed() {
        Fragment currentPanel = getSupportFragmentManager().findFragmentByTag(MainPanel.FRAGMENT_TAG);
        if (currentPanel instanceof MainPanel) {
            if (!mImageShow.hasModifications()) {
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
        } else {
            backToMain();
        }
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
