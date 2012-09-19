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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *  This class stores the EXIF header in IFDs according to the JPEG specification.
 *  It is the result produced by {@link ExifReader}.
 *  @see ExifReader
 *  @see IfdData
 */
public class ExifData {
    private final IfdData[] mIfdDatas = new IfdData[IfdId.TYPE_IFD_COUNT];
    private byte[] mThumbnail;
    private ArrayList<byte[]> mStripBytes = new ArrayList<byte[]>();
    private final ByteOrder mByteOrder;

    public ExifData(ByteOrder order) {
        mByteOrder = order;
    }

    IfdData getIfdData(int ifdId) {
        return mIfdDatas[ifdId];
    }

    /**
     * Adds IFD data. If IFD data of the same type already exists,
     * it will be replaced by the new data.
     */
    void addIfdData(IfdData data) {
        mIfdDatas[data.getId()] = data;
    }

    /**
     * Gets the compressed thumbnail. Returns null if there is no compressed thumbnail.
     *
     * @see #hasCompressedThumbnail()
     */
    public byte[] getCompressedThumbnail() {
        return mThumbnail;
    }

    /**
     * Sets the compressed thumbnail.
     */
    public void setCompressedThumbnail(byte[] thumbnail) {
        mThumbnail = thumbnail;
    }

    /**
     * Returns true it this header contains a compressed thumbnail.
     */
    public boolean hasCompressedThumbnail() {
        return mThumbnail != null;
    }

    /**
     * Adds an uncompressed strip.
     */
    public void setStripBytes(int index, byte[] strip) {
        if (index < mStripBytes.size()) {
            mStripBytes.set(index, strip);
        } else {
            for (int i = mStripBytes.size(); i < index; i++) {
                mStripBytes.add(null);
            }
            mStripBytes.add(strip);
        }
    }

    /**
     * Gets the strip count.
     */
    public int getStripCount() {
        return mStripBytes.size();
    }

    /**
     * Gets the strip at the specified index.
     * @exceptions #IndexOutOfBoundException
     */
    public byte[] getStrip(int index) {
        return mStripBytes.get(index);
    }

    /**
     * Gets the byte order.
     */
    public ByteOrder getByteOrder() {
        return mByteOrder;
    }

    /**
     * Returns true if this header contains uncompressed strip of thumbnail.
     */
    public boolean hasUncompressedStrip() {
        return mStripBytes.size() != 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ExifData) {
            ExifData data = (ExifData) obj;
            if (data.mByteOrder != mByteOrder
                    || !Arrays.equals(data.mThumbnail, mThumbnail)
                    || data.mStripBytes.size() != mStripBytes.size()) return false;

            for (int i = 0; i < mStripBytes.size(); i++) {
                if (!Arrays.equals(data.mStripBytes.get(i), mStripBytes.get(i))) return false;
            }

            for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
                if (!Util.equals(data.getIfdData(i), getIfdData(i))) return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Adds {@link ExifTag#TAG_GPS_LATITUDE}, {@link ExifTag#TAG_GPS_LONGITUDE},
     * {@link ExifTag#TAG_GPS_LATITUDE_REF} and {@link ExifTag#TAG_GPS_LONGITUDE_REF} with the
     * given latitude and longitude.
     */
    public void addGpsTags(double latitude, double longitude) {
        IfdData gpsIfd = getIfdData(IfdId.TYPE_IFD_GPS);
        if (gpsIfd == null) {
            gpsIfd = new IfdData(IfdId.TYPE_IFD_GPS);
            addIfdData(gpsIfd);
        }
        ExifTag latTag = new ExifTag(ExifTag.TAG_GPS_LATITUDE, ExifTag.TYPE_RATIONAL,
                3, IfdId.TYPE_IFD_GPS);
        ExifTag longTag = new ExifTag(ExifTag.TAG_GPS_LONGITUDE, ExifTag.TYPE_RATIONAL,
                3, IfdId.TYPE_IFD_GPS);
        ExifTag latRefTag = new ExifTag(ExifTag.TAG_GPS_LATITUDE_REF,
                ExifTag.TYPE_ASCII, 2, IfdId.TYPE_IFD_GPS);
        ExifTag longRefTag = new ExifTag(ExifTag.TAG_GPS_LONGITUDE_REF,
                ExifTag.TYPE_ASCII, 2, IfdId.TYPE_IFD_GPS);
        latTag.setValue(toExifLatLong(latitude));
        longTag.setValue(toExifLatLong(longitude));
        latRefTag.setValue(latitude >= 0
                ? ExifTag.GpsLatitudeRef.NORTH
                : ExifTag.GpsLatitudeRef.SOUTH);
        longRefTag.setValue(longitude >= 0
                ? ExifTag.GpsLongitudeRef.EAST
                : ExifTag.GpsLongitudeRef.WEST);
        gpsIfd.setTag(latTag);
        gpsIfd.setTag(longTag);
        gpsIfd.setTag(latRefTag);
        gpsIfd.setTag(longRefTag);
    }

    private static Rational[] toExifLatLong(double value) {
        // convert to the format dd/1 mm/1 ssss/100
        value = Math.abs(value);
        int degrees = (int) value;
        value = (value - degrees) * 60;
        int minutes = (int) value;
        value = (value - minutes) * 6000;
        int seconds = (int) value;
        return new Rational[] {
                new Rational(degrees, 1), new Rational(minutes, 1), new Rational(seconds, 100)};
    }

    private IfdData getOrCreateIfdData(int ifdId) {
        IfdData ifdData = mIfdDatas[ifdId];
        if (ifdData == null) {
            ifdData = new IfdData(ifdId);
            mIfdDatas[ifdId] = ifdData;
        }
        return ifdData;
    }

    /**
     * Gets the tag with the given tag ID. Returns null if the tag does not exist. For tags
     * related to interoperability or thumbnail, call {@link #getInteroperabilityTag(short)} and
     * {@link #getThumbnailTag(short)} respectively.
     */
    public ExifTag getTag(short tagId) {
        int ifdId = ExifTag.getIfdIdFromTagId(tagId);
        IfdData ifdData = mIfdDatas[ifdId];
        return (ifdData == null) ? null : ifdData.getTag(tagId);
    }

    /**
     * Gets the thumbnail-related tag with the given tag ID.
     */
    public ExifTag getThumbnailTag(short tagId) {
        IfdData ifdData = mIfdDatas[IfdId.TYPE_IFD_1];
        return (ifdData == null) ? null : ifdData.getTag(tagId);
    }

    /**
     * Gets the interoperability-related tag with the given tag ID.
     */
    public ExifTag getInteroperabilityTag(short tagId) {
        IfdData ifdData = mIfdDatas[IfdId.TYPE_IFD_INTEROPERABILITY];
        return (ifdData == null) ? null : ifdData.getTag(tagId);
    }

    /**
     * Adds a tag with the given tag ID. The original tag will be replaced by the new tag. For tags
     * related to interoperability or thumbnail, call {@link #addInteroperabilityTag(short)} or
     * {@link #addThumbnailTag(short)} respectively.
     * @exception IllegalArgumentException if the tag ID is invalid.
     */
    public ExifTag addTag(short tagId) {
        int ifdId = ExifTag.getIfdIdFromTagId(tagId);
        IfdData ifdData = getOrCreateIfdData(ifdId);
        ExifTag tag = ExifTag.buildTag(tagId);
        ifdData.setTag(tag);
        return tag;
    }

    /**
     * Adds a thumbnail-related tag with the given tag ID. The original tag will be replaced
     * by the new tag.
     * @exception IllegalArgumentException if the tag ID is invalid.
     */
    public ExifTag addThumbnailTag(short tagId) {
        IfdData ifdData = getOrCreateIfdData(IfdId.TYPE_IFD_1);
        ExifTag tag = ExifTag.buildThumbnailTag(tagId);
        ifdData.setTag(tag);
        return tag;
    }

    /**
     * Adds an interoperability-related tag with the given tag ID. The original tag will be
     * replaced by the new tag.
     * @exception IllegalArgumentException if the tag ID is invalid.
     */
    public ExifTag addInteroperabilityTag(short tagId) {
        IfdData ifdData = getOrCreateIfdData(IfdId.TYPE_IFD_INTEROPERABILITY);
        ExifTag tag = ExifTag.buildInteroperabilityTag(tagId);
        ifdData.setTag(tag);
        return tag;
    }

    public void removeThumbnailData() {
        mThumbnail = null;
        mStripBytes.clear();
        mIfdDatas[IfdId.TYPE_IFD_1] = null;
    }
}