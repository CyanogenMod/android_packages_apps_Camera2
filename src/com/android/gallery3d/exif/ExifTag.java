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

import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * This class stores information of an EXIF tag.
 * @see ExifParser
 * @see ExifReader
 * @see IfdData
 * @see ExifData
 */
public class ExifTag {
    // Tiff Tags
    public static final short TAG_IMAGE_WIDTH = 0x100;
    /*
     * The height of the image.
     */
    public static final short TAG_IMAGE_LENGTH = 0x101;
    public static final short TAG_BITS_PER_SAMPLE = 0x102;
    public static final short TAG_COMPRESSION = 0x103;
    public static final short TAG_PHOTOMETRIC_INTERPRETATION = 0x106;
    public static final short TAG_IMAGE_DESCRIPTION = 0x10E;
    public static final short TAG_MAKE = 0x10F;
    public static final short TAG_MODEL = 0x110;
    public static final short TAG_STRIP_OFFSETS = 0x111;
    public static final short TAG_ORIENTATION = 0x112;
    public static final short TAG_SAMPLES_PER_PIXEL = 0x115;
    public static final short TAG_ROWS_PER_STRIP = 0x116;
    public static final short TAG_STRIP_BYTE_COUNTS = 0x117;
    public static final short TAG_X_RESOLUTION = 0x11A;
    public static final short TAG_Y_RESOLUTION = 0x11B;
    public static final short TAG_PLANAR_CONFIGURATION = 0x11C;
    public static final short TAG_RESOLUTION_UNIT = 0x128;
    public static final short TAG_TRANSFER_FUNCTION = 0x12D;
    public static final short TAG_SOFTWARE = 0x131;
    public static final short TAG_DATE_TIME = 0x132;
    public static final short TAG_ARTIST = 0x13B;
    public static final short TAG_WHITE_POINT = 0x13E;
    public static final short TAG_PRIMARY_CHROMATICITIES = 0x13F;
    public static final short TAG_JPEG_INTERCHANGE_FORMAT = 0x201;
    public static final short TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x202;
    public static final short TAG_Y_CB_CR_COEFFICIENTS = 0x211;
    public static final short TAG_Y_CB_CR_SUB_SAMPLING = 0x212;
    public static final short TAG_Y_CB_CR_POSITIONING = 0x213;
    public static final short TAG_REFERENCE_BLACK_WHITE = 0x214;
    public static final short TAG_COPYRIGHT = (short) 0x8298;
    public static final short TAG_EXIF_IFD = (short) 0x8769;
    public static final short TAG_GPS_IFD = (short) 0x8825;

    // Exif Tags
    public static final short TAG_EXPOSURE_TIME = (short) 0x829A;
    public static final short TAG_F_NUMBER = (short) 0x829D;
    public static final short TAG_EXPOSURE_PROGRAM = (short) 0x8822;
    public static final short TAG_SPECTRAL_SENSITIVITY = (short) 0x8824;
    public static final short TAG_ISO_SPEED_RATINGS = (short) 0x8827;
    public static final short TAG_OECF = (short) 0x8828;
    public static final short TAG_EXIF_VERSION = (short) 0x9000;
    public static final short TAG_DATE_TIME_ORIGINAL = (short) 0x9003;
    public static final short TAG_DATE_TIME_DIGITIZED = (short) 0x9004;
    public static final short TAG_COMPONENTS_CONFIGURATION = (short) 0x9101;
    public static final short TAG_COMPRESSED_BITS_PER_PIXEL = (short) 0x9102;
    public static final short TAG_SHUTTER_SPEED_VALUE = (short) 0x9201;
    public static final short TAG_APERTURE_VALUE = (short) 0x9202;
    public static final short TAG_BRIGHTNESS_VALUE = (short) 0x9203;
    public static final short TAG_EXPOSURE_BIAS_VALUE = (short) 0x9204;
    public static final short TAG_MAX_APERTURE_VALUE = (short) 0x9205;
    public static final short TAG_SUBJECT_DISTANCE = (short) 0x9206;
    public static final short TAG_METERING_MODE = (short) 0x9207;
    public static final short TAG_LIGHT_SOURCE = (short) 0x9208;
    public static final short TAG_FLASH = (short) 0x9209;
    public static final short TAG_FOCAL_LENGTH = (short) 0x920A;
    public static final short TAG_SUBJECT_AREA = (short) 0x9214;
    public static final short TAG_MAKER_NOTE = (short) 0x927C;
    public static final short TAG_USER_COMMENT = (short) 0x9286;
    public static final short TAG_SUB_SEC_TIME = (short) 0x9290;
    public static final short TAG_SUB_SEC_TIME_ORIGINAL = (short) 0x9291;
    public static final short TAG_SUB_SEC_TIME_DIGITIZED = (short) 0x9292;
    public static final short TAG_FLASHPIX_VERSION = (short) 0xA000;
    public static final short TAG_COLOR_SPACE = (short) 0xA001;
    public static final short TAG_PIXEL_X_DIMENSION = (short) 0xA002;
    public static final short TAG_PIXEL_Y_DIMENSION = (short) 0xA003;
    public static final short TAG_RELATED_SOUND_FILE = (short) 0xA004;
    public static final short TAG_INTEROPERABILITY_IFD = (short) 0xA005;
    public static final short TAG_FLASH_ENERGY = (short) 0xA20B;
    public static final short TAG_SPATIAL_FREQUENCY_RESPONSE = (short) 0xA20C;
    public static final short TAG_FOCAL_PLANE_X_RESOLUTION = (short) 0xA20E;
    public static final short TAG_FOCAL_PLANE_Y_RESOLUTION = (short) 0xA20F;
    public static final short TAG_FOCAL_PLANE_RESOLUTION_UNIT = (short) 0xA210;
    public static final short TAG_SUBJECT_LOCATION = (short) 0xA214;
    public static final short TAG_EXPOSURE_INDEX = (short) 0xA215;
    public static final short TAG_SENSING_METHOD = (short) 0xA217;
    public static final short TAG_FILE_SOURCE = (short) 0xA300;
    public static final short TAG_SCENE_TYPE = (short) 0xA301;
    public static final short TAG_CFA_PATTERN = (short) 0xA302;
    public static final short TAG_CUSTOM_RENDERED = (short) 0xA401;
    public static final short TAG_EXPOSURE_MODE = (short) 0xA402;
    public static final short TAG_WHITE_BALANCE = (short) 0xA403;
    public static final short TAG_DIGITAL_ZOOM_RATIO = (short) 0xA404;
    public static final short TAG_FOCAL_LENGTH_IN_35_MM_FILE = (short) 0xA405;
    public static final short TAG_SCENE_CAPTURE_TYPE = (short) 0xA406;
    public static final short TAG_GAIN_CONTROL = (short) 0xA407;
    public static final short TAG_CONTRAST = (short) 0xA408;
    public static final short TAG_SATURATION = (short) 0xA409;
    public static final short TAG_SHARPNESS = (short) 0xA40A;
    public static final short TAG_DEVICE_SETTING_DESCRIPTION = (short) 0xA40B;
    public static final short TAG_SUBJECT_DISTANCE_RANGE = (short) 0xA40C;
    public static final short TAG_IMAGE_UNIQUE_ID = (short) 0xA420;

    // GPS tags
    public static final short TAG_GPS_VERSION_ID = 0;
    public static final short TAG_GPS_LATITUDE_REF = 1;
    public static final short TAG_GPS_LATITUDE = 2;
    public static final short TAG_GPS_LONGITUDE_REF = 3;
    public static final short TAG_GPS_LONGITUDE = 4;
    public static final short TAG_GPS_ALTITUDE_REF = 5;
    public static final short TAG_GPS_ALTITUDE = 6;
    public static final short TAG_GPS_TIME_STAMP = 7;
    public static final short TAG_GPS_SATTELLITES = 8;
    public static final short TAG_GPS_STATUS = 9;
    public static final short TAG_GPS_MEASURE_MODE = 10;
    public static final short TAG_GPS_DOP = 11;
    public static final short TAG_GPS_SPEED_REF = 12;
    public static final short TAG_GPS_SPEED = 13;
    public static final short TAG_GPS_TRACK_REF = 14;
    public static final short TAG_GPS_TRACK = 15;
    public static final short TAG_GPS_IMG_DIRECTION_REF = 16;
    public static final short TAG_GPS_IMG_DIRECTION = 17;
    public static final short TAG_GPS_MAP_DATUM = 18;
    public static final short TAG_GPS_DEST_LATITUDE_REF = 19;
    public static final short TAG_GPS_DEST_LATITUDE = 20;
    public static final short TAG_GPS_DEST_LONGITUDE_REF = 21;
    public static final short TAG_GPS_DEST_LONGITUDE = 22;
    public static final short TAG_GPS_DEST_BEARING_REF = 23;
    public static final short TAG_GPS_DEST_BEARING = 24;
    public static final short TAG_GPS_DEST_DISTANCE_REF = 25;
    public static final short TAG_GPS_DEST_DISTANCE = 26;
    public static final short TAG_GPS_PROCESSING_METHOD = 27;
    public static final short TAG_GPS_AREA_INFORMATION = 28;
    public static final short TAG_GPS_DATA_STAMP = 29;
    public static final short TAG_GPS_DIFFERENTIAL = 30;

    // Interoperability tag
    public static final short TAG_INTEROPERABILITY_INDEX = 1;

    /**
     * Constants for {@link #TAG_ORIENTATION}
     */
    public static interface Orientation {
        public static final short TOP_LEFT = 1;
        public static final short TOP_RIGHT = 2;
        public static final short BOTTOM_LEFT = 3;
        public static final short BOTTOM_RIGHT = 4;
        public static final short LEFT_TOP = 5;
        public static final short RIGHT_TOP = 6;
        public static final short LEFT_BOTTOM = 7;
        public static final short RIGHT_BOTTOM = 8;
    }

    /**
     * Constants for {@link #TAG_Y_CB_CR_POSITIONING}
     */
    public static interface YCbCrPositioning {
        public static final short CENTERED = 1;
        public static final short CO_SITED = 2;
    }

    /**
     * Constants for {@link #TAG_COMPRESSION}
     */
    public static interface Compression {
        public static final short UNCOMPRESSION = 1;
        public static final short JPEG = 6;
    }

    /**
     * Constants for {@link #TAG_RESOLUTION_UNIT}
     */
    public static interface ResolutionUnit {
        public static final short INCHES = 2;
        public static final short CENTIMETERS = 3;
    }

    /**
     * Constants for {@link #TAG_PHOTOMETRIC_INTERPRETATION}
     */
    public static interface PhotometricInterpretation {
        public static final short RGB = 2;
        public static final short YCBCR = 6;
    }

    /**
     * Constants for {@link #TAG_PLANAR_CONFIGURATION}
     */
    public static interface PlanarConfiguration {
        public static final short CHUNKY = 1;
        public static final short PLANAR = 2;
    }

    /**
     * Constants for {@link #TAG_EXPOSURE_PROGRAM}
     */
    public static interface ExposureProgram {
        public static final short NOT_DEFINED = 0;
        public static final short MANUAL = 1;
        public static final short NORMAL_PROGRAM = 2;
        public static final short APERTURE_PRIORITY = 3;
        public static final short SHUTTER_PRIORITY = 4;
        public static final short CREATIVE_PROGRAM = 5;
        public static final short ACTION_PROGRAM = 6;
        public static final short PROTRAIT_MODE = 7;
        public static final short LANDSCAPE_MODE = 8;
    }

    /**
     * Constants for {@link #TAG_METERING_MODE}
     */
    public static interface MeteringMode {
        public static final short UNKNOWN = 0;
        public static final short AVERAGE = 1;
        public static final short CENTER_WEIGHTED_AVERAGE = 2;
        public static final short SPOT = 3;
        public static final short MULTISPOT = 4;
        public static final short PATTERN = 5;
        public static final short PARTAIL = 6;
        public static final short OTHER = 255;
    }

    /**
     * Constants for {@link #TAG_FLASH} As the definition in Jeita EXIF 2.2 standard, we can
     * treat this constant as bitwise flag.
     * <p>
     * e.g.
     * <p>
     * short flash = FIRED | RETURN_STROBE_RETURN_LIGHT_DETECTED | MODE_AUTO_MODE
     */
    public static interface Flash {
        // LSB
        public static final short DID_NOT_FIRED = 0;
        public static final short FIRED = 1;
        // 1st~2nd bits
        public static final short RETURN_NO_STROBE_RETURN_DETECTION_FUNCTION = 0 << 1;
        public static final short RETURN_STROBE_RETURN_LIGHT_NOT_DETECTED = 2 << 1;
        public static final short RETURN_STROBE_RETURN_LIGHT_DETECTED = 3 << 1;
        // 3rd~4th bits
        public static final short MODE_UNKNOWN = 0 << 3;
        public static final short MODE_COMPULSORY_FLASH_FIRING = 1 << 3;
        public static final short MODE_COMPULSORY_FLASH_SUPPRESSION = 2 << 3;
        public static final short MODE_AUTO_MODE = 3 << 3;
        // 5th bit
        public static final short FUNCTION_PRESENT = 0 << 5;
        public static final short FUNCTION_NO_FUNCTION = 1 << 5;
        // 6th bit
        public static final short RED_EYE_REDUCTION_NO_OR_UNKNOWN = 0 << 6;
        public static final short RED_EYE_REDUCTION_SUPPORT = 1 << 6;
    }

    /**
     * Constants for {@link #TAG_COLOR_SPACE}
     */
    public static interface ColorSpace {
        public static final short SRGB = 1;
        public static final short UNCALIBRATED = (short) 0xFFFF;
    }

    /**
     * Constants for {@link #TAG_EXPOSURE_MODE}
     */
    public static interface ExposureMode {
        public static final short AUTO_EXPOSURE = 0;
        public static final short MANUAL_EXPOSURE = 1;
        public static final short AUTO_BRACKET = 2;
    }

    /**
     * Constants for {@link #TAG_WHITE_BALANCE}
     */
    public static interface WhiteBalance {
        public static final short AUTO = 0;
        public static final short MANUAL = 1;
    }

    /**
     * Constants for {@link #TAG_SCENE_CAPTURE_TYPE}
     */
    public static interface SceneCapture {
        public static final short STANDARD = 0;
        public static final short LANDSCAPE = 1;
        public static final short PROTRAIT = 2;
        public static final short NIGHT_SCENE = 3;
    }

    /**
     * Constants for {@link #TAG_COMPONENTS_CONFIGURATION}
     */
    public static interface ComponentsConfiguration {
        public static final short NOT_EXIST = 0;
        public static final short Y = 1;
        public static final short CB = 2;
        public static final short CR = 3;
        public static final short R = 4;
        public static final short G = 5;
        public static final short B = 6;
    }

    /**
     * Constants for {@link #TAG_LIGHT_SOURCE}
     */
    public static interface LightSource {
        public static final short UNKNOWN = 0;
        public static final short DAYLIGHT = 1;
        public static final short FLUORESCENT = 2;
        public static final short TUNGSTEN = 3;
        public static final short FLASH = 4;
        public static final short FINE_WEATHER = 9;
        public static final short CLOUDY_WEATHER = 10;
        public static final short SHADE = 11;
        public static final short DAYLIGHT_FLUORESCENT = 12;
        public static final short DAY_WHITE_FLUORESCENT = 13;
        public static final short COOL_WHITE_FLUORESCENT = 14;
        public static final short WHITE_FLUORESCENT = 15;
        public static final short STANDARD_LIGHT_A = 17;
        public static final short STANDARD_LIGHT_B = 18;
        public static final short STANDARD_LIGHT_C = 19;
        public static final short D55 = 20;
        public static final short D65 = 21;
        public static final short D75 = 22;
        public static final short D50 = 23;
        public static final short ISO_STUDIO_TUNGSTEN = 24;
        public static final short OTHER = 255;
    }

    /**
     * Constants for {@link #TAG_SENSING_METHOD}
     */
    public static interface SensingMethod {
        public static final short NOT_DEFINED = 1;
        public static final short ONE_CHIP_COLOR = 2;
        public static final short TWO_CHIP_COLOR = 3;
        public static final short THREE_CHIP_COLOR = 4;
        public static final short COLOR_SEQUENTIAL_AREA = 5;
        public static final short TRILINEAR = 7;
        public static final short COLOR_SEQUENTIAL_LINEAR = 8;
    }

    /**
     * Constants for {@link #TAG_FILE_SOURCE}
     */
    public static interface FileSource {
        public static final short DSC = 3;
    }

    /**
     * Constants for {@link #TAG_SCENE_TYPE}
     */
    public static interface SceneType {
        public static final short DIRECT_PHOTOGRAPHED = 1;
    }

    /**
     * Constants for {@link #TAG_GAIN_CONTROL}
     */
    public static interface GainControl {
        public static final short NONE = 0;
        public static final short LOW_UP = 1;
        public static final short HIGH_UP = 2;
        public static final short LOW_DOWN = 3;
        public static final short HIGH_DOWN = 4;
    }

    /**
     * Constants for {@link #TAG_CONTRAST}
     */
    public static interface Contrast {
        public static final short NORMAL = 0;
        public static final short SOFT = 1;
        public static final short HARD = 2;
    }

    /**
     * Constants for {@link #TAG_SATURATION}
     */
    public static interface Saturation {
        public static final short NORMAL = 0;
        public static final short LOW = 1;
        public static final short HIGH = 2;
    }

    /**
     * Constants for {@link #TAG_SHARPNESS}
     */
    public static interface Sharpness {
        public static final short NORMAL = 0;
        public static final short SOFT = 1;
        public static final short HARD = 2;
    }

    /**
     * Constants for {@link #TAG_SUBJECT_DISTANCE}
     */
    public static interface SubjectDistance {
        public static final short UNKNOWN = 0;
        public static final short MACRO = 1;
        public static final short CLOSE_VIEW = 2;
        public static final short DISTANT_VIEW = 3;
    }

    /**
     * Constants for {@link #TAG_GPS_LATITUDE_REF}, {@link #TAG_GPS_DEST_LATITUDE_REF}
     */
    public static interface GpsLatitudeRef {
        public static final String NORTH = "N";
        public static final String SOUTH = "S";
    }

    /**
     * Constants for {@link #TAG_GPS_LONGITUDE_REF}, {@link #TAG_GPS_DEST_LONGITUDE_REF}
     */
    public static interface GpsLongitudeRef {
        public static final String EAST = "E";
        public static final String WEST = "W";
    }

    /**
     * Constants for {@link #TAG_GPS_ALTITUDE_REF}
     */
    public static interface GpsAltitudeRef {
        public static final short SEA_LEVEL = 0;
        public static final short SEA_LEVEL_NEGATIVE = 1;
    }

    /**
     * Constants for {@link #TAG_GPS_STATUS}
     */
    public static interface GpsStatus {
        public static final String IN_PROGRESS = "A";
        public static final String INTEROPERABILITY = "V";
    }

    /**
     * Constants for {@link #TAG_GPS_MEASURE_MODE}
     */
    public static interface GpsMeasureMode {
        public static final String MODE_2_DIMENSIONAL = "2";
        public static final String MODE_3_DIMENSIONAL = "3";
    }

    /**
     * Constants for {@link #TAG_GPS_SPEED_REF}, {@link #TAG_GPS_DEST_DISTANCE_REF}
     */
    public static interface GpsSpeedRef {
        public static final String KILOMETERS = "K";
        public static final String MILES = "M";
        public static final String KNOTS = "N";
    }

    /**
     * Constants for {@link #TAG_GPS_TRACK_REF}, {@link #TAG_GPS_IMG_DIRECTION_REF},
     * {@link #TAG_GPS_DEST_BEARING_REF}
     */
    public static interface GpsTrackRef {
        public static final String TRUE_DIRECTION = "T";
        public static final String MAGNETIC_DIRECTION = "M";
    }

    /**
     * Constants for {@link #TAG_GPS_DIFFERENTIAL}
     */
    public static interface GpsDifferential {
        public static final short WITHOUT_DIFFERENTIAL_CORRECTION = 0;
        public static final short DIFFERENTIAL_CORRECTION_APPLIED = 1;
    }

    /**
     * The BYTE type in the EXIF standard. An 8-bit unsigned integer.
     */
    public static final short TYPE_UNSIGNED_BYTE = 1;
    /**
     * The ASCII type in the EXIF standard. An 8-bit byte containing one 7-bit ASCII code.
     * The final byte is terminated with NULL.
     */
    public static final short TYPE_ASCII = 2;
    /**
     * The SHORT type in the EXIF standard. A 16-bit (2-byte) unsigned integer
     */
    public static final short TYPE_UNSIGNED_SHORT = 3;
    /**
     * The LONG type in the EXIF standard. A 32-bit (4-byte) unsigned integer
     */
    public static final short TYPE_UNSIGNED_LONG = 4;
    /**
     * The RATIONAL type of EXIF standard. It consists of two LONGs. The first one is the numerator
     * and the second one expresses the denominator.
     */
    public static final short TYPE_UNSIGNED_RATIONAL = 5;
    /**
     * The UNDEFINED type in the EXIF standard. An 8-bit byte that can take any value
     * depending on the field definition.
     */
    public static final short TYPE_UNDEFINED = 7;
    /**
     * The SLONG type in the EXIF standard. A 32-bit (4-byte) signed integer
     * (2's complement notation).
     */
    public static final short TYPE_LONG = 9;
    /**
     * The SRATIONAL type of EXIF standard. It consists of two SLONGs. The first one is the
     * numerator and the second one is the denominator.
     */
    public static final short TYPE_RATIONAL = 10;

    private static final int TYPE_TO_SIZE_MAP[] = new int[11];
    static {
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_BYTE] = 1;
        TYPE_TO_SIZE_MAP[TYPE_ASCII] = 1;
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_SHORT] = 2;
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_LONG] = 4;
        TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_RATIONAL] = 8;
        TYPE_TO_SIZE_MAP[TYPE_UNDEFINED] = 1;
        TYPE_TO_SIZE_MAP[TYPE_LONG] = 4;
        TYPE_TO_SIZE_MAP[TYPE_RATIONAL] = 8;
    }

    /**
     * Gets the element size of the given data type.
     *
     * @see #TYPE_ASCII
     * @see #TYPE_LONG
     * @see #TYPE_RATIONAL
     * @see #TYPE_UNDEFINED
     * @see #TYPE_UNSIGNED_BYTE
     * @see #TYPE_UNSIGNED_LONG
     * @see #TYPE_UNSIGNED_RATIONAL
     * @see #TYPE_UNSIGNED_SHORT
     */
    public static int getElementSize(short type) {
        return TYPE_TO_SIZE_MAP[type];
    }

    private static volatile SparseArray<Integer> sTagInfo = null;
    private static volatile SparseArray<Integer> sInteroperTagInfo = null;
    private static final int SIZE_UNDEFINED = 0;

    private static SparseArray<Integer> getTagInfo() {
        if (sTagInfo == null) {
            synchronized(ExifTag.class) {
                if (sTagInfo == null) {
                    sTagInfo = new SparseArray<Integer>();
                    initTagInfo();
                }
            }
        }
        return sTagInfo;
    }

    private static SparseArray<Integer> getInteroperTagInfo() {
        if (sInteroperTagInfo == null) {
            synchronized(ExifTag.class) {
                if (sInteroperTagInfo == null) {
                    sInteroperTagInfo = new SparseArray<Integer>();
                    sInteroperTagInfo.put(TAG_INTEROPERABILITY_INDEX,
                            (IfdId.TYPE_IFD_INTEROPERABILITY << 24)
                            | TYPE_ASCII << 16 | SIZE_UNDEFINED);
                }
            }
        }
        return sInteroperTagInfo;
    }

    private static void initTagInfo() {
        /**
         * We put tag information in a 4-bytes integer. The first byte is the
         * IFD of the tag, and the second byte is the default data type. The
         * last two byte are a short value indicating the component count of this
         * tag.
         */
        sTagInfo.put(TAG_MAKE,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_IMAGE_WIDTH,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_IMAGE_LENGTH,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_BITS_PER_SAMPLE,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 3);
        sTagInfo.put(TAG_COMPRESSION,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_PHOTOMETRIC_INTERPRETATION,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_ORIENTATION, (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_SAMPLES_PER_PIXEL,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_PLANAR_CONFIGURATION,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_Y_CB_CR_SUB_SAMPLING,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 2);
        sTagInfo.put(TAG_Y_CB_CR_POSITIONING,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_X_RESOLUTION,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_Y_RESOLUTION,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_RESOLUTION_UNIT,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_STRIP_OFFSETS,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_ROWS_PER_STRIP,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_STRIP_BYTE_COUNTS,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_JPEG_INTERCHANGE_FORMAT,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_TRANSFER_FUNCTION,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_SHORT << 16 | 3 * 256);
        sTagInfo.put(TAG_WHITE_POINT,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 2);
        sTagInfo.put(TAG_PRIMARY_CHROMATICITIES,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 6);
        sTagInfo.put(TAG_Y_CB_CR_COEFFICIENTS,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 3);
        sTagInfo.put(TAG_REFERENCE_BLACK_WHITE,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 6);
        sTagInfo.put(TAG_DATE_TIME,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | 20);
        sTagInfo.put(TAG_IMAGE_DESCRIPTION,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_MAKE,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_MODEL,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_SOFTWARE,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_ARTIST,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_COPYRIGHT,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_EXIF_IFD,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_GPS_IFD,
                (IfdId.TYPE_IFD_0 << 24) | TYPE_UNSIGNED_LONG << 16 | 1);

        // EXIF TAG
        sTagInfo.put(TAG_EXIF_VERSION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | 4);
        sTagInfo.put(TAG_FLASHPIX_VERSION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | 4);
        sTagInfo.put(TAG_COLOR_SPACE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_COMPONENTS_CONFIGURATION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | 4);
        sTagInfo.put(TAG_COMPRESSED_BITS_PER_PIXEL,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_PIXEL_X_DIMENSION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_PIXEL_Y_DIMENSION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_LONG << 16 | 1);
        sTagInfo.put(TAG_MAKER_NOTE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_USER_COMMENT,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_RELATED_SOUND_FILE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | 13);
        sTagInfo.put(TAG_DATE_TIME_ORIGINAL,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | 20);
        sTagInfo.put(TAG_DATE_TIME_DIGITIZED,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | 20);
        sTagInfo.put(TAG_SUB_SEC_TIME,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_SUB_SEC_TIME_ORIGINAL,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_SUB_SEC_TIME_DIGITIZED,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_IMAGE_UNIQUE_ID,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | 33);
        sTagInfo.put(TAG_EXPOSURE_TIME,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_F_NUMBER,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_EXPOSURE_PROGRAM,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_SPECTRAL_SENSITIVITY,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_ISO_SPEED_RATINGS,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_OECF,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_SHUTTER_SPEED_VALUE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_APERTURE_VALUE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_BRIGHTNESS_VALUE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_EXPOSURE_BIAS_VALUE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_MAX_APERTURE_VALUE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_SUBJECT_DISTANCE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_METERING_MODE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_LIGHT_SOURCE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_FLASH,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_FOCAL_LENGTH,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_SUBJECT_AREA,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_FLASH_ENERGY,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_SPATIAL_FREQUENCY_RESPONSE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_FOCAL_PLANE_X_RESOLUTION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_FOCAL_PLANE_Y_RESOLUTION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_FOCAL_PLANE_RESOLUTION_UNIT,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_SUBJECT_LOCATION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 2);
        sTagInfo.put(TAG_EXPOSURE_INDEX,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_SENSING_METHOD,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_FILE_SOURCE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | 1);
        sTagInfo.put(TAG_SCENE_TYPE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | 1);
        sTagInfo.put(TAG_CFA_PATTERN,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_CUSTOM_RENDERED,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_EXPOSURE_MODE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_WHITE_BALANCE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_DIGITAL_ZOOM_RATIO,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_FOCAL_LENGTH_IN_35_MM_FILE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_SCENE_CAPTURE_TYPE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_GAIN_CONTROL,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_CONTRAST,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_SATURATION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_SHARPNESS,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        sTagInfo.put(TAG_DEVICE_SETTING_DESCRIPTION,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_SUBJECT_DISTANCE_RANGE,
                (IfdId.TYPE_IFD_EXIF << 24) | TYPE_UNSIGNED_SHORT << 16 | 1);
        // GPS tag
        sTagInfo.put(TAG_GPS_VERSION_ID,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_BYTE << 16 | 4);
        sTagInfo.put(TAG_GPS_LATITUDE_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_LONGITUDE_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_LATITUDE,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_RATIONAL << 16 | 3);
        sTagInfo.put(TAG_GPS_LONGITUDE,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_RATIONAL << 16 | 3);
        sTagInfo.put(TAG_GPS_ALTITUDE_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_BYTE << 16 | 1);
        sTagInfo.put(TAG_GPS_ALTITUDE,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_TIME_STAMP,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 3);
        sTagInfo.put(TAG_GPS_SATTELLITES,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_GPS_STATUS,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_MEASURE_MODE,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_DOP,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_SPEED_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_SPEED,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_TRACK_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_TRACK,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_IMG_DIRECTION_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_IMG_DIRECTION,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_MAP_DATUM,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_GPS_DEST_LATITUDE_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_DEST_LATITUDE,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_DEST_BEARING_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_DEST_BEARING,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_DEST_DISTANCE_REF,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 2);
        sTagInfo.put(TAG_GPS_DEST_DISTANCE,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_RATIONAL << 16 | 1);
        sTagInfo.put(TAG_GPS_PROCESSING_METHOD,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_GPS_AREA_INFORMATION,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNDEFINED << 16 | SIZE_UNDEFINED);
        sTagInfo.put(TAG_GPS_DATA_STAMP,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_ASCII << 16 | 11);
        sTagInfo.put(TAG_GPS_DIFFERENTIAL,
                (IfdId.TYPE_IFD_GPS << 24) | TYPE_UNSIGNED_SHORT << 16 | 11);
    }

    private final short mTagId;
    private final short mDataType;
    private final int mIfd;
    private final boolean mComponentCountDefined;
    private int mComponentCount;
    private Object mValue;
    private int mOffset;

    static private short getTypeFromInfo(int info) {
        return (short) ((info >> 16) & 0xff);
    }

    static private int getComponentCountFromInfo(int info) {
        return info & 0xffff;
    }

    static private int getIfdIdFromInfo(int info) {
        return (info >> 24) & 0xff;
    }

    static private boolean getComponentCountDefined(short tagId, int ifd) {
        Integer info = (ifd == IfdId.TYPE_IFD_INTEROPERABILITY) ?
                getInteroperTagInfo().get(tagId) : getTagInfo().get(tagId);
        if (info == null) return false;
        return getComponentCountFromInfo(info) != SIZE_UNDEFINED;
    }

    static int getIfdIdFromTagId(short tagId) {
        Integer info = getTagInfo().get(tagId);
        if (info == null) {
            throw new IllegalArgumentException("Unknown Tag ID: " + tagId);
        }
        return getIfdIdFromInfo(info);
    }

    /**
     * Create a tag with given ID. For tags related to interoperability and thumbnail, call
     * {@link #buildInteroperabilityTag(short)} and {@link #buildThumbnailTag(short)} respectively.
     * @exception IllegalArgumentException If the ID is invalid.
     */
    static public ExifTag buildTag(short tagId) {
        Integer info = getTagInfo().get(tagId);
        if (info == null) {
            throw new IllegalArgumentException("Unknown Tag ID: " + tagId);
        }
        return new ExifTag(tagId, getTypeFromInfo(info),
                getComponentCountFromInfo(info),
                getIfdIdFromInfo(info));
    }

    /**
     * Create a tag related to thumbnail with given ID.
     * @exception IllegalArgumentException If the ID is invalid.
     */
    static public ExifTag buildThumbnailTag(short tagId) {
        Integer info = getTagInfo().get(tagId);
        if (info == null || getIfdIdFromInfo(info) != IfdId.TYPE_IFD_0) {
            throw new IllegalArgumentException("Unknown Thumnail Tag ID: " + tagId);
        }
        return new ExifTag(tagId, getTypeFromInfo(info),
                getComponentCountFromInfo(info),
                IfdId.TYPE_IFD_1);
    }

    /**
     * Create a tag related to interoperability with given ID.
     * @exception IllegalArgumentException If the ID is invalid.
     */
    static public ExifTag buildInteroperabilityTag(short tagId) {
        Integer info = getInteroperTagInfo().get(tagId);
        if (info == null || getIfdIdFromInfo(info) != IfdId.TYPE_IFD_INTEROPERABILITY) {
            throw new RuntimeException("Unknown Interoperability Tag ID: " + tagId);
        }
        return new ExifTag(tagId, getTypeFromInfo(info),
                getComponentCountFromInfo(info),
                IfdId.TYPE_IFD_INTEROPERABILITY);
    }

    ExifTag(short tagId, short type, int componentCount, int ifd) {
        mTagId = tagId;
        mDataType = type;
        mComponentCount = componentCount;
        mComponentCountDefined = getComponentCountDefined(tagId, ifd);
        mIfd = ifd;
    }

    /**
     * Returns the ID of the IFD this tag belongs to.
     *
     * @see IfdId#TYPE_IFD_0
     * @see IfdId#TYPE_IFD_1
     * @see IfdId#TYPE_IFD_EXIF
     * @see IfdId#TYPE_IFD_GPS
     * @see IfdId#TYPE_IFD_INTEROPERABILITY
     */
    public int getIfd() {
        return mIfd;
    }

    /**
     * Gets the ID of this tag.
     */
    public short getTagId() {
        return mTagId;
    }

    /**
     * Gets the data type of this tag
     *
     * @see #TYPE_ASCII
     * @see #TYPE_LONG
     * @see #TYPE_RATIONAL
     * @see #TYPE_UNDEFINED
     * @see #TYPE_UNSIGNED_BYTE
     * @see #TYPE_UNSIGNED_LONG
     * @see #TYPE_UNSIGNED_RATIONAL
     * @see #TYPE_UNSIGNED_SHORT
     */
    public short getDataType() {
        return mDataType;
    }

    /**
     * Gets the total data size in bytes of the value of this tag.
     */
    public int getDataSize() {
        return getComponentCount() * getElementSize(getDataType());
    }

    /**
     * Gets the component count of this tag.
     */
    public int getComponentCount() {
        return mComponentCount;
    }

    /**
     * Returns true if this ExifTag contains value; otherwise, this tag will contain an offset value
     * that links to the area where the actual value is located.
     *
     * @see #getOffset()
     */
    public boolean hasValue() {
        return mValue != null;
    }

    /**
     * Gets the offset of this tag. This is only valid if this data size > 4 and contains an offset
     * to the location of the actual value.
     */
    public int getOffset() {
        return mOffset;
    }

    /**
     * Sets the offset of this tag.
     */
    void setOffset(int offset) {
        mOffset = offset;
    }

    private void checkComponentCountOrThrow(int count)
            throws IllegalArgumentException {
        if (mComponentCountDefined && (mComponentCount != count)) {
            throw new IllegalArgumentException("Tag " + mTagId + ": Required "
                    + mComponentCount + " components but was given " + count
                    + " component(s)");
        }
    }

    private void throwTypeNotMatchedException(String className)
            throws IllegalArgumentException {
        throw new IllegalArgumentException("Tag " + mTagId + ": expect type " +
                convertTypeToString(mDataType) + " but got " + className);
    }

    private static String convertTypeToString(short type) {
        switch (type) {
            case TYPE_UNSIGNED_BYTE:
                return "UNSIGNED_BYTE";
            case TYPE_ASCII:
                return "ASCII";
            case TYPE_UNSIGNED_SHORT:
                return "UNSIGNED_SHORT";
            case TYPE_UNSIGNED_LONG:
                return "UNSIGNED_LONG";
            case TYPE_UNSIGNED_RATIONAL:
                return "UNSIGNED_RATIONAL";
            case TYPE_UNDEFINED:
                return "UNDEFINED";
            case TYPE_LONG:
                return "LONG";
            case TYPE_RATIONAL:
                return "RATIONAL";
            default:
                return "";
        }
    }

    private static final int UNSIGNED_SHORT_MAX = 65535;
    private static final long UNSIGNED_LONG_MAX = 4294967295L;
    private static final long LONG_MAX = Integer.MAX_VALUE;
    private static final long LONG_MIN = Integer.MIN_VALUE;

    private void checkOverflowForUnsignedShort(int[] value) {
        for (int v : value) {
            if (v > UNSIGNED_SHORT_MAX || v < 0) {
                throw new IllegalArgumentException(
                        "Tag " + mTagId+ ": Value" + v +
                        " is illegal for type UNSIGNED_SHORT");
            }
        }
    }

    private void checkOverflowForUnsignedLong(long[] value) {
        for (long v: value) {
            if (v < 0 || v > UNSIGNED_LONG_MAX) {
                throw new IllegalArgumentException(
                        "Tag " + mTagId+ ": Value" + v +
                        " is illegal for type UNSIGNED_LONG");
            }
        }
    }

    private void checkOverflowForUnsignedLong(int[] value) {
        for (int v: value) {
            if (v < 0) {
                throw new IllegalArgumentException(
                        "Tag " + mTagId+ ": Value" + v +
                        " is illegal for type UNSIGNED_LONG");
            }
        }
    }

    private void checkOverflowForUnsignedRational(Rational[] value) {
        for (Rational v: value) {
            if (v.getNominator() < 0 || v.getDenominator() < 0
                    || v.getNominator() > UNSIGNED_LONG_MAX
                    || v.getDenominator() > UNSIGNED_LONG_MAX) {
                throw new IllegalArgumentException(
                        "Tag " + mTagId+ ": Value" + v +
                        " is illegal for type UNSIGNED_RATIONAL");
            }
        }
    }

    private void checkOverflowForRational(Rational[] value) {
        for (Rational v: value) {
            if (v.getNominator() < LONG_MIN || v.getDenominator() < LONG_MIN
                    || v.getNominator() > LONG_MAX
                    || v.getDenominator() > LONG_MAX) {
                throw new IllegalArgumentException(
                        "Tag " + mTagId+ ": Value" + v +
                        " is illegal for type RATIONAL");
            }
        }
    }

    /**
     * Sets integer values into this tag.
     * @exception IllegalArgumentException for the following situation:
     * <ul>
     *     <li>The component type of this tag is not {@link #TYPE_UNSIGNED_SHORT},
     *      {@link #TYPE_UNSIGNED_LONG}, or {@link #TYPE_LONG}.</li>
     *     <li>The value overflows. </li>
     *     <li>The value.length does NOT match the definition of component count in
     *      EXIF standard.</li>
     * </ul>
     */
    public void setValue(int[] value) {
        checkComponentCountOrThrow(value.length);
        if (mDataType != TYPE_UNSIGNED_SHORT && mDataType != TYPE_LONG &&
                mDataType != TYPE_UNSIGNED_LONG) {
            throwTypeNotMatchedException("int");
        }
        if (mDataType == TYPE_UNSIGNED_SHORT) {
            checkOverflowForUnsignedShort(value);
        } else if (mDataType == TYPE_UNSIGNED_LONG) {
            checkOverflowForUnsignedLong(value);
        }

        long[] data = new long[value.length];
        for (int i = 0; i < value.length; i++) {
            data[i] = value[i];
        }
        mValue = data;
        mComponentCount = value.length;
    }

    /**
     * Sets integer values into this tag.
     * @exception IllegalArgumentException For the following situation:
     * <ul>
     *     <li>The component type of this tag is not {@link #TYPE_UNSIGNED_SHORT},
     *      {@link #TYPE_UNSIGNED_LONG}, or {@link #TYPE_LONG}.</li>
     *     <li>The value overflows.</li>
     *     <li>The component count in the definition of EXIF standard is not 1.</li>
     * </ul>
     */
    public void setValue(int value) {
        checkComponentCountOrThrow(1);
        setValue(new int[] {value});
    }

    /**
     * Sets long values into this tag.
     * @exception IllegalArgumentException For the following situation:
     * <ul>
     *      <li>The component type of this tag is not {@link #TYPE_UNSIGNED_LONG}.</li>
     *      <li>The value overflows. </li>
     *      <li>The value.length does NOT match the definition of component count in
     *       EXIF standard.</li>
     * </ul>
     */
    public void setValue(long[] value) {
        checkComponentCountOrThrow(value.length);
        if (mDataType != TYPE_UNSIGNED_LONG) {
            throwTypeNotMatchedException("long");
        }
        checkOverflowForUnsignedLong(value);
        mValue = value;
        mComponentCount = value.length;
    }

    /**
     * Sets long values into this tag.
     * @exception IllegalArgumentException For the following situation:
     * <ul>
     *     <li>The component type of this tag is not {@link #TYPE_UNSIGNED_LONG}.</li>
     *     <li>The value overflows. </li>
     *     <li>The component count in the definition of EXIF standard is not 1.</li>
     * </ul>
     */
    public void setValue(long value) {
        setValue(new long[] {value});
    }

    /**
     * Sets string values into this tag.
     * @exception IllegalArgumentException If the data type is not {@link #TYPE_ASCII}
     * or value.length() + 1 does NOT fit the definition of the component count in the
     * EXIF standard.
     */
    public void setValue(String value) {
        checkComponentCountOrThrow(value.length() + 1);
        if (mDataType != TYPE_ASCII) {
            throwTypeNotMatchedException("String");
        }
        mComponentCount = value.length() + 1;
        mValue = value;
    }

    /**
     * Sets Rational values into this tag.
     * @exception IllegalArgumentException For the following situation:
     * <ul>
     *      <li>The component type of this tag is not {@link #TYPE_UNSIGNED_RATIONAL} or
     *       {@link #TYPE_RATIONAL} .</li>
     *      <li>The value overflows. </li>
     *      <li>The value.length does NOT match the definition of component count in
     *       EXIF standard.</li>
     * </ul>
     */
    public void setValue(Rational[] value) {
        if (mDataType == TYPE_UNSIGNED_RATIONAL) {
            checkOverflowForUnsignedRational(value);
        } else if (mDataType == TYPE_RATIONAL) {
            checkOverflowForRational(value);
        } else {
            throwTypeNotMatchedException("Rational");
        }
        checkComponentCountOrThrow(value.length);
        mValue = value;
        mComponentCount = value.length;
    }

    /**
     * Sets Rational values into this tag.
     * @exception IllegalArgumentException For the following situation:
     * <ul>
     *      <li>The component type of this tag is not {@link #TYPE_UNSIGNED_RATIONAL} or
     *       {@link #TYPE_RATIONAL} .</li>
     *      <li>The value overflows. </li>
     *      <li>The component count in the definition of EXIF standard is not 1.</li>
     * </ul>
     * */
    public void setValue(Rational value) {
        setValue(new Rational[] {value});
    }

    /**
     * Sets byte values into this tag.
     * @exception IllegalArgumentException For the following situation:
     * <ul>
     *      <li>The component type of this tag is not {@link #TYPE_UNSIGNED_BYTE} or
     *       {@link #TYPE_UNDEFINED} .</li>
     *      <li>The length does NOT match the definition of component count in EXIF standard.</li>
     * </ul>
     * */
    public void setValue(byte[] value, int offset, int length) {
        checkComponentCountOrThrow(length);
        if (mDataType != TYPE_UNSIGNED_BYTE && mDataType != TYPE_UNDEFINED) {
            throwTypeNotMatchedException("byte");
        }
        mValue = new byte[length];
        System.arraycopy(value, offset, mValue, 0, length);
        mComponentCount = length;
    }

    /**
     * Equivalent to setValue(value, 0, value.length).
     */
    public void setValue(byte[] value) {
        setValue(value, 0, value.length);
    }

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy:MM:dd kk:mm:ss");

    /**
     * Sets a timestamp to this tag. The method converts the timestamp with the format of
     * "yyyy:MM:dd kk:mm:ss" and calls {@link #setValue(String)}.
     *
     * @param time the number of milliseconds since Jan. 1, 1970 GMT
     * @exception IllegalArgumentException If the data type is not {@link #TYPE_ASCII}
     * or the component count of this tag is not 20 or undefined
     */
    public void setTimeValue(long time) {
        // synchronized on TIME_FORMAT as SimpleDateFormat is not thread safe
        synchronized (TIME_FORMAT) {
            setValue(TIME_FORMAT.format(new Date(time)));
        }
    }

    /**
     * Gets the {@link #TYPE_UNSIGNED_SHORT} data.
     * @exception IllegalArgumentException If the type is NOT {@link #TYPE_UNSIGNED_SHORT}.
     */
    public int getUnsignedShort(int index) {
        if (mDataType != TYPE_UNSIGNED_SHORT) {
            throw new IllegalArgumentException("Cannot get UNSIGNED_SHORT value from "
                    + convertTypeToString(mDataType));
        }
        return (int) (((long[]) mValue) [index]);
    }

    /**
     * Gets the {@link #TYPE_LONG} data.
     * @exception IllegalArgumentException If the type is NOT {@link #TYPE_LONG}.
     */
    public int getLong(int index) {
        if (mDataType != TYPE_LONG) {
            throw new IllegalArgumentException("Cannot get LONG value from "
                    + convertTypeToString(mDataType));
        }
        return (int) (((long[]) mValue) [index]);
    }

    /**
     * Gets the {@link #TYPE_UNSIGNED_LONG} data.
     * @exception IllegalArgumentException If the type is NOT {@link #TYPE_UNSIGNED_LONG}.
     */
    public long getUnsignedLong(int index) {
        if (mDataType != TYPE_UNSIGNED_LONG) {
            throw new IllegalArgumentException("Cannot get UNSIGNED LONG value from "
                    + convertTypeToString(mDataType));
        }
        return ((long[]) mValue) [index];
    }

    /**
     * Gets the {@link #TYPE_ASCII} data.
     * @exception IllegalArgumentException If the type is NOT {@link #TYPE_ASCII}.
     */
    public String getString() {
        if (mDataType != TYPE_ASCII) {
            throw new IllegalArgumentException("Cannot get ASCII value from "
                    + convertTypeToString(mDataType));
        }
        return (String) mValue;
    }

    /**
     * Gets the {@link #TYPE_RATIONAL} or {@link #TYPE_UNSIGNED_RATIONAL} data.
     * @exception IllegalArgumentException If the type is NOT {@link #TYPE_RATIONAL} or
     * {@link #TYPE_UNSIGNED_RATIONAL}.
     */
    public Rational getRational(int index) {
        if ((mDataType != TYPE_RATIONAL) && (mDataType != TYPE_UNSIGNED_RATIONAL)) {
            throw new IllegalArgumentException("Cannot get RATIONAL value from "
                    + convertTypeToString(mDataType));
        }
        return ((Rational[]) mValue) [index];
    }

    /**
     * Equivalent to getBytes(buffer, 0, buffer.length).
     */
    public void getBytes(byte[] buf) {
        getBytes(buf, 0, buf.length);
    }

    /**
     * Gets the {@link #TYPE_UNDEFINED} or {@link #TYPE_UNSIGNED_BYTE} data.
     *
     * @param buf the byte array in which to store the bytes read.
     * @param offset the initial position in buffer to store the bytes.
     * @param length the maximum number of bytes to store in buffer. If length > component count,
     * only the valid bytes will be stored.
     *
     * @exception IllegalArgumentException If the type is NOT {@link #TYPE_UNDEFINED} or
     * {@link #TYPE_UNSIGNED_BYTE}.
     */
    public void getBytes(byte[] buf, int offset, int length) {
        if ((mDataType != TYPE_UNDEFINED) && (mDataType != TYPE_UNSIGNED_BYTE)) {
            throw new IllegalArgumentException("Cannot get BYTE value from "
                    + convertTypeToString(mDataType));
        }
        System.arraycopy(mValue, 0, buf, offset,
                (length > mComponentCount) ? mComponentCount : length);
    }

    /**
     * Returns a string representation of the value of this tag.
     */
    public String valueToString() {
        StringBuilder sbuilder = new StringBuilder();
        switch (getDataType()) {
            case ExifTag.TYPE_UNDEFINED:
            case ExifTag.TYPE_UNSIGNED_BYTE:
                byte buf[] = new byte[getComponentCount()];
                getBytes(buf);
                for(int i = 0, n = getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(String.format("%02x", buf[i]));
                }
                break;
            case ExifTag.TYPE_ASCII:
                sbuilder.append(getString());
                break;
            case ExifTag.TYPE_UNSIGNED_LONG:
                for(int i = 0, n = getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(getUnsignedLong(i));
                }
                break;
            case ExifTag.TYPE_RATIONAL:
            case ExifTag.TYPE_UNSIGNED_RATIONAL:
                for(int i = 0, n = getComponentCount(); i < n; i++) {
                    Rational r = getRational(i);
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(r.getNominator()).append("/").append(r.getDenominator());
                }
                break;
            case ExifTag.TYPE_UNSIGNED_SHORT:
                for(int i = 0, n = getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(getUnsignedShort(i));
                }
                break;
            case ExifTag.TYPE_LONG:
                for(int i = 0, n = getComponentCount(); i < n; i++) {
                    if(i != 0) sbuilder.append(" ");
                    sbuilder.append(getLong(i));
                }
                break;
        }
        return sbuilder.toString();
    }

    /**
     * Returns true if the ID is one of the following: {@link #TAG_EXIF_IFD},
     * {@link #TAG_GPS_IFD}, {@link #TAG_JPEG_INTERCHANGE_FORMAT},
     * {@link #TAG_STRIP_OFFSETS}, {@link #TAG_INTEROPERABILITY_IFD}
     */
    static boolean isOffsetTag(short tagId) {
        return tagId == TAG_EXIF_IFD
                || tagId == TAG_GPS_IFD
                || tagId == TAG_JPEG_INTERCHANGE_FORMAT
                || tagId == TAG_STRIP_OFFSETS
                || tagId == TAG_INTEROPERABILITY_IFD;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ExifTag) {
            ExifTag tag = (ExifTag) obj;
            if (mValue != null) {
                if (mValue instanceof long[]) {
                    if (!(tag.mValue instanceof long[])) return false;
                    return Arrays.equals((long[]) mValue, (long[]) tag.mValue);
                } else if (mValue instanceof Rational[]) {
                    if (!(tag.mValue instanceof Rational[])) return false;
                    return Arrays.equals((Rational[]) mValue, (Rational[]) tag.mValue);
                } else if (mValue instanceof byte[]) {
                    if (!(tag.mValue instanceof byte[])) return false;
                    return Arrays.equals((byte[]) mValue, (byte[]) tag.mValue);
                } else {
                    return mValue.equals(tag.mValue);
                }
            } else {
                return tag.mValue == null;
            }
        }
        return false;
    }
}
