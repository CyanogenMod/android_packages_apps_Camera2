/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExifInterfaceTest extends ExifXmlDataTestCase {

    private File mTmpFile;
    private List<Map<Short, List<String>>> mGroundTruth;
    private ExifInterface mInterface;
    private ExifTag mVersionTag;
    private ExifTag mGpsVersionTag;
    private ExifTag mModelTag;
    private ExifTag mDateTimeTag;
    private ExifTag mCompressionTag;
    private ExifTag mThumbnailFormatTag;
    private ExifTag mLongitudeTag;
    private ExifTag mShutterTag;
    Map<Integer, ExifTag> mTestTags;
    Map<Integer, Integer> mTagDefinitions;

    public ExifInterfaceTest(int imageRes, int xmlRes) {
        super(imageRes, xmlRes);
    }

    public ExifInterfaceTest(String imagePath, String xmlPath) {
        super(imagePath, xmlPath);
    }

    public void testInterface() throws Exception {

        InputStream imageInputStream = null;
        try {
            // Basic checks

            // Check if bitmap is valid
            byte[] imgData = Util.readToByteArray(getImageInputStream());
            imageInputStream = new ByteArrayInputStream(imgData);
            checkBitmap(imageInputStream);

            // Check defines
            int tag = ExifInterface.defineTag(1, (short) 0x0100);
            assertTrue(getImageTitle(), tag == 0x00010100);
            int tagDef = mInterface.getTagDefinition((short) 0x0100, IfdId.TYPE_IFD_0);
            assertTrue(getImageTitle(), tagDef == 0x03040001);
            int[] allowed = ExifInterface.getAllowedIfdsFromInfo(mInterface.getTagInfo().get(
                    ExifInterface.TAG_IMAGE_WIDTH));
            assertTrue(getImageTitle(), allowed.length == 2 && allowed[0] == IfdId.TYPE_IFD_0
                    && allowed[1] == IfdId.TYPE_IFD_1);

            // Check if there are any initial tags
            assertTrue(getImageTitle(), mInterface.getAllTags() == null);

            // ///////// Basic read/write testing

            // Make sure we can read
            imageInputStream = new ByteArrayInputStream(imgData);
            mInterface.readExif(imageInputStream);

            // Check tags against ground truth
            checkTagsAgainstXml(mInterface.getAllTags());

            // Make sure clearing Exif works
            mInterface.clearExif();
            assertTrue(getImageTitle(), mInterface.getAllTags() == null);

            // Make sure setting tags works
            mInterface.setTags(mTestTags.values());
            checkTagsAgainstHash(mInterface.getAllTags(), mTestTags);

            // Try writing over bitmap exif
            ByteArrayOutputStream imgModified = new ByteArrayOutputStream();
            mInterface.writeExif(imgData, imgModified);

            // Check if bitmap is valid
            byte[] imgData2 = imgModified.toByteArray();
            imageInputStream = new ByteArrayInputStream(imgData2);
            checkBitmap(imageInputStream);

            // Make sure we get the same tags out
            imageInputStream = new ByteArrayInputStream(imgData2);
            mInterface.readExif(imageInputStream);
            checkTagsAgainstHash(mInterface.getAllTags(), mTestTags);

            // Reread original image
            imageInputStream = new ByteArrayInputStream(imgData);
            mInterface.readExif(imageInputStream);

            // Write out with original exif
            imgModified = new ByteArrayOutputStream();
            mInterface.writeExif(imgData2, imgModified);

            // Read back in exif and check tags
            imgData2 = imgModified.toByteArray();
            imageInputStream = new ByteArrayInputStream(imgData2);
            mInterface.readExif(imageInputStream);
            checkTagsAgainstXml(mInterface.getAllTags());

            // Check if bitmap is valid
            imageInputStream = new ByteArrayInputStream(imgData2);
            checkBitmap(imageInputStream);

        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        } finally {
            Util.closeSilently(imageInputStream);
        }
    }

    public void testInterfaceModify() throws Exception {

        // TODO: This test is dependent on galaxy_nexus jpeg/xml file.
        InputStream imageInputStream = null;
        try {
            // Check if bitmap is valid
            byte[] imgData = Util.readToByteArray(getImageInputStream());
            imageInputStream = new ByteArrayInputStream(imgData);
            checkBitmap(imageInputStream);

            // ///////// Exif modifier testing.

            // Read exif and write to temp file
            imageInputStream = new ByteArrayInputStream(imgData);
            mInterface.readExif(imageInputStream);
            mInterface.writeExif(imgData, mTmpFile.getPath());

            // Check if bitmap is valid
            imageInputStream = new FileInputStream(mTmpFile);
            checkBitmap(imageInputStream);

            // Create some tags to overwrite with
            ArrayList<ExifTag> tags = new ArrayList<ExifTag>();
            tags.add(mInterface.buildTag(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.Orientation.RIGHT_TOP));
            tags.add(mInterface.buildTag(ExifInterface.TAG_USER_COMMENT, "goooooooooooooooooogle"));

            // Attempt to rewrite tags
            assertTrue(getImageTitle(), mInterface.rewriteExif(mTmpFile.getPath(), tags));

            imageInputStream.close();
            // Check if bitmap is valid
            imageInputStream = new FileInputStream(mTmpFile);
            checkBitmap(imageInputStream);

            // Read tags and check against xml
            mInterface.readExif(mTmpFile.getPath());
            for (ExifTag t : mInterface.getAllTags()) {
                short tid = t.getTagId();
                if (tid != ExifInterface.getTrueTagKey(ExifInterface.TAG_ORIENTATION)
                        && tid != ExifInterface.getTrueTagKey(ExifInterface.TAG_USER_COMMENT)) {
                    checkTagAgainstXml(t);
                }
            }
            assertTrue(getImageTitle(), mInterface.getTagIntValue(ExifInterface.TAG_ORIENTATION)
                    .shortValue() == ExifInterface.Orientation.RIGHT_TOP);
            String valString = mInterface.getTagStringValue(ExifInterface.TAG_USER_COMMENT);
            assertTrue(getImageTitle(), valString.equals("goooooooooooooooooogle"));

            // Test forced modify

            // Create some tags to overwrite with
            tags = new ArrayList<ExifTag>();
            tags.add(mInterface.buildTag(ExifInterface.TAG_SOFTWARE, "magic super photomaker pro"));
            tags.add(mInterface.buildTag(ExifInterface.TAG_USER_COMMENT, "noodles"));
            tags.add(mInterface.buildTag(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.Orientation.TOP_LEFT));

            // Force rewrite tags
            mInterface.forceRewriteExif(mTmpFile.getPath(), tags);

            imageInputStream.close();
            // Check if bitmap is valid
            imageInputStream = new FileInputStream(mTmpFile);
            checkBitmap(imageInputStream);

            // Read tags and check against xml
            mInterface.readExif(mTmpFile.getPath());
            for (ExifTag t : mInterface.getAllTags()) {
                short tid = t.getTagId();
                if (!ExifInterface.isOffsetTag(tid)
                        && tid != ExifInterface.getTrueTagKey(ExifInterface.TAG_SOFTWARE)
                        && tid != ExifInterface.getTrueTagKey(ExifInterface.TAG_USER_COMMENT)) {
                    checkTagAgainstXml(t);
                }
            }
            valString = mInterface.getTagStringValue(ExifInterface.TAG_SOFTWARE);
            String compareString = "magic super photomaker pro\0";
            assertTrue(getImageTitle(), valString.equals(compareString));
            valString = mInterface.getTagStringValue(ExifInterface.TAG_USER_COMMENT);
            assertTrue(getImageTitle(), valString.equals("noodles"));

        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        } finally {
            Util.closeSilently(imageInputStream);
        }
    }

    public void testInterfaceDefines() throws Exception {

        InputStream imageInputStream = null;
        try {
            // Check if bitmap is valid
            byte[] imgData = Util.readToByteArray(getImageInputStream());
            imageInputStream = new ByteArrayInputStream(imgData);
            checkBitmap(imageInputStream);

            // Set some tags.
            mInterface.setTags(mTestTags.values());

            // Check tag definitions against default
            for (Integer i : mTestTags.keySet()) {
                int check = mTagDefinitions.get(i).intValue();
                int actual = mInterface.getTagInfo().get(i);
                assertTrue(check == actual);
            }

            // Check defines
            int tag1 = ExifInterface.defineTag(IfdId.TYPE_IFD_1, (short) 42);
            int tag2 = ExifInterface.defineTag(IfdId.TYPE_IFD_INTEROPERABILITY, (short) 43);
            assertTrue(tag1 == 0x0001002a);
            assertTrue(tag2 == 0x0003002b);

            // Define some non-standard tags
            assertTrue(mInterface.setTagDefinition((short) 42, IfdId.TYPE_IFD_1,
                    ExifTag.TYPE_UNSIGNED_BYTE, (short) 16, new int[] {
                        IfdId.TYPE_IFD_1
                    }) == tag1);
            assertTrue(mInterface.getTagInfo().get(tag1) == 0x02010010);
            assertTrue(mInterface.setTagDefinition((short) 43, IfdId.TYPE_IFD_INTEROPERABILITY,
                    ExifTag.TYPE_ASCII, (short) 5, new int[] {
                            IfdId.TYPE_IFD_GPS, IfdId.TYPE_IFD_INTEROPERABILITY
                    }) == tag2);
            assertTrue(mInterface.getTagInfo().get(tag2) == 0x18020005);

            // Make sure these don't work
            assertTrue(mInterface.setTagDefinition((short) 42, IfdId.TYPE_IFD_1,
                    ExifTag.TYPE_UNSIGNED_BYTE, (short) 16, new int[] {
                        IfdId.TYPE_IFD_0
                    }) == ExifInterface.TAG_NULL);
            assertTrue(mInterface.setTagDefinition((short) 42, IfdId.TYPE_IFD_1, (short) 0,
                    (short) 16, new int[] {
                        IfdId.TYPE_IFD_1
                    }) == ExifInterface.TAG_NULL);
            assertTrue(mInterface.setTagDefinition((short) 42, 5, ExifTag.TYPE_UNSIGNED_BYTE,
                    (short) 16, new int[] {
                        5
                    }) == ExifInterface.TAG_NULL);
            assertTrue(mInterface.setTagDefinition((short) 42, IfdId.TYPE_IFD_1,
                    ExifTag.TYPE_UNSIGNED_BYTE, (short) 16, new int[] {
                        -1
                    }) == ExifInterface.TAG_NULL);
            assertTrue(mInterface.setTagDefinition((short) 43, IfdId.TYPE_IFD_GPS,
                    ExifTag.TYPE_ASCII, (short) 5, new int[] {
                        IfdId.TYPE_IFD_GPS
                    }) == ExifInterface.TAG_NULL);
            assertTrue(mInterface.setTagDefinition((short) 43, IfdId.TYPE_IFD_0,
                    ExifTag.TYPE_ASCII, (short) 5, new int[] {
                            IfdId.TYPE_IFD_0, IfdId.TYPE_IFD_GPS
                    }) == ExifInterface.TAG_NULL);

            // Set some tags
            mInterface.setTags(mTestTags.values());
            checkTagsAgainstHash(mInterface.getAllTags(), mTestTags);

            // Make some tags using new defines
            ExifTag defTag0 = mInterface.buildTag(tag1, new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
            });
            assertTrue(defTag0 != null);
            ExifTag defTag1 = mInterface.buildTag(tag2, "hihi");
            assertTrue(defTag1 != null);
            ExifTag defTag2 = mInterface.buildTag(tag2, IfdId.TYPE_IFD_GPS, "byte");
            assertTrue(defTag2 != null);

            // Make sure these don't work
            ExifTag badTag = mInterface.buildTag(tag1, new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
            });
            assertTrue(badTag == null);
            badTag = mInterface.buildTag(tag1, IfdId.TYPE_IFD_0, new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
            });
            assertTrue(badTag == null);
            badTag = mInterface.buildTag(0x0002002a, new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
            });
            assertTrue(badTag == null);
            badTag = mInterface.buildTag(tag2, new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17
            });
            assertTrue(badTag == null);

            // Set the tags
            assertTrue(mInterface.setTag(defTag0) == null);
            assertTrue(mInterface.setTag(defTag1) == null);
            assertTrue(mInterface.setTag(defTag2) == null);
            assertTrue(mInterface.setTag(defTag0).equals(defTag0));
            assertTrue(mInterface.setTag(null) == null);
            assertTrue(mInterface.setTagValue(tag2, "yoyo") == true);
            assertTrue(mInterface.setTagValue(tag2, "yaaarggg") == false);
            assertTrue(mInterface.getTagStringValue(tag2).equals("yoyo\0"));

            // Try writing over bitmap exif
            ByteArrayOutputStream imgModified = new ByteArrayOutputStream();
            mInterface.writeExif(imgData, imgModified);

            // Check if bitmap is valid
            byte[] imgData2 = imgModified.toByteArray();
            imageInputStream = new ByteArrayInputStream(imgData2);
            checkBitmap(imageInputStream);

            // Read back in the tags
            mInterface.readExif(imgData2);

            // Check tags
            for (ExifTag t : mInterface.getAllTags()) {
                int tid = t.getTagId();
                if (tid != ExifInterface.getTrueTagKey(tag1)
                        && tid != ExifInterface.getTrueTagKey(tag2)) {
                    checkTagAgainstHash(t, mTestTags);
                }
            }
            assertTrue(Arrays.equals(mInterface.getTagByteValues(tag1), new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
            }));
            assertTrue(mInterface.getTagStringValue(tag2).equals("yoyo\0"));
            assertTrue(mInterface.getTagStringValue(tag2, IfdId.TYPE_IFD_GPS).equals("byte\0"));

        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        } finally {
            Util.closeSilently(imageInputStream);
        }
    }

    public void testInterfaceThumbnails() throws Exception {

        InputStream imageInputStream = null;
        try {
            // Check if bitmap is valid
            byte[] imgData = Util.readToByteArray(getImageInputStream());
            imageInputStream = new ByteArrayInputStream(imgData);
            checkBitmap(imageInputStream);

            // Check thumbnails
            mInterface.readExif(imgData);
            Bitmap bmap = mInterface.getThumbnailBitmap();
            assertTrue(getImageTitle(), bmap != null);

            // Make a new thumbnail and set it
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 16;
            Bitmap thumb = BitmapFactory.decodeByteArray(imgData, 0, imgData.length, opts);
            assertTrue(getImageTitle(), thumb != null);
            assertTrue(getImageTitle(), mInterface.setCompressedThumbnail(thumb) == true);

            // Write out image
            ByteArrayOutputStream outData = new ByteArrayOutputStream();
            mInterface.writeExif(imgData, outData);

            // Make sure bitmap is still valid
            byte[] imgData2 = outData.toByteArray();
            imageInputStream = new ByteArrayInputStream(imgData2);
            checkBitmap(imageInputStream);

            // Read in bitmap and make sure thumbnail is still valid
            mInterface.readExif(imgData2);
            bmap = mInterface.getThumbnailBitmap();
            assertTrue(getImageTitle(), bmap != null);

        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        } finally {
            Util.closeSilently(imageInputStream);
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTmpFile = File.createTempFile("exif_test", ".jpg");
        mGroundTruth = ExifXmlReader.readXml(getXmlParser());

        mInterface = new ExifInterface();

        // TYPE_UNDEFINED with 4 components
        mVersionTag = mInterface.buildTag(ExifInterface.TAG_EXIF_VERSION, new byte[] {
                5, 4, 3, 2
        });
        // TYPE_UNSIGNED_BYTE with 4 components
        mGpsVersionTag = mInterface.buildTag(ExifInterface.TAG_GPS_VERSION_ID, new byte[] {
                6, 7, 8, 9
        });
        // TYPE ASCII with arbitary length
        mModelTag = mInterface.buildTag(ExifInterface.TAG_MODEL, "helloworld");
        // TYPE_ASCII with 20 components
        mDateTimeTag = mInterface.buildTag(ExifInterface.TAG_DATE_TIME, "2013:02:11 20:20:20");
        // TYPE_UNSIGNED_SHORT with 1 components
        mCompressionTag = mInterface.buildTag(ExifInterface.TAG_COMPRESSION, 100);
        // TYPE_UNSIGNED_LONG with 1 components
        mThumbnailFormatTag =
                mInterface.buildTag(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, 100);
        // TYPE_UNSIGNED_RATIONAL with 3 components
        mLongitudeTag = mInterface.buildTag(ExifInterface.TAG_GPS_LONGITUDE, new Rational[] {
                new Rational(2, 2), new Rational(11, 11),
                new Rational(102, 102)
        });
        // TYPE_RATIONAL with 1 components
        mShutterTag = mInterface
                .buildTag(ExifInterface.TAG_SHUTTER_SPEED_VALUE, new Rational(4, 6));

        mTestTags = new HashMap<Integer, ExifTag>();

        mTestTags.put(ExifInterface.TAG_EXIF_VERSION, mVersionTag);
        mTestTags.put(ExifInterface.TAG_GPS_VERSION_ID, mGpsVersionTag);
        mTestTags.put(ExifInterface.TAG_MODEL, mModelTag);
        mTestTags.put(ExifInterface.TAG_DATE_TIME, mDateTimeTag);
        mTestTags.put(ExifInterface.TAG_COMPRESSION, mCompressionTag);
        mTestTags.put(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, mThumbnailFormatTag);
        mTestTags.put(ExifInterface.TAG_GPS_LONGITUDE, mLongitudeTag);
        mTestTags.put(ExifInterface.TAG_SHUTTER_SPEED_VALUE, mShutterTag);

        mTagDefinitions = new HashMap<Integer, Integer>();
        mTagDefinitions.put(ExifInterface.TAG_EXIF_VERSION, 0x04070004);
        mTagDefinitions.put(ExifInterface.TAG_GPS_VERSION_ID, 0x10010004);
        mTagDefinitions.put(ExifInterface.TAG_MODEL, 0x03020000);
        mTagDefinitions.put(ExifInterface.TAG_DATE_TIME, 0x03020014);
        mTagDefinitions.put(ExifInterface.TAG_COMPRESSION, 0x03030001);
        mTagDefinitions.put(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, 0x02040001);
        mTagDefinitions.put(ExifInterface.TAG_GPS_LONGITUDE, 0x100a0003);
        mTagDefinitions.put(ExifInterface.TAG_SHUTTER_SPEED_VALUE, 0x040a0001);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mTmpFile.delete();
    }

    // Helper functions

    private void checkTagAgainstXml(ExifTag tag) {
        List<String> truth = mGroundTruth.get(tag.getIfd()).get(tag.getTagId());

        if (truth == null) {
            fail(String.format("Unknown Tag %02x", tag.getTagId()) + ", " + getImageTitle());
        }

        // No value from exiftool.
        if (truth.contains(null))
            return;

        String dataString = Util.tagValueToString(tag).trim();
        assertTrue(String.format("Tag %02x", tag.getTagId()) + ", " + getImageTitle()
                + ": " + dataString,
                truth.contains(dataString));
    }

    private void checkTagsAgainstXml(List<ExifTag> tags) {
        for (ExifTag t : tags) {
            checkTagAgainstXml(t);
        }
    }

    private void checkTagAgainstHash(ExifTag tag, Map<Integer, ExifTag> testTags) {
        int tagdef = mInterface.getTagDefinitionForTag(tag);
        assertTrue(getImageTitle(), tagdef != ExifInterface.TAG_NULL);
        ExifTag t = testTags.get(tagdef);
        // Ignore offset tags & other special tags
        if (!ExifInterface.sBannedDefines.contains(tag.getTagId())) {
            assertTrue(getImageTitle(), t != null);
        } else {
            return;
        }
        if (t == tag)
            return;
        assertTrue(getImageTitle(), tag.equals(t));
        assertTrue(getImageTitle(), tag.getDataType() == t.getDataType());
        assertTrue(getImageTitle(), tag.getTagId() == t.getTagId());
        assertTrue(getImageTitle(), tag.getIfd() == t.getIfd());
        assertTrue(getImageTitle(), tag.getComponentCount() == t.getComponentCount());
    }

    private void checkTagsAgainstHash(List<ExifTag> tags, Map<Integer, ExifTag> testTags) {
        for (ExifTag t : tags) {
            checkTagAgainstHash(t, testTags);
        }
    }

    private void checkBitmap(InputStream inputStream) throws IOException {
        Bitmap bmp = BitmapFactory.decodeStream(inputStream);
        assertTrue(getImageTitle(), bmp != null);
    }

}
