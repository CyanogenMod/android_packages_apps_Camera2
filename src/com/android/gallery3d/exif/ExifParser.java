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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * This class provides a low-level EXIF parsing API. Given a JPEG format InputStream, the caller
 * can request which IFD's to read via {@link #parse(InputStream, int)} with given options.
 * <p>
 * Below is an example of getting EXIF data from IFD 0 and EXIF IFD using the parser.
 * <pre>
 * void parse() {
 *     ExifParser parser = ExifParser.parse(mImageInputStream,
 *             ExifParser.OPTION_IFD_0 | ExifParser.OPTIONS_IFD_EXIF);
 *     int event = parser.next();
 *     while (event != ExifParser.EVENT_END) {
 *         switch (event) {
 *             case ExifParser.EVENT_START_OF_IFD:
 *                 break;
 *             case ExifParser.EVENT_NEW_TAG:
 *                 ExifTag tag = parser.getTag();
 *                 if (!tag.hasValue()) {
 *                     parser.registerForTagValue(tag);
 *                 } else {
 *                     processTag(tag);
 *                 }
 *                 break;
 *             case ExifParser.EVENT_VALUE_OF_REGISTERED_TAG:
 *                 tag = parser.getTag();
 *                 if (tag.getDataType() != ExifTag.TYPE_UNDEFINED) {
 *                     processTag(tag);
 *                 }
 *                 break;
 *         }
 *         event = parser.next();
 *     }
 * }
 *
 * void processTag(ExifTag tag) {
 *     // process the tag as you like.
 * }
 * </pre>
 */
public class ExifParser {
    /**
     * When the parser reaches a new IFD area. Call
     *  {@link #getCurrentIfd()} to know which IFD we are in.
     */
    public static final int EVENT_START_OF_IFD = 0;
    /**
     * When the parser reaches a new tag. Call {@link #getTag()}to get the
     * corresponding tag.
     */
    public static final int EVENT_NEW_TAG = 1;
    /**
     * When the parser reaches the value area of tag that is registered by
     *  {@link #registerForTagValue(ExifTag)} previously. Call
     *  {@link #getTag()} to get the corresponding tag.
     */
    public static final int EVENT_VALUE_OF_REGISTERED_TAG = 2;

    /**
     * When the parser reaches the compressed image area.
     */
    public static final int EVENT_COMPRESSED_IMAGE = 3;
    /**
     * When the parser reaches the uncompressed image strip.
     *  Call {@link #getStripIndex()} to get the index of the strip.
     * @see #getStripIndex()
     * @see #getStripCount()
     */
    public static final int EVENT_UNCOMPRESSED_STRIP = 4;
    /**
     * When there is nothing more to parse.
     */
    public static final int EVENT_END = 5;

    /**
     * Option bit to request to parse IFD0.
     */
    public static final int OPTION_IFD_0 = 1 << 0;
    /**
     * Option bit to request to parse IFD1.
     */
    public static final int OPTION_IFD_1 = 1 << 1;
    /**
     * Option bit to request to parse Exif-IFD.
     */
    public static final int OPTION_IFD_EXIF = 1 << 2;
    /**
     * Option bit to request to parse GPS-IFD.
     */
    public static final int OPTION_IFD_GPS = 1 << 3;
    /**
     * Option bit to request to parse Interoperability-IFD.
     */
    public static final int OPTION_IFD_INTEROPERABILITY = 1 << 4;
    /**
     * Option bit to request to parse thumbnail.
     */
    public static final int OPTION_THUMBNAIL = 1 << 5;

    private static final int EXIF_HEADER = 0x45786966; // EXIF header "Exif"
    private static final short EXIF_HEADER_TAIL = (short) 0x0000; // EXIF header in APP1

    // TIFF header
    private static final short LITTLE_ENDIAN_TAG = (short) 0x4949; // "II"
    private static final short BIG_ENDIAN_TAG = (short) 0x4d4d; // "MM"
    private static final short TIFF_HEADER_TAIL = 0x002A;

    private static final int TAG_SIZE = 12;
    private static final int OFFSET_SIZE = 2;

    private final CountedDataInputStream mTiffStream;
    private final int mOptions;
    private int mIfdStartOffset = 0;
    private int mNumOfTagInIfd = 0;
    private int mIfdType;
    private ExifTag mTag;
    private ImageEvent mImageEvent;
    private int mStripCount;
    private ExifTag mStripSizeTag;
    private ExifTag mJpegSizeTag;
    private boolean mNeedToParseOffsetsInCurrentIfd;
    private boolean mContainExifData = false;

    private final TreeMap<Integer, Object> mCorrespondingEvent = new TreeMap<Integer, Object>();

    private boolean isIfdRequested(int ifdType) {
        switch (ifdType) {
            case IfdId.TYPE_IFD_0:
                return (mOptions & OPTION_IFD_0) != 0;
            case IfdId.TYPE_IFD_1:
                return (mOptions & OPTION_IFD_1) != 0;
            case IfdId.TYPE_IFD_EXIF:
                return (mOptions & OPTION_IFD_EXIF) != 0;
            case IfdId.TYPE_IFD_GPS:
                return (mOptions & OPTION_IFD_GPS) != 0;
            case IfdId.TYPE_IFD_INTEROPERABILITY:
                return (mOptions & OPTION_IFD_INTEROPERABILITY) != 0;
        }
        return false;
    }

    private boolean isThumbnailRequested() {
        return (mOptions & OPTION_THUMBNAIL) != 0;
    }

    private ExifParser(InputStream inputStream, int options)
            throws IOException, ExifInvalidFormatException {
        mContainExifData = seekTiffData(inputStream);
        mTiffStream = new CountedDataInputStream(inputStream);
        mOptions = options;
        if (!mContainExifData) return;
        if (mTiffStream.getReadByteCount() == 0) {
            parseTiffHeader();
            long offset = mTiffStream.readUnsignedInt();
            registerIfd(IfdId.TYPE_IFD_0, offset);
        }
    }

    /**
     * Parses the the given InputStream with the given options
     * @exception IOException
     * @exception ExifInvalidFormatException
     */
    public static ExifParser parse(InputStream inputStream, int options)
             throws IOException, ExifInvalidFormatException {
         return new ExifParser(inputStream, options);
    }

    /**
     * Parses the the given InputStream with default options; that is, every IFD and thumbnaill
     * will be parsed.
     * @exception IOException
     * @exception ExifInvalidFormatException
     * @see #parse(InputStream, int)
     */
    public static ExifParser parse(InputStream inputStream)
            throws IOException, ExifInvalidFormatException {
        return new ExifParser(inputStream, OPTION_IFD_0 | OPTION_IFD_1
                | OPTION_IFD_EXIF | OPTION_IFD_GPS | OPTION_IFD_INTEROPERABILITY
                | OPTION_THUMBNAIL);
    }

    /**
     * Moves the parser forward and returns the next parsing event
     *
     * @exception IOException
     * @exception ExifInvalidFormatException
     * @see #EVENT_START_OF_IFD
     * @see #EVENT_NEW_TAG
     * @see #EVENT_VALUE_OF_REGISTERED_TAG
     * @see #EVENT_COMPRESSED_IMAGE
     * @see #EVENT_UNCOMPRESSED_STRIP
     * @see #EVENT_END
     */
    public int next() throws IOException, ExifInvalidFormatException {
        if (!mContainExifData) {
            return EVENT_END;
        }
        int offset = mTiffStream.getReadByteCount();
        int endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * mNumOfTagInIfd;
        if (offset < endOfTags) {
            mTag = readTag();
            if (mNeedToParseOffsetsInCurrentIfd) {
                checkOffsetOrImageTag(mTag);
            }
            return EVENT_NEW_TAG;
        } else if (offset == endOfTags) {
            long ifdOffset = readUnsignedLong();
            // There is a link to ifd1 at the end of ifd0
            if (mIfdType == IfdId.TYPE_IFD_0) {
                if (isIfdRequested(IfdId.TYPE_IFD_1) || isThumbnailRequested()) {
                    if (ifdOffset != 0) {
                        registerIfd(IfdId.TYPE_IFD_1, ifdOffset);
                    }
                }
            } else {
                if (ifdOffset != 0) {
                    throw new ExifInvalidFormatException("Invalid link to next IFD");
                }
            }
        }
        while(mCorrespondingEvent.size() != 0) {
            Entry<Integer, Object> entry = mCorrespondingEvent.pollFirstEntry();
            Object event = entry.getValue();
            skipTo(entry.getKey());
            if (event instanceof IfdEvent) {
                mIfdType = ((IfdEvent) event).ifd;
                mNumOfTagInIfd = mTiffStream.readUnsignedShort();
                mIfdStartOffset = entry.getKey();
                mNeedToParseOffsetsInCurrentIfd = needToParseOffsetsInCurrentIfd();
                if (((IfdEvent) event).isRequested) {
                    return EVENT_START_OF_IFD;
                } else {
                    skipRemainingTagsInCurrentIfd();
                }
            } else if (event instanceof ImageEvent) {
                mImageEvent = (ImageEvent) event;
                return mImageEvent.type;
            } else {
                ExifTagEvent tagEvent = (ExifTagEvent) event;
                mTag = tagEvent.tag;
                if (mTag.getDataType() != ExifTag.TYPE_UNDEFINED) {
                    readFullTagValue(mTag);
                    checkOffsetOrImageTag(mTag);
                }
                if (tagEvent.isRequested) {
                    return EVENT_VALUE_OF_REGISTERED_TAG;
                }
            }
        }
        return EVENT_END;
    }

    /**
     * Skips the tags area of current IFD, if the parser is not in the tag area, nothing will
     * happen.
     *
     * @throws IOException
     * @throws ExifInvalidFormatException
     */
    public void skipRemainingTagsInCurrentIfd() throws IOException, ExifInvalidFormatException {
        int endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * mNumOfTagInIfd;
        int offset = mTiffStream.getReadByteCount();
        if (offset > endOfTags) return;
        if (mNeedToParseOffsetsInCurrentIfd) {
            while (offset < endOfTags) {
                mTag = readTag();
                checkOffsetOrImageTag(mTag);
                offset += TAG_SIZE;
            }
        } else {
            skipTo(endOfTags);
        }
        long ifdOffset = readUnsignedLong();
        // For ifd0, there is a link to ifd1 in the end of all tags
        if (mIfdType == IfdId.TYPE_IFD_0
                && (isIfdRequested(IfdId.TYPE_IFD_1) || isThumbnailRequested())) {
            if (ifdOffset > 0) {
                registerIfd(IfdId.TYPE_IFD_1, ifdOffset);
            }
        }
    }

    private boolean needToParseOffsetsInCurrentIfd() {
        switch (mIfdType) {
            case IfdId.TYPE_IFD_0:
                return isIfdRequested(IfdId.TYPE_IFD_EXIF) || isIfdRequested(IfdId.TYPE_IFD_GPS)
                        || isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY);
            case IfdId.TYPE_IFD_1:
                return isThumbnailRequested();
            case IfdId.TYPE_IFD_EXIF:
                // The offset to interoperability IFD is located in Exif IFD
                return isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY);
            default:
                return false;
        }
    }

    /**
     * If {@link #next()} return {@link #EVENT_NEW_TAG} or {@link #EVENT_VALUE_OF_REGISTERED_TAG},
     * call this function to get the corresponding tag.
     * <p>
     *
     * For {@link #EVENT_NEW_TAG}, the tag may not contain the value if the size of the value is
     * greater than 4 bytes. One should call {@link ExifTag#hasValue()} to check if the tag
     * contains value.
     * If there is no value,call {@link #registerForTagValue(ExifTag)} to have the parser emit
     * {@link #EVENT_VALUE_OF_REGISTERED_TAG} when it reaches the area pointed by the offset.
     *
     * <p>
     * When {@link #EVENT_VALUE_OF_REGISTERED_TAG} is emitted, the value of the tag will have
     * already been read except for tags of undefined type. For tags of undefined type, call
     * one of the read methods to get the value.
     *
     * @see #registerForTagValue(ExifTag)
     * @see #read(byte[])
     * @see #read(byte[], int, int)
     * @see #readLong()
     * @see #readRational()
     * @see #readShort()
     * @see #readString(int)
     * @see #readString(int, Charset)
     */
    public ExifTag getTag() {
        return mTag;
    }

    /**
     * Gets number of tags in the current IFD area.
     */
    public int getTagCountInCurrentIfd() {
        return mNumOfTagInIfd;
    }

    /**
     * Gets the ID of current IFD.
     *
     * @see IfdId#TYPE_IFD_0
     * @see IfdId#TYPE_IFD_1
     * @see IfdId#TYPE_IFD_GPS
     * @see IfdId#TYPE_IFD_INTEROPERABILITY
     * @see IfdId#TYPE_IFD_EXIF
     */
    public int getCurrentIfd() {
        return mIfdType;
    }

    /**
     * When receiving {@link #EVENT_UNCOMPRESSED_STRIP},
     * call this function to get the index of this strip.
     * @see #getStripCount()
     */
    public int getStripIndex() {
        return mImageEvent.stripIndex;
    }

    /**
     * When receiving {@link #EVENT_UNCOMPRESSED_STRIP}, call this function to get the number
     * of strip data.
     * @see #getStripIndex()
     */
    public int getStripCount() {
        return mStripCount;
    }

    /**
     * When receiving {@link #EVENT_UNCOMPRESSED_STRIP}, call this function to get the strip size.
     */
    public int getStripSize() {
        if (mStripSizeTag == null) return 0;
        if (mStripSizeTag.getDataType() == ExifTag.TYPE_UNSIGNED_SHORT) {
            return mStripSizeTag.getUnsignedShort(mImageEvent.stripIndex);
        } else {
            // Cast unsigned int to int since the strip size is always smaller
            // than the size of APP1 (65536)
            return (int) mStripSizeTag.getUnsignedLong(mImageEvent.stripIndex);
        }
    }

    /**
     * When receiving {@link #EVENT_COMPRESSED_IMAGE}, call this function to get the image data
     * size.
     */
    public int getCompressedImageSize() {
        if (mJpegSizeTag == null) return 0;
        // Cast unsigned int to int since the thumbnail is always smaller
        // than the size of APP1 (65536)
        return (int) mJpegSizeTag.getUnsignedLong(0);
    }

    private void skipTo(int offset) throws IOException {
        mTiffStream.skipTo(offset);
        while (!mCorrespondingEvent.isEmpty() && mCorrespondingEvent.firstKey() < offset) {
            mCorrespondingEvent.pollFirstEntry();
        }
    }

    /**
     * When getting {@link #EVENT_NEW_TAG} in the tag area of IFD,
     * the tag may not contain the value if the size of the value is greater than 4 bytes.
     * When the value is not available here, call this method so that the parser will emit
     * {@link #EVENT_VALUE_OF_REGISTERED_TAG} when it reaches the area where the value is located.

     * @see #EVENT_VALUE_OF_REGISTERED_TAG
     */
    public void registerForTagValue(ExifTag tag) {
        mCorrespondingEvent.put(tag.getOffset(), new ExifTagEvent(tag, true));
    }

    private void registerIfd(int ifdType, long offset) {
        // Cast unsigned int to int since the offset is always smaller
        // than the size of APP1 (65536)
        mCorrespondingEvent.put((int) offset, new IfdEvent(ifdType, isIfdRequested(ifdType)));
    }

    private void registerCompressedImage(long offset) {
        mCorrespondingEvent.put((int) offset, new ImageEvent(EVENT_COMPRESSED_IMAGE));
    }

    private void registerUncompressedStrip(int stripIndex, long offset) {
        mCorrespondingEvent.put((int) offset, new ImageEvent(EVENT_UNCOMPRESSED_STRIP
                , stripIndex));
    }

    private ExifTag readTag() throws IOException, ExifInvalidFormatException {
        short tagId = mTiffStream.readShort();
        short dataFormat = mTiffStream.readShort();
        long numOfComp = mTiffStream.readUnsignedInt();
        if (numOfComp > Integer.MAX_VALUE) {
            throw new ExifInvalidFormatException(
                    "Number of component is larger then Integer.MAX_VALUE");
        }
        ExifTag tag = new ExifTag(tagId, dataFormat, (int) numOfComp, mIfdType);
        int dataSize = tag.getDataSize();
        if (dataSize > 4) {
            long offset = mTiffStream.readUnsignedInt();
            if (offset > Integer.MAX_VALUE) {
                throw new ExifInvalidFormatException(
                        "offset is larger then Integer.MAX_VALUE");
            }
            tag.setOffset((int) offset);
        } else {
            readFullTagValue(tag);
            mTiffStream.skip(4 - dataSize);
        }
        return tag;
    }

    /**
     * Check the tag, if the tag is one of the offset tag that points to the IFD or image the
     * caller is interested in, register the IFD or image.
     */
    private void checkOffsetOrImageTag(ExifTag tag) {
        switch (tag.getTagId()) {
            case ExifTag.TAG_EXIF_IFD:
                if (isIfdRequested(IfdId.TYPE_IFD_EXIF)
                        || isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)) {
                    registerIfd(IfdId.TYPE_IFD_EXIF, tag.getUnsignedLong(0));
                }
                break;
            case ExifTag.TAG_GPS_IFD:
                if (isIfdRequested(IfdId.TYPE_IFD_GPS)) {
                    registerIfd(IfdId.TYPE_IFD_GPS, tag.getUnsignedLong(0));
                }
                break;
            case ExifTag.TAG_INTEROPERABILITY_IFD:
                if (isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)) {
                    registerIfd(IfdId.TYPE_IFD_INTEROPERABILITY, tag.getUnsignedLong(0));
                }
                break;
            case ExifTag.TAG_JPEG_INTERCHANGE_FORMAT:
                if (isThumbnailRequested()) {
                    registerCompressedImage(tag.getUnsignedLong(0));
                }
                break;
            case ExifTag.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH:
                if (isThumbnailRequested()) {
                    mJpegSizeTag = tag;
                }
                break;
            case ExifTag.TAG_STRIP_OFFSETS:
                if (isThumbnailRequested()) {
                    if (tag.hasValue()) {
                        for (int i = 0; i < tag.getComponentCount(); i++) {
                            if (tag.getDataType() == ExifTag.TYPE_UNSIGNED_SHORT) {
                                registerUncompressedStrip(i, tag.getUnsignedShort(i));
                            } else {
                                registerUncompressedStrip(i, tag.getUnsignedLong(i));
                            }
                        }
                    } else {
                        mCorrespondingEvent.put(tag.getOffset(), new ExifTagEvent(tag, false));
                    }
                }
                break;
            case ExifTag.TAG_STRIP_BYTE_COUNTS:
                if (isThumbnailRequested()) {
                    if (tag.hasValue()) {
                        mStripSizeTag = tag;
                    }
                }
                break;
        }
    }

    private void readFullTagValue(ExifTag tag) throws IOException {
        switch(tag.getDataType()) {
            case ExifTag.TYPE_UNSIGNED_BYTE:
            case ExifTag.TYPE_UNDEFINED:
                {
                    byte buf[] = new byte[tag.getComponentCount()];
                    read(buf);
                    tag.setValue(buf);
                }
                break;
            case ExifTag.TYPE_ASCII:
                tag.setValue(readString(tag.getComponentCount()));
                break;
            case ExifTag.TYPE_UNSIGNED_LONG:
                {
                    long value[] = new long[tag.getComponentCount()];
                    for (int i = 0, n = value.length; i < n; i++) {
                        value[i] = readUnsignedLong();
                    }
                    tag.setValue(value);
                }
                break;
          case ExifTag.TYPE_UNSIGNED_RATIONAL:
              {
                  Rational value[] = new Rational[tag.getComponentCount()];
                  for (int i = 0, n = value.length; i < n; i++) {
                      value[i] = readUnsignedRational();
                  }
                  tag.setValue(value);
              }
              break;
          case ExifTag.TYPE_UNSIGNED_SHORT:
              {
                  int value[] = new int[tag.getComponentCount()];
                  for (int i = 0, n = value.length; i < n; i++) {
                      value[i] = readUnsignedShort();
                  }
                  tag.setValue(value);
              }
              break;
          case ExifTag.TYPE_LONG:
              {
                  int value[] = new int[tag.getComponentCount()];
                  for (int i = 0, n = value.length; i < n; i++) {
                      value[i] = readLong();
                  }
                  tag.setValue(value);
              }
              break;
          case ExifTag.TYPE_RATIONAL:
              {
                  Rational value[] = new Rational[tag.getComponentCount()];
                  for (int i = 0, n = value.length; i < n; i++) {
                      value[i] = readRational();
                  }
                  tag.setValue(value);
              }
              break;
        }
    }

    private void parseTiffHeader() throws IOException,
            ExifInvalidFormatException {
        short byteOrder = mTiffStream.readShort();
        ByteOrder order;
        if (LITTLE_ENDIAN_TAG == byteOrder) {
            mTiffStream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        } else if (BIG_ENDIAN_TAG == byteOrder) {
            mTiffStream.setByteOrder(ByteOrder.BIG_ENDIAN);
        } else {
            throw new ExifInvalidFormatException("Invalid TIFF header");
        }

        if (mTiffStream.readShort() != TIFF_HEADER_TAIL) {
            throw new ExifInvalidFormatException("Invalid TIFF header");
        }
    }

    private boolean seekTiffData(InputStream inputStream) throws IOException,
            ExifInvalidFormatException {
        DataInputStream dataStream = new DataInputStream(inputStream);

        // SOI and APP1
        if (dataStream.readShort() != JpegHeader.SOI) {
            throw new ExifInvalidFormatException("Invalid JPEG format");
        }

        short marker = dataStream.readShort();
        while(marker != JpegHeader.APP1 && marker != JpegHeader.EOI
                && !JpegHeader.isSofMarker(marker)) {
            int length = dataStream.readUnsignedShort();
            if ((length - 2) != dataStream.skip(length - 2)) {
                throw new EOFException();
            }
            marker = dataStream.readShort();
        }

        if (marker != JpegHeader.APP1) return false; // No APP1 segment

        // APP1 length, it's not used for us
        dataStream.readShort();

        // Exif header
        return (dataStream.readInt() == EXIF_HEADER
                && dataStream.readShort() == EXIF_HEADER_TAIL);
    }

    /**
     * Reads bytes from the InputStream.
     */
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return mTiffStream.read(buffer, offset, length);
    }

    /**
     * Equivalent to read(buffer, 0, buffer.length).
     */
    public int read(byte[] buffer) throws IOException {
        return mTiffStream.read(buffer);
    }

    /**
     * Reads a String from the InputStream with UTF8 charset.
     * This is used for reading values of type {@link ExifTag#TYPE_ASCII}.
     */
    public String readString(int n) throws IOException {
        if (n > 0) {
            byte[] buf = new byte[n];
            mTiffStream.readOrThrow(buf);
            return new String(buf, 0, n - 1, "UTF8");
        } else {
            return "";
        }
    }

    /**
     * Reads a String from the InputStream with the given charset.
     * This is used for reading values of type {@link ExifTag#TYPE_ASCII}.
     */
    public String readString(int n, Charset charset) throws IOException {
        byte[] buf = new byte[n];
        mTiffStream.readOrThrow(buf);
        return new String(buf, 0, n - 1, charset);
    }

    /**
     * Reads value of type {@link ExifTag#TYPE_UNSIGNED_SHORT} from the InputStream.
     */
    public int readUnsignedShort() throws IOException {
        return mTiffStream.readShort() & 0xffff;
    }

    /**
     * Reads value of type {@link ExifTag#TYPE_UNSIGNED_LONG} from the InputStream.
     */
    public long readUnsignedLong() throws IOException {
        return readLong() & 0xffffffffL;
    }

    /**
     * Reads value of type {@link ExifTag#TYPE_UNSIGNED_RATIONAL} from the InputStream.
     */
    public Rational readUnsignedRational() throws IOException {
        long nomi = readUnsignedLong();
        long denomi = readUnsignedLong();
        return new Rational(nomi, denomi);
    }

    /**
     * Reads value of type {@link ExifTag#TYPE_LONG} from the InputStream.
     */
    public int readLong() throws IOException {
        return mTiffStream.readInt();
    }

    /**
     * Reads value of type {@link ExifTag#TYPE_RATIONAL} from the InputStream.
     */
    public Rational readRational() throws IOException {
        int nomi = readLong();
        int denomi = readLong();
        return new Rational(nomi, denomi);
    }

    private static class ImageEvent {
        int stripIndex;
        int type;
        ImageEvent(int type) {
            this.stripIndex = 0;
            this.type = type;
        }
        ImageEvent(int type, int stripIndex) {
            this.type = type;
            this.stripIndex = stripIndex;
        }
    }

    private static class IfdEvent {
        int ifd;
        boolean isRequested;
        IfdEvent(int ifd, boolean isInterestedIfd) {
            this.ifd = ifd;
            this.isRequested = isInterestedIfd;
        }
    }

    private static class ExifTagEvent {
        ExifTag tag;
        boolean isRequested;
        ExifTagEvent(ExifTag tag, boolean isRequireByUser) {
            this.tag = tag;
            this.isRequested = isRequireByUser;
        }
    }

    /**
     * Gets the byte order of the current InputStream.
     */
    public ByteOrder getByteOrder() {
        return mTiffStream.getByteOrder();
    }
}