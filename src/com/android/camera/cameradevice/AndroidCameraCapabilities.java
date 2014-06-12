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

package com.android.camera.cameradevice;

import android.hardware.Camera;

import com.android.camera.debug.Log;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The subclass of {@link CameraCapabilities} for Android Camera 1 API.
 */
class AndroidCameraCapabilities extends CameraCapabilities {

    private static Log.Tag TAG = new Log.Tag("AndroidCameraCapabilities");

    private FpsComparator mFpsComparator = new FpsComparator();
    private SizeComparator mSizeComparator = new SizeComparator();

    AndroidCameraCapabilities(Camera.Parameters p) {
        super(new AndroidCameraCapabilityStringifier());
        mMaxExposureCompensation = p.getMaxExposureCompensation();
        mMinExposureCompensation = p.getMinExposureCompensation();
        mExposureCompensationStep = p.getExposureCompensationStep();
        mMaxNumOfFacesSupported = p.getMaxNumDetectedFaces();
        mMaxNumOfMeteringArea = p.getMaxNumMeteringAreas();
        mPreferredPreviewSizeForVideo = new Size(p.getPreferredPreviewSizeForVideo());
        mSupportedPreviewFormats.addAll(p.getSupportedPreviewFormats());
        mSupportedPhotoFormats.addAll(p.getSupportedPictureFormats());
        mMaxZoomIndex = p.getMaxZoom();
        mZoomRatioList.addAll(p.getZoomRatios());
        mMaxZoomRatio = mZoomRatioList.get(mMaxZoomIndex);
        buildPreviewFpsRange(p);
        buildPreviewSizes(p);
        buildVideoSizes(p);
        buildPictureSizes(p);
        buildSceneModes(p);
        buildFlashModes(p);
        buildFocusModes(p);
        buildWhiteBalances(p);

        if (p.isZoomSupported()) {
            mSupportedFeatures.add(Feature.ZOOM);
        }
        if (p.isVideoSnapshotSupported()) {
            mSupportedFeatures.add(Feature.VIDEO_SNAPSHOT);
        }
        if (p.isAutoExposureLockSupported()) {
            mSupportedFeatures.add(Feature.AUTO_EXPOSURE_LOCK);
        }
        if (p.isAutoWhiteBalanceLockSupported()) {
            mSupportedFeatures.add(Feature.AUTO_WHITE_BALANCE_LOCK);
        }
        if (supports(FocusMode.AUTO)) {
            mMaxNumOfFocusAreas = p.getMaxNumFocusAreas();
            if (mMaxNumOfFocusAreas > 0) {
                mSupportedFeatures.add(Feature.FOCUS_AREA);
            }
        }
        if (mMaxNumOfMeteringArea > 0) {
            mSupportedFeatures.add(Feature.METERING_AREA);
        }
    }

    AndroidCameraCapabilities(AndroidCameraCapabilities src) {
        super(src);
    }

    private void buildPreviewFpsRange(Camera.Parameters p) {
        List<int[]> supportedPreviewFpsRange = p.getSupportedPreviewFpsRange();
        if (supportedPreviewFpsRange != null) {
            mSupportedPreviewFpsRange.addAll(supportedPreviewFpsRange);
        }
        Collections.sort(mSupportedPreviewFpsRange, mFpsComparator);
    }

    private void buildPreviewSizes(Camera.Parameters p) {
        List<Camera.Size> supportedPreviewSizes = p.getSupportedPreviewSizes();
        if (supportedPreviewSizes != null) {
            for (Camera.Size s : supportedPreviewSizes) {
                mSupportedPreviewSizes.add(new Size(s.width, s.height));
            }
        }
        Collections.sort(mSupportedPreviewSizes, mSizeComparator);
    }

    private void buildVideoSizes(Camera.Parameters p) {
        List<Camera.Size> supportedVideoSizes = p.getSupportedVideoSizes();
        if (supportedVideoSizes != null) {
            for (Camera.Size s : supportedVideoSizes) {
                mSupportedVideoSizes.add(new Size(s.width, s.height));
            }
        }
        Collections.sort(mSupportedVideoSizes, mSizeComparator);
    }

    private void buildPictureSizes(Camera.Parameters p) {
        List<Camera.Size> supportedPictureSizes = p.getSupportedPictureSizes();
        if (supportedPictureSizes != null) {
            for (Camera.Size s : supportedPictureSizes) {
                mSupportedPhotoSizes.add(new Size(s.width, s.height));
            }
        }
        Collections.sort(mSupportedPhotoSizes, mSizeComparator);

    }

    private void buildSceneModes(Camera.Parameters p) {
        List<String> supportedSceneModes = p.getSupportedSceneModes();
        if (supportedSceneModes != null) {
            for (String scene : supportedSceneModes) {
                if (Camera.Parameters.SCENE_MODE_AUTO.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.AUTO);
                } else if (Camera.Parameters.SCENE_MODE_ACTION.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.ACTION);
                } else if (Camera.Parameters.SCENE_MODE_BARCODE.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.BARCODE);
                } else if (Camera.Parameters.SCENE_MODE_BEACH.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.BEACH);
                } else if (Camera.Parameters.SCENE_MODE_CANDLELIGHT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.CANDLELIGHT);
                } else if (Camera.Parameters.SCENE_MODE_FIREWORKS.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.FIREWORKS);
                } else if (Camera.Parameters.SCENE_MODE_HDR.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.HDR);
                } else if (Camera.Parameters.SCENE_MODE_LANDSCAPE.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.LANDSCAPE);
                } else if (Camera.Parameters.SCENE_MODE_NIGHT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.NIGHT);
                } else if (Camera.Parameters.SCENE_MODE_NIGHT_PORTRAIT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.NIGHT_PORTRAIT);
                } else if (Camera.Parameters.SCENE_MODE_PARTY.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.PARTY);
                } else if (Camera.Parameters.SCENE_MODE_PORTRAIT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.PORTRAIT);
                } else if (Camera.Parameters.SCENE_MODE_SNOW.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.SNOW);
                } else if (Camera.Parameters.SCENE_MODE_SPORTS.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.SPORTS);
                } else if (Camera.Parameters.SCENE_MODE_STEADYPHOTO.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.STEADYPHOTO);
                } else if (Camera.Parameters.SCENE_MODE_SUNSET.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.SUNSET);
                } else if (Camera.Parameters.SCENE_MODE_THEATRE.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.THEATRE);
                }
            }
        }
    }

    private void buildFlashModes(Camera.Parameters p) {
        List<String> supportedFlashModes = p.getSupportedFlashModes();
        if (supportedFlashModes == null) {
            // Camera 1 will return NULL if no flash mode is supported.
            mSupportedFlashModes.add(FlashMode.NO_FLASH);
        } else {
            for (String flash : supportedFlashModes) {
                if (Camera.Parameters.FLASH_MODE_AUTO.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.AUTO);
                } else if (Camera.Parameters.FLASH_MODE_OFF.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.OFF);
                } else if (Camera.Parameters.FLASH_MODE_ON.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.ON);
                } else if (Camera.Parameters.FLASH_MODE_RED_EYE.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.RED_EYE);
                }
            }
        }
    }

    private void buildFocusModes(Camera.Parameters p) {
        List<String> supportedFocusModes = p.getSupportedFocusModes();
        if (supportedFocusModes != null) {
            for (String focus : supportedFocusModes) {
                if (Camera.Parameters.FOCUS_MODE_AUTO.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.AUTO);
                } else if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.CONTINUOUS_PICTURE);
                } else if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.CONTINUOUS_VIDEO);
                } else if (Camera.Parameters.FOCUS_MODE_EDOF.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.EXTENDED_DOF);
                } else if (Camera.Parameters.FOCUS_MODE_FIXED.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.FIXED);
                } else if (Camera.Parameters.FOCUS_MODE_INFINITY.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.INFINITY);
                } else if (Camera.Parameters.FOCUS_MODE_MACRO.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.MACRO);
                }
            }
        }
    }

    private void buildWhiteBalances(Camera.Parameters p) {
        List<String> supportedWhiteBalances = p.getSupportedFocusModes();
        if (supportedWhiteBalances != null) {
            for (String wb : supportedWhiteBalances) {
                if (Camera.Parameters.WHITE_BALANCE_AUTO.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.AUTO);
                } else if (Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.CLOUDY_DAYLIGHT);
                } else if (Camera.Parameters.WHITE_BALANCE_DAYLIGHT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.DAYLIGHT);
                } else if (Camera.Parameters.WHITE_BALANCE_FLUORESCENT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.FLUORESCENT);
                } else if (Camera.Parameters.WHITE_BALANCE_INCANDESCENT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.INCANDESCENT);
                } else if (Camera.Parameters.WHITE_BALANCE_SHADE.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.SHADE);
                } else if (Camera.Parameters.WHITE_BALANCE_TWILIGHT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.TWILIGHT);
                } else if (Camera.Parameters.WHITE_BALANCE_WARM_FLUORESCENT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.WARM_FLUORESCENT);
                }
            }
        }
    }

    private static class FpsComparator implements Comparator<int[]> {
        @Override
        public int compare(int[] fps1, int[] fps2) {
            return (fps1[0] == fps2[0] ? fps1[1] - fps2[1] : fps1[0] - fps2[0]);
        }
    }

    private static class SizeComparator implements Comparator<Size> {

        @Override
        public int compare(Size size1, Size size2) {
            return (size1.width() == size2.width() ? size1.height() - size2.height() :
                    size1.width() - size2.width());
        }
    }

    private static class AndroidCameraCapabilityStringifier implements Stringifier {

        @Override
        public String stringify(FocusMode focus) {
            if (focus == null) {
                return null;
            }

            switch (focus) {
                case AUTO:
                    return Camera.Parameters.FOCUS_MODE_AUTO;
                case CONTINUOUS_PICTURE:
                    return Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
                case CONTINUOUS_VIDEO:
                    return Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                case EXTENDED_DOF:
                    return Camera.Parameters.FOCUS_MODE_EDOF;
                case FIXED:
                    return Camera.Parameters.FOCUS_MODE_FIXED;
                case INFINITY:
                    return Camera.Parameters.FOCUS_MODE_INFINITY;
                case MACRO:
                    return Camera.Parameters.FOCUS_MODE_MACRO;
            }
            return null;
        }

        @Override
        public FocusMode focusModeFromString(String val) {
            if (val == null) {
                return null;
            }

            if (Camera.Parameters.FOCUS_MODE_AUTO.equals(val)) {
                return FocusMode.AUTO;
            } else if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(val)) {
                return FocusMode.CONTINUOUS_PICTURE;
            } else if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(val)) {
                return FocusMode.CONTINUOUS_VIDEO;
            } else if (Camera.Parameters.FOCUS_MODE_EDOF.equals(val)) {
                return FocusMode.EXTENDED_DOF;
            } else if (Camera.Parameters.FOCUS_MODE_FIXED.equals(val)) {
                return FocusMode.FIXED;
            } else if (Camera.Parameters.FOCUS_MODE_INFINITY.equals(val)) {
                return FocusMode.INFINITY;
            } else if (Camera.Parameters.FOCUS_MODE_MACRO.equals(val)) {
                return FocusMode.MACRO;
            } else {
                return null;
            }
        }

        @Override
        public String stringify(FlashMode flash) {
            if (flash == null) {
                return null;
            }

            switch (flash) {
                case NO_FLASH:
                    return null;
                case AUTO:
                    return Camera.Parameters.FLASH_MODE_AUTO;
                case OFF:
                    return Camera.Parameters.FLASH_MODE_OFF;
                case ON:
                    return Camera.Parameters.FLASH_MODE_ON;
                case TORCH:
                    return Camera.Parameters.FLASH_MODE_TORCH;
                case RED_EYE:
                    return Camera.Parameters.FLASH_MODE_RED_EYE;
            }
            return null;
        }

        @Override
        public FlashMode flashModeFromString(String val) {
            if (val == null) {
                return FlashMode.NO_FLASH;
            } else if (Camera.Parameters.FLASH_MODE_AUTO.equals(val)) {
                return FlashMode.AUTO;
            } else if (Camera.Parameters.FLASH_MODE_OFF.equals(val)) {
                return FlashMode.OFF;
            } else if (Camera.Parameters.FLASH_MODE_ON.equals(val)) {
                return FlashMode.ON;
            } else if (Camera.Parameters.FLASH_MODE_TORCH.equals(val)) {
                return FlashMode.TORCH;
            } else if (Camera.Parameters.FLASH_MODE_RED_EYE.equals(val)) {
                return FlashMode.RED_EYE;
            } else {
                return null;
            }
        }

        @Override
        public String stringify(SceneMode scene) {
            if (scene == null) {
                return null;
            }

            switch (scene) {
                case AUTO:
                    return Camera.Parameters.SCENE_MODE_AUTO;
                case ACTION:
                    return Camera.Parameters.SCENE_MODE_ACTION;
                case BARCODE:
                    return Camera.Parameters.SCENE_MODE_BARCODE;
                case BEACH:
                    return Camera.Parameters.SCENE_MODE_BEACH;
                case CANDLELIGHT:
                    return Camera.Parameters.SCENE_MODE_CANDLELIGHT;
                case FIREWORKS:
                    return Camera.Parameters.SCENE_MODE_FIREWORKS;
                case HDR:
                    return Camera.Parameters.SCENE_MODE_HDR;
                case LANDSCAPE:
                    return Camera.Parameters.SCENE_MODE_LANDSCAPE;
                case NIGHT:
                    return Camera.Parameters.SCENE_MODE_NIGHT;
                case NIGHT_PORTRAIT:
                    return Camera.Parameters.SCENE_MODE_NIGHT_PORTRAIT;
                case PARTY:
                    return Camera.Parameters.SCENE_MODE_PARTY;
                case PORTRAIT:
                    return Camera.Parameters.SCENE_MODE_PORTRAIT;
                case SNOW:
                    return Camera.Parameters.SCENE_MODE_SNOW;
                case SPORTS:
                    return Camera.Parameters.SCENE_MODE_SPORTS;
                case STEADYPHOTO:
                    return Camera.Parameters.SCENE_MODE_STEADYPHOTO;
                case SUNSET:
                    return Camera.Parameters.SCENE_MODE_SUNSET;
                case THEATRE:
                    return Camera.Parameters.SCENE_MODE_THEATRE;
            }
            return null;
        }

        @Override
        public SceneMode sceneModeFromString(String val) {
            if (val == null) {
                return SceneMode.NO_SCENE_MODE;
            } else if (Camera.Parameters.SCENE_MODE_AUTO.equals(val)) {
                return SceneMode.AUTO;
            } else if (Camera.Parameters.SCENE_MODE_ACTION.equals(val)) {
                return SceneMode.ACTION;
            } else if (Camera.Parameters.SCENE_MODE_BARCODE.equals(val)) {
                return SceneMode.BARCODE;
            } else if (Camera.Parameters.SCENE_MODE_BEACH.equals(val)) {
                return SceneMode.BEACH;
            } else if (Camera.Parameters.SCENE_MODE_CANDLELIGHT.equals(val)) {
                return SceneMode.CANDLELIGHT;
            } else if (Camera.Parameters.SCENE_MODE_FIREWORKS.equals(val)) {
                return SceneMode.FIREWORKS;
            } else if (Camera.Parameters.SCENE_MODE_HDR.equals(val)) {
                return SceneMode.HDR;
            } else if (Camera.Parameters.SCENE_MODE_LANDSCAPE.equals(val)) {
                return SceneMode.LANDSCAPE;
            } else if (Camera.Parameters.SCENE_MODE_NIGHT.equals(val)) {
                return SceneMode.NIGHT;
            } else if (Camera.Parameters.SCENE_MODE_NIGHT_PORTRAIT.equals(val)) {
                return SceneMode.NIGHT_PORTRAIT;
            } else if (Camera.Parameters.SCENE_MODE_PARTY.equals(val)) {
                return SceneMode.PARTY;
            } else if (Camera.Parameters.SCENE_MODE_PORTRAIT.equals(val)) {
                return SceneMode.PORTRAIT;
            } else if (Camera.Parameters.SCENE_MODE_SNOW.equals(val)) {
                return SceneMode.SNOW;
            } else if (Camera.Parameters.SCENE_MODE_SPORTS.equals(val)) {
                return SceneMode.SPORTS;
            } else if (Camera.Parameters.SCENE_MODE_STEADYPHOTO.equals(val)) {
                return SceneMode.STEADYPHOTO;
            } else if (Camera.Parameters.SCENE_MODE_SUNSET.equals(val)) {
                return SceneMode.SUNSET;
            } else if (Camera.Parameters.SCENE_MODE_THEATRE.equals(val)) {
                return SceneMode.THEATRE;
            } else {
                return null;
            }
        }

        @Override
        public String stringify(WhiteBalance wb) {
            if (wb == null) {
                return null;
            }

            switch (wb) {
                case AUTO:
                    return Camera.Parameters.WHITE_BALANCE_AUTO;
                case CLOUDY_DAYLIGHT:
                    return Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT;
                case DAYLIGHT:
                    return Camera.Parameters.WHITE_BALANCE_DAYLIGHT;
                case FLUORESCENT:
                    return Camera.Parameters.WHITE_BALANCE_FLUORESCENT;
                case INCANDESCENT:
                    return Camera.Parameters.WHITE_BALANCE_INCANDESCENT;
                case SHADE:
                    return Camera.Parameters.WHITE_BALANCE_SHADE;
                case TWILIGHT:
                    return Camera.Parameters.WHITE_BALANCE_TWILIGHT;
                case WARM_FLUORESCENT:
                    return Camera.Parameters.WHITE_BALANCE_WARM_FLUORESCENT;
            }
            return null;
        }

        @Override
        public WhiteBalance whiteBalanceFromString(String val) {
            if (val == null) {
                return null;
            }

            if (Camera.Parameters.WHITE_BALANCE_AUTO.equals(val)) {
                return WhiteBalance.AUTO;
            } else if (Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT.equals(val)) {
                return WhiteBalance.CLOUDY_DAYLIGHT;
            } else if (Camera.Parameters.WHITE_BALANCE_DAYLIGHT.equals(val)) {
                return WhiteBalance.DAYLIGHT;
            } else if (Camera.Parameters.WHITE_BALANCE_FLUORESCENT.equals(val)) {
                return WhiteBalance.FLUORESCENT;
            } else if (Camera.Parameters.WHITE_BALANCE_INCANDESCENT.equals(val)) {
                return WhiteBalance.INCANDESCENT;
            } else if (Camera.Parameters.WHITE_BALANCE_SHADE.equals(val)) {
                return WhiteBalance.SHADE;
            } else if (Camera.Parameters.WHITE_BALANCE_TWILIGHT.equals(val)) {
                return WhiteBalance.TWILIGHT;
            } else if (Camera.Parameters.WHITE_BALANCE_WARM_FLUORESCENT.equals(val)) {
                return WhiteBalance.WARM_FLUORESCENT;
            } else {
                return null;
            }
        }
    }
}
