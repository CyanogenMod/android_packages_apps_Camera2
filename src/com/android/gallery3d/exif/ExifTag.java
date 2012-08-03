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

package com.android.gallery3d.exif;

public class ExifTag {
    public static interface TIFF_TAG {
        public static final short TAG_IMAGE_WIDTH = 0x100;
        public static final short TAG_IMAGE_HEIGHT = 0x101;
        public static final short TAG_COMPRESSION = 0x103;
        public static final short TAG_MAKE = 0x10f;
        public static final short TAG_MODEL = 0x110;
        public static final short TAG_ORIENTATION = 0x112;
        public static final short TAG_X_RESOLUTION = 0x11A;
        public static final short TAG_Y_RESOLUTION = 0x11B;
        public static final short TAG_RESOLUTION_UNIT = 0x128;
        public static final short TAG_SOFTWARE = 0x131;
        public static final short TAG_DATE_TIME = 0x132;
        public static final short TAG_JPEG_INTERCHANGE_FORMAT = 0x201;
        public static final short TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x202;
        public static final short TAG_Y_CB_CR_POSITIONING = 0x213;
        public static final short TAG_EXIF_IFD = (short) 0x8769;

        public static final short ORIENTATION_TOP_LEFT = 1;
        public static final short ORIENTATION_TOP_RIGHT = 2;
        public static final short ORIENTATION_BOTTOM_LEFT = 3;
        public static final short ORIENTATION_BOTTOM_RIGHT = 4;
        public static final short ORIENTATION_LEFT_TOP = 5;
        public static final short ORIENTATION_RIGHT_TOP = 6;
        public static final short ORIENTATION_LEFT_BOTTOM = 7;
        public static final short ORIENTATION_RIGHT_BOTTOM = 8;

        public static final short Y_CB_CR_POSITIONING_CENTERED = 1;
        public static final short Y_CB_CR_POSITIONING_CO_SITED = 2;

        public static final short COMPRESSION_UNCOMPRESSION = 1;
        public static final short COMPRESSION_JPEG = 6;

        public static final short RESOLUTION_UNIT_INCHES = 2;
        public static final short RESOLUTION_UNIT_CENTIMETERS = 3;
    }

    public static interface EXIF_TAG {
        public static final short TAG_EXPOSURE_TIME = (short) 0x829A;
        public static final short TAG_F_NUMBER = (short) 0x829D;
        public static final short TAG_EXPOSURE_PROGRAM = (short) 0x8822;
        public static final short TAG_ISO_SPEED_RATINGS = (short) 0x8827;
        public static final short TAG_EXIF_VERSION = (short) 0x9000;
        public static final short TAG_DATE_TIME_ORIGINAL = (short) 0x9003;
        public static final short TAG_DATE_TIME_DIGITIZED = (short) 0x9004;
        public static final short TAG_SHUTTER_SPEED = (short) 0x9201;
        public static final short TAG_APERTURE_VALUE = (short) 0x9202;
        public static final short TAG_BRIGHTNESS_VALUE = (short) 0x9203;
        public static final short TAG_EXPOSURE_BIAS_VALUE = (short) 0x9204;
        public static final short TAG_MAX_APERTURE_VALUE = (short) 0x9205;
        public static final short TAG_METERING_MODE = (short) 0x9207;
        public static final short TAG_FLASH = (short) 0x9209;
        public static final short TAG_FOCAL_LENGTH = (short) 0x920A;
        public static final short TAG_USER_COMMENT = (short) 0x9286;
        public static final short TAG_COLOR_SPACE = (short) 0xA001;
        public static final short TAG_PIXEL_X_DIMENSION = (short) 0xA002;
        public static final short TAG_PIXEL_Y_DIMENSION = (short) 0xA003;
        public static final short TAG_EXPOSURE_MODE = (short) 0xA402;
        public static final short TAG_WHITH_BALANCE = (short) 0xA403;
        public static final short TAG_SCENE_CAPTURE_TYPE = (short) 0xA406;

        public static final short EXPOSURE_PROGRAM_NOT_DEFINED = 0;
        public static final short EXPOSURE_PROGRAM_MANUAL = 1;
        public static final short EXPOSURE_PROGRAM_NORMAL_PROGRAM = 2;
        public static final short EXPOSURE_PROGRAM_APERTURE_PRIORITY = 3;
        public static final short EXPOSURE_PROGRAM_SHUTTER_PRIORITY = 4;
        public static final short EXPOSURE_PROGRAM_CREATIVE_PROGRAM = 5;
        public static final short EXPOSURE_PROGRAM_ACTION_PROGRAM = 6;
        public static final short EXPOSURE_PROGRAM_PROTRAIT_MODE = 7;
        public static final short EXPOSURE_PROGRAM_LANDSCAPE_MODE = 8;

        public static final short METERING_MODE_UNKNOWN = 0;
        public static final short METERING_MODE_AVERAGE = 1;
        public static final short METERING_MODE_CENTER_WEIGHTED_AVERAGE = 2;
        public static final short METERING_MODE_SPOT = 3;
        public static final short METERING_MODE_MULTISPOT = 4;
        public static final short METERING_MODE_PATTERN = 5;
        public static final short METERING_MODE_PARTAIL = 6;
        public static final short METERING_MODE_OTHER = 255;

        // Flash flag
        // LSB
        public static final short FLASH_DID_NOT_FIRED = 0;
        public static final short FLASH_FIRED = 1;
        // 1~2 bits
        public static final short FLASH_RETURN_NO_STROBE_RETURN_DETECTION_FUNCTION = 0 << 1;
        public static final short FLASH_RETURN_STROBE_RETURN_LIGHT_NOT_DETECTED = 2 << 1;
        public static final short FLASH_RETURN_STROBE_RETURN_LIGHT_DETECTED = 3 << 1;
        // 3~4 bits
        public static final short FLASH_MODE_UNKNOWN = 0 << 3;
        public static final short FLASH_MODE_COMPULSORY_FLASH_FIRING = 1 << 3;
        public static final short FLASH_MODE_COMPULSORY_FLASH_SUPPRESSION = 2 << 3;
        public static final short FLASH_MODE_AUTO_MODE = 3 << 3;
        // 5 bit
        public static final short FLASH_FUNCTION_PRESENT = 0 << 5;
        public static final short FLASH_FUNCTION_NO_FUNCTION = 1 << 5;
        // 6 bit
        public static final short FLASH_RED_EYE_REDUCTION_NO_OR_UNKNOWN = 0 << 6;
        public static final short FLASH_RED_EYE_REDUCTION_SUPPORT = 1 << 6;

        public static final short COLOR_SPACE_SRGB = 1;
        public static final short COLOR_SPACE_UNCALIBRATED = (short) 0xFFFF;

        public static final short EXPOSURE_MODE_AUTO_EXPOSURE = 0;
        public static final short EXPOSURE_MODE_MANUAL_EXPOSURE = 1;
        public static final short EXPOSURE_MODE_AUTO_BRACKET = 2;

        public static final short WHITE_BALACE_MODE_AUTO = 0;
        public static final short WHITE_BALACE_MODE_MANUAL = 1;

        public static final short SCENE_CAPTURE_TYPE_STANDARD = 0;
        public static final short SCENE_CAPTURE_TYPE_LANDSCAPE = 1;
        public static final short SCENE_CAPTURE_TYPE_PROTRAIT = 2;
        public static final short SCENE_CAPTURE_TYPE_NIGHT_SCENE = 3;
    }

    public static final short TYPE_BYTE = 1;
    public static final short TYPE_ASCII = 2;
    public static final short TYPE_SHORT = 3;
    public static final short TYPE_INT = 4;
    public static final short TYPE_RATIONAL = 5;
    public static final short TYPE_UNDEFINED = 7;
    public static final short TYPE_SINT = 9;
    public static final short TYPE_SRATIONAL = 10;

    private static final int TYPE_TO_SIZE_MAP[] = new int[11];
    static {
        TYPE_TO_SIZE_MAP[TYPE_BYTE] = 1;
        TYPE_TO_SIZE_MAP[TYPE_ASCII] = 1;
        TYPE_TO_SIZE_MAP[TYPE_SHORT] = 2;
        TYPE_TO_SIZE_MAP[TYPE_INT] = 4;
        TYPE_TO_SIZE_MAP[TYPE_RATIONAL] = 8;
        TYPE_TO_SIZE_MAP[TYPE_UNDEFINED] = 1;
        TYPE_TO_SIZE_MAP[TYPE_SINT] = 4;
        TYPE_TO_SIZE_MAP[TYPE_SRATIONAL] = 8;
    }

    public static int getElementSize(short type) {
        return TYPE_TO_SIZE_MAP[type];
    }

    private final short mTagId;
    private final short mDataType;
    private final int mDataCount;

    ExifTag(short tagId, short type, int dataCount) {
        mTagId = tagId;
        mDataType = type;
        mDataCount = dataCount;
    }

    public short getTagId() {
        return mTagId;
    }

    public short getDataType() {
        return mDataType;
    }

    public int getDataSize() {
        return getComponentCount() * getElementSize(getDataType());
    }

    public int getComponentCount() {
        return mDataCount;
    }
}