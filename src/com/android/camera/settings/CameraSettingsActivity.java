/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.settings;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.support.v4.app.FragmentActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.camera.settings.SettingsUtil.SelectedPictureSizes;
import com.android.camera.settings.SettingsUtil.SelectedVideoQualities;
import com.android.camera.util.FeedbackHelper;
import com.android.camera.util.SettingsHelper;
import com.android.camera2.R;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Provides the settings UI for the Camera app.
 */
public class CameraSettingsActivity extends FragmentActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.mode_settings);

        CameraSettingsFragment dialog = new CameraSettingsFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content, dialog).commit();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        return true;
    }

    public static class CameraSettingsFragment extends PreferenceFragment implements
            OnSharedPreferenceChangeListener {
        private static final String BUILD_VERSION = "build_version";
        private static final String PREF_CATEGORY_RESOLUTION = "pref_category_resolution";
        private static DecimalFormat sMegaPixelFormat = new DecimalFormat("##0.0");
        private FeedbackHelper mFeedbackHelper;
        private String[] mCamcorderProfileNames;

        // Selected resolutions for the different cameras and sizes.
        private SelectedPictureSizes mPictureSizesBack;
        private SelectedPictureSizes mPictureSizesFront;
        private SelectedVideoQualities mVideoQualitiesBack;
        private SelectedVideoQualities mVideoQualitiesFront;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mFeedbackHelper = new FeedbackHelper(getActivity());
            addPreferencesFromResource(R.xml.camera_preferences);
            mCamcorderProfileNames = getResources().getStringArray(R.array.camcorder_profile_names);
        }

        @Override
        public void onResume() {
            super.onResume();
            // Only show open source licenses in GoogleCamera build.
            if (!SettingsHelper.isOpenSourceLicensesShown()) {
                Preference pref = findPreference("pref_open_source_licenses");
                getPreferenceScreen().removePreference(pref);
            }

            // Load the camera sizes.
            loadSizes();

            // Make sure to hide settings for cameras that don't exist on this
            // device.
            setVisibilities();

            // Put in the summaries for the currently set values.
            final PreferenceScreen resolutionScreen =
                    (PreferenceScreen) findPreference(PREF_CATEGORY_RESOLUTION);
            fillEntriesAndSummaries(resolutionScreen);

            // Make sure the sub-screen has home-as-up configured.
            resolutionScreen.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    setUpHomeButton(resolutionScreen);
                    return false;
                }
            });

            // Set build number.
            try {
                final PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(
                        getActivity().getPackageName(), 0);
                findPreference(BUILD_VERSION).setSummary(packageInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                findPreference(BUILD_VERSION).setSummary("?");
            }
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);

            // Set-Up Feedback entry to launch the feedback flow on click.
            findPreference("pref_send_feedback").setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {

                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            mFeedbackHelper.startFeedback();
                            return true;
                        }
                    });
        }

        private void setUpHomeButton(PreferenceScreen preferenceScreen) {
            final Dialog dialog = preferenceScreen.getDialog();
            dialog.getActionBar().setDisplayHomeAsUpEnabled(true);

            View homeButton = dialog.findViewById(android.R.id.home);
            if (homeButton != null) {
                homeButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
            }
        }

        /**
         * Depending on camera availability on the device, this removes settings
         * for cameras the device doesn't have.
         */
        private void setVisibilities() {
            PreferenceGroup resolutions =
                    (PreferenceGroup) findPreference(PREF_CATEGORY_RESOLUTION);
            if (mPictureSizesBack == null) {
                resolutions.removePreference(
                        findPreference(SettingsManager.KEY_PICTURE_SIZE_BACK));
                resolutions.removePreference(
                        findPreference(SettingsManager.KEY_VIDEO_QUALITY_BACK));
            }
            if (mPictureSizesFront == null) {
                resolutions.removePreference(
                        findPreference(SettingsManager.KEY_PICTURE_SIZE_FRONT));
                resolutions.removePreference(
                        findPreference(SettingsManager.KEY_VIDEO_QUALITY_FRONT));
            }
        }

        /**
         * Recursively go through settings and fill entries and summaries of our
         * preferences.
         */
        private void fillEntriesAndSummaries(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); ++i) {
                Preference pref = group.getPreference(i);
                if (pref instanceof PreferenceGroup) {
                    fillEntriesAndSummaries((PreferenceGroup) pref);
                }
                setSummary(pref);
                setEntries(pref);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            mFeedbackHelper.stopFeedback();
            super.onStop();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            setSummary(findPreference(key));
        }

        /**
         * Set the entries for the given preference. The given preference needs
         * to be a {@link ListPreference}
         */
        private void setEntries(Preference preference) {
            if (!(preference instanceof ListPreference)) {
                return;
            }

            ListPreference listPreference = (ListPreference) preference;
            if (listPreference.getKey().equals(SettingsManager.KEY_PICTURE_SIZE_BACK)) {
                setEntriesForSelection(mPictureSizesBack, listPreference);
            } else if (listPreference.getKey().equals(SettingsManager.KEY_PICTURE_SIZE_FRONT)) {
                setEntriesForSelection(mPictureSizesFront, listPreference);
            } else if (listPreference.getKey().equals(SettingsManager.KEY_VIDEO_QUALITY_BACK)) {
                setEntriesForSelection(mVideoQualitiesBack, listPreference);
            } else if (listPreference.getKey().equals(SettingsManager.KEY_VIDEO_QUALITY_FRONT)) {
                setEntriesForSelection(mVideoQualitiesFront, listPreference);
            }
        }

        /**
         * Set the summary for the given preference. The given preference needs
         * to be a {@link ListPreference}.
         */
        private void setSummary(Preference preference) {
            if (!(preference instanceof ListPreference)) {
                return;
            }

            ListPreference listPreference = (ListPreference) preference;
            if (listPreference.getKey().equals(SettingsManager.KEY_PICTURE_SIZE_BACK)) {
                setSummaryForSelection(mPictureSizesBack, listPreference);
            } else if (listPreference.getKey().equals(SettingsManager.KEY_PICTURE_SIZE_FRONT)) {
                setSummaryForSelection(mPictureSizesFront, listPreference);
            } else if (listPreference.getKey().equals(SettingsManager.KEY_VIDEO_QUALITY_BACK)) {
                setSummaryForSelection(mVideoQualitiesBack, listPreference);
            } else if (listPreference.getKey().equals(SettingsManager.KEY_VIDEO_QUALITY_FRONT)) {
                setSummaryForSelection(mVideoQualitiesFront, listPreference);
            }
        }

        /**
         * Sets the entries for the given list preference.
         *
         * @param selectedQualities The possible S,M,L entries the user can
         *            choose from.
         * @param preference The preference to set the entries for.
         */
        private void setEntriesForSelection(SelectedPictureSizes selectedSizes,
                ListPreference preference) {
            String[] entries = new String[3];
            entries[0] = getSizeSummaryString(selectedSizes.large);
            entries[1] = getSizeSummaryString(selectedSizes.medium);
            entries[2] = getSizeSummaryString(selectedSizes.small);
            preference.setEntries(entries);
        }

        /**
         * Sets the entries for the given list preference.
         *
         * @param selectedQualities The possible S,M,L entries the user can
         *            choose from.
         * @param preference The preference to set the entries for.
         */
        private void setEntriesForSelection(SelectedVideoQualities selectedQualities,
                ListPreference preference) {
            String[] entries = new String[3];
            entries[0] = mCamcorderProfileNames[selectedQualities.large];
            entries[1] = mCamcorderProfileNames[selectedQualities.medium];
            entries[2] = mCamcorderProfileNames[selectedQualities.small];
            preference.setEntries(entries);
        }

        /**
         * Sets the summary for the given list preference.
         *
         * @param selectedQualities The selected picture sizes.
         * @param preference The preference for which to set the summary.
         */
        private void setSummaryForSelection(SelectedPictureSizes selectedSizes,
                ListPreference preference) {
            Size selectedSize = selectedSizes.getFromSetting(preference.getValue());
            preference.setSummary(getSizeSummaryString(selectedSize));
        }

        /**
         * Sets the summary for the given list preference.
         *
         * @param selectedQualities The selected video qualities.
         * @param preference The preference for which to set the summary.
         */
        private void setSummaryForSelection(SelectedVideoQualities selectedQualities,
                ListPreference preference) {
            int selectedQuality = selectedQualities.getFromSetting(preference.getValue());
            preference.setSummary(mCamcorderProfileNames[selectedQuality]);
        }

        /**
         * This method gets the selected picture sizes for S,M,L and populates
         * {@link #mPictureSizesBack}, {@link #mPictureSizesFront},
         * {@link #mVideoQualitiesBack} and {@link #mVideoQualitiesFront}
         * accordingly.
         */
        private void loadSizes() {
            // Back camera.
            int backCameraId = getCameraId(CameraInfo.CAMERA_FACING_BACK);
            if (backCameraId >= 0) {
                // Check whether we cached the sizes:
                mPictureSizesBack = SettingsUtil.getSelectedCameraPictureSizes(null, backCameraId);
                if (mPictureSizesBack == null) {
                    Camera backCamera = Camera.open(backCameraId);
                    if (backCamera != null) {
                        List<Size> sizes = backCamera.getParameters().getSupportedPictureSizes();
                        backCamera.release();
                        mPictureSizesBack = SettingsUtil.getSelectedCameraPictureSizes(sizes,
                                backCameraId);
                    }
                }
                mVideoQualitiesBack = SettingsUtil.getSelectedVideoQualities(backCameraId);
            } else {
                mPictureSizesBack = null;
                mVideoQualitiesBack = null;
            }

            // Front camera.
            int frontCameraId = getCameraId(CameraInfo.CAMERA_FACING_FRONT);
            if (frontCameraId >= 0) {
                mPictureSizesFront = SettingsUtil.getSelectedCameraPictureSizes(null,
                        frontCameraId);
                if (mPictureSizesFront == null) {
                    Camera frontCamera = Camera.open(frontCameraId);
                    if (frontCamera != null) {
                        List<Size> sizes = frontCamera.getParameters().getSupportedPictureSizes();
                        frontCamera.release();
                        mPictureSizesFront = SettingsUtil.getSelectedCameraPictureSizes(sizes,
                                frontCameraId);
                    }
                }
                mVideoQualitiesFront = SettingsUtil.getSelectedVideoQualities(frontCameraId);
            } else {
                mPictureSizesFront = null;
                mVideoQualitiesFront = null;
            }
        }

        /**
         * Gets the first camera facing the given direction.
         *
         * @param facing Either {@link CameraInfo#CAMERA_FACING_BACK} or
         *            {@link CameraInfo#CAMERA_FACING_FRONT}.
         * @return The ID of the first camera matching the given direction, or
         *         -1, if no camera with the given facing was found.
         */
        private static int getCameraId(int facing) {
            int numCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numCameras; ++i) {
                CameraInfo info = new CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == facing) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * @param size The photo resolution.
         * @return A human readable and translated string for labeling the
         *         picture size in megapixels.
         */
        private String getSizeSummaryString(Size size) {
            String megaPixels = sMegaPixelFormat.format((size.width * size.height) / 1e6);
            return getResources().getString(R.string.setting_summary_x_megapixels, megaPixels);
        }
    }
}
