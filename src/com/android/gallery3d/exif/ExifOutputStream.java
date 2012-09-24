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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ExifOutputStream extends FilterOutputStream {
    private static final String TAG = "ExifOutputStream";

    private static final int STATE_SOI = 0;
    private static final int STATE_FRAME_HEADER = 1;
    private static final int STATE_JPEG_DATA = 2;

    private static final int EXIF_HEADER = 0x45786966;
    private static final short TIFF_HEADER = 0x002A;
    private static final short TIFF_BIG_ENDIAN = 0x4d4d;
    private static final short TIFF_LITTLE_ENDIAN = 0x4949;
    private static final short TAG_SIZE = 12;
    private static final short TIFF_HEADER_SIZE = 8;

    private ExifData mExifData;
    private int mState = STATE_SOI;
    private int mByteToSkip;
    private int mByteToCopy;
    private ByteBuffer mBuffer = ByteBuffer.allocate(4);

    public ExifOutputStream(OutputStream ou) {
        super(ou);
    }

    public void setExifData(ExifData exifData) {
        mExifData = exifData;
    }

    public ExifData getExifData() {
        return mExifData;
    }

    private int requestByteToBuffer(int requestByteCount, byte[] buffer
            , int offset, int length) {
        int byteNeeded = requestByteCount - mBuffer.position();
        int byteToRead = length > byteNeeded ? byteNeeded : length;
        mBuffer.put(buffer, offset, byteToRead);
        return byteToRead;
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        while((mByteToSkip > 0 || mByteToCopy > 0 || mState != STATE_JPEG_DATA)
                && length > 0) {
            if (mByteToSkip > 0) {
                int byteToProcess = length > mByteToSkip ? mByteToSkip : length;
                length -= byteToProcess;
                mByteToSkip -= byteToProcess;
                offset += byteToProcess;
            }
            if (mByteToCopy > 0) {
                int byteToProcess = length > mByteToCopy ? mByteToCopy : length;
                out.write(buffer, offset, byteToProcess);
                length -= byteToProcess;
                mByteToCopy -= byteToProcess;
                offset += byteToProcess;
            }
            if (length == 0) return;
            switch (mState) {
                case STATE_SOI:
                    int byteRead = requestByteToBuffer(2, buffer, offset, length);
                    offset += byteRead;
                    length -= byteRead;
                    if (mBuffer.position() < 2) return;
                    mBuffer.rewind();
                    assert(mBuffer.getShort() == JpegHeader.SOI);
                    out.write(mBuffer.array(), 0 ,2);
                    mState = STATE_FRAME_HEADER;
                    mBuffer.rewind();
                    writeExifData();
                    break;
                case STATE_FRAME_HEADER:
                    // We ignore the APP1 segment and copy all other segments until SOF tag.
                    byteRead = requestByteToBuffer(4, buffer, offset, length);
                    offset += byteRead;
                    length -= byteRead;
                    // Check if this image data doesn't contain SOF.
                    if (mBuffer.position() == 2) {
                        short tag = mBuffer.getShort();
                        if (tag == JpegHeader.EOI) {
                            out.write(mBuffer.array(), 0, 2);
                            mBuffer.rewind();
                        }
                    }
                    if (mBuffer.position() < 4) return;
                    mBuffer.rewind();
                    short marker = mBuffer.getShort();
                    if (marker == JpegHeader.APP1) {
                        mByteToSkip = (mBuffer.getShort() & 0xff) - 2;
                        mState = STATE_JPEG_DATA;
                    } else if (!JpegHeader.isSofMarker(marker)) {
                        out.write(mBuffer.array(), 0, 4);
                        mByteToCopy = (mBuffer.getShort() & 0xff) - 2;
                    } else {
                        out.write(mBuffer.array(), 0, 4);
                        mState = STATE_JPEG_DATA;
                    }
                    mBuffer.rewind();
            }
        }
        if (length > 0) {
            out.write(buffer, offset, length);
        }
    }

    @Override
    public void write(int oneByte) throws IOException {
        byte[] buf = new byte[] {(byte) (0xff & oneByte)};
        write(buf);
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    private void writeExifData() throws IOException {
        createRequiredIfdAndTag();
        int exifSize = calculateAllOffset();
        OrderedDataOutputStream dataOutputStream = new OrderedDataOutputStream(out);
        dataOutputStream.setByteOrder(ByteOrder.BIG_ENDIAN);
        dataOutputStream.writeShort(JpegHeader.APP1);
        dataOutputStream.writeShort((short) (exifSize + 8));
        dataOutputStream.writeInt(EXIF_HEADER);
        dataOutputStream.writeShort((short) 0x0000);
        if (mExifData.getByteOrder() == ByteOrder.BIG_ENDIAN) {
            dataOutputStream.writeShort(TIFF_BIG_ENDIAN);
        } else {
            dataOutputStream.writeShort(TIFF_LITTLE_ENDIAN);
        }
        dataOutputStream.setByteOrder(mExifData.getByteOrder());
        dataOutputStream.writeShort(TIFF_HEADER);
        dataOutputStream.writeInt(8);
        writeAllTags(dataOutputStream);
        writeThumbnail(dataOutputStream);
    }

    private void writeThumbnail(OrderedDataOutputStream dataOutputStream) throws IOException {
        if (mExifData.hasCompressedThumbnail()) {
            dataOutputStream.write(mExifData.getCompressedThumbnail());
        } else if (mExifData.hasUncompressedStrip()) {
            for (int i = 0; i < mExifData.getStripCount(); i++) {
                dataOutputStream.write(mExifData.getStrip(i));
            }
        }
    }

    private void writeAllTags(OrderedDataOutputStream dataOutputStream) throws IOException {
        writeIfd(mExifData.getIfdData(IfdId.TYPE_IFD_0), dataOutputStream);
        writeIfd(mExifData.getIfdData(IfdId.TYPE_IFD_EXIF), dataOutputStream);
        IfdData interoperabilityIfd = mExifData.getIfdData(IfdId.TYPE_IFD_INTEROPERABILITY);
        if (interoperabilityIfd != null) {
            writeIfd(interoperabilityIfd, dataOutputStream);
        }
        IfdData gpsIfd = mExifData.getIfdData(IfdId.TYPE_IFD_GPS);
        if (gpsIfd != null) {
            writeIfd(gpsIfd, dataOutputStream);
        }
        IfdData ifd1 = mExifData.getIfdData(IfdId.TYPE_IFD_1);
        if (ifd1 != null) {
            writeIfd(mExifData.getIfdData(IfdId.TYPE_IFD_1), dataOutputStream);
        }
    }

    private void writeIfd(IfdData ifd, OrderedDataOutputStream dataOutputStream)
            throws IOException {
        ExifTag[] tags = ifd.getAllTags();
        dataOutputStream.writeShort((short) tags.length);
        for (ExifTag tag: tags) {
            dataOutputStream.writeShort(tag.getTagId());
            dataOutputStream.writeShort(tag.getDataType());
            dataOutputStream.writeInt(tag.getComponentCount());
            if (tag.getDataSize() > 4) {
                dataOutputStream.writeInt(tag.getOffset());
            } else {
                writeTagValue(tag, dataOutputStream);
                for (int i = 0, n = 4 - tag.getDataSize(); i < n; i++) {
                    dataOutputStream.write(0);
                }
            }
        }
        dataOutputStream.writeInt(ifd.getOffsetToNextIfd());
        for (ExifTag tag: tags) {
            if (tag.getDataSize() > 4) {
                writeTagValue(tag, dataOutputStream);
            }
        }
    }

    private void writeTagValue(ExifTag tag, OrderedDataOutputStream dataOutputStream)
            throws IOException {
        switch (tag.getDataType()) {
            case ExifTag.TYPE_ASCII:
                dataOutputStream.write(tag.getString().getBytes());
                int remain = tag.getComponentCount() - tag.getString().length();
                for (int i = 0; i < remain; i++) {
                    dataOutputStream.write(0);
                }
                break;
            case ExifTag.TYPE_LONG:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    dataOutputStream.writeInt(tag.getLong(i));
                }
                break;
            case ExifTag.TYPE_RATIONAL:
            case ExifTag.TYPE_UNSIGNED_RATIONAL:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    dataOutputStream.writeRational(tag.getRational(i));
                }
                break;
            case ExifTag.TYPE_UNDEFINED:
            case ExifTag.TYPE_UNSIGNED_BYTE:
                byte[] buf = new byte[tag.getComponentCount()];
                tag.getBytes(buf);
                dataOutputStream.write(buf);
                break;
            case ExifTag.TYPE_UNSIGNED_LONG:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    dataOutputStream.writeInt((int) tag.getUnsignedLong(i));
                }
                break;
            case ExifTag.TYPE_UNSIGNED_SHORT:
                for (int i = 0, n = tag.getComponentCount(); i < n; i++) {
                    dataOutputStream.writeShort((short) tag.getUnsignedShort(i));
                }
                break;
        }
    }

    private int calculateOffsetOfIfd(IfdData ifd, int offset) {
        offset += 2 + ifd.getTagCount() * TAG_SIZE + 4;
        ExifTag[] tags = ifd.getAllTags();
        for(ExifTag tag: tags) {
            if (tag.getDataSize() > 4) {
                tag.setOffset(offset);
                offset += tag.getDataSize();
            }
        }
        return offset;
    }

    private void createRequiredIfdAndTag() {
        // IFD0 is required for all file
        IfdData ifd0 = mExifData.getIfdData(IfdId.TYPE_IFD_0);
        if (ifd0 == null) {
            ifd0 = new IfdData(IfdId.TYPE_IFD_0);
            mExifData.addIfdData(ifd0);
        }
        ExifTag exifOffsetTag = new ExifTag(ExifTag.TAG_EXIF_IFD,
                ExifTag.TYPE_UNSIGNED_LONG, 1, IfdId.TYPE_IFD_0);
        ifd0.setTag(exifOffsetTag);

        // Exif IFD is required for all file.
        IfdData exifIfd = mExifData.getIfdData(IfdId.TYPE_IFD_EXIF);
        if (exifIfd == null) {
            exifIfd = new IfdData(IfdId.TYPE_IFD_EXIF);
            mExifData.addIfdData(exifIfd);
        }

        // GPS IFD
        IfdData gpsIfd = mExifData.getIfdData(IfdId.TYPE_IFD_GPS);
        if (gpsIfd != null) {
            ExifTag gpsOffsetTag = new ExifTag(ExifTag.TAG_GPS_IFD,
                    ExifTag.TYPE_UNSIGNED_LONG, 1, IfdId.TYPE_IFD_0);
            ifd0.setTag(gpsOffsetTag);
        }

        // Interoperability IFD
        IfdData interIfd = mExifData.getIfdData(IfdId.TYPE_IFD_INTEROPERABILITY);
        if (interIfd != null) {
            ExifTag interOffsetTag = new ExifTag(ExifTag.TAG_INTEROPERABILITY_IFD,
                    ExifTag.TYPE_UNSIGNED_LONG, 1, IfdId.TYPE_IFD_EXIF);
            exifIfd.setTag(interOffsetTag);
        }

        IfdData ifd1 = mExifData.getIfdData(IfdId.TYPE_IFD_1);

        // thumbnail
        if (mExifData.hasCompressedThumbnail()) {
            if (ifd1 == null) {
                ifd1 = new IfdData(IfdId.TYPE_IFD_1);
                mExifData.addIfdData(ifd1);
            }
            ExifTag offsetTag = new ExifTag(ExifTag.TAG_JPEG_INTERCHANGE_FORMAT,
                    ExifTag.TYPE_UNSIGNED_LONG, 1, IfdId.TYPE_IFD_1);
            ifd1.setTag(offsetTag);
            ExifTag lengthTag = new ExifTag(ExifTag.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
                    ExifTag.TYPE_UNSIGNED_LONG, 1, IfdId.TYPE_IFD_1);
            lengthTag.setValue(mExifData.getCompressedThumbnail().length);
            ifd1.setTag(lengthTag);
        } else if (mExifData.hasUncompressedStrip()){
            if (ifd1 == null) {
                ifd1 = new IfdData(IfdId.TYPE_IFD_1);
                mExifData.addIfdData(ifd1);
            }
            int stripCount = mExifData.getStripCount();
            ExifTag offsetTag = new ExifTag(ExifTag.TAG_STRIP_OFFSETS,
                    ExifTag.TYPE_UNSIGNED_LONG, stripCount, IfdId.TYPE_IFD_1);
            ExifTag lengthTag = new ExifTag(ExifTag.TAG_STRIP_BYTE_COUNTS,
                    ExifTag.TYPE_UNSIGNED_LONG, stripCount, IfdId.TYPE_IFD_1);
            long[] lengths = new long[stripCount];
            for (int i = 0; i < mExifData.getStripCount(); i++) {
                lengths[i] = mExifData.getStrip(i).length;
            }
            lengthTag.setValue(lengths);
            ifd1.setTag(offsetTag);
            ifd1.setTag(lengthTag);
        }
    }

    private int calculateAllOffset() {
        int offset = TIFF_HEADER_SIZE;
        IfdData ifd0 = mExifData.getIfdData(IfdId.TYPE_IFD_0);
        offset = calculateOffsetOfIfd(ifd0, offset);
        ifd0.getTag(ExifTag.TAG_EXIF_IFD).setValue(offset);

        IfdData exifIfd = mExifData.getIfdData(IfdId.TYPE_IFD_EXIF);
        offset = calculateOffsetOfIfd(exifIfd, offset);

        IfdData interIfd = mExifData.getIfdData(IfdId.TYPE_IFD_INTEROPERABILITY);
        if (interIfd != null) {
            exifIfd.getTag(ExifTag.TAG_INTEROPERABILITY_IFD).setValue(offset);
            offset = calculateOffsetOfIfd(interIfd, offset);
        }

        IfdData gpsIfd = mExifData.getIfdData(IfdId.TYPE_IFD_GPS);
        if (gpsIfd != null) {
            ifd0.getTag(ExifTag.TAG_GPS_IFD).setValue(offset);
            offset = calculateOffsetOfIfd(gpsIfd, offset);
        }

        IfdData ifd1 = mExifData.getIfdData(IfdId.TYPE_IFD_1);
        if (ifd1 != null) {
            ifd0.setOffsetToNextIfd(offset);
            offset = calculateOffsetOfIfd(ifd1, offset);
        }

        // thumbnail
        if (mExifData.hasCompressedThumbnail()) {
            ifd1.getTag(ExifTag.TAG_JPEG_INTERCHANGE_FORMAT).setValue(offset);
            offset += mExifData.getCompressedThumbnail().length;
        } else if (mExifData.hasUncompressedStrip()){
            int stripCount = mExifData.getStripCount();
            long[] offsets = new long[stripCount];
            for (int i = 0; i < mExifData.getStripCount(); i++) {
                offsets[i] = offset;
                offset += mExifData.getStrip(i).length;
            }
            ifd1.getTag(ExifTag.TAG_STRIP_OFFSETS).setValue(offsets);
        }
        return offset;
    }
}