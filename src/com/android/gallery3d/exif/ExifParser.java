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
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

public class ExifParser {

    private static final String TAG = "ExifParser";

    private static final short SOI =  (short) 0xFFD8; // SOI marker of JPEG
    private static final short APP1 = (short) 0xFFE1; // APP1 marker of JPEG

    private static final int EXIF_HEADER = 0x45786966; // EXIF header "Exif"
    private static final short EXIF_HEADER_TAIL = (short) 0x0000; // EXIF header in APP1

    // TIFF header
    private static final short LITTLE_ENDIAN_TAG = (short) 0x4949; // "II"
    private static final short BIG_ENDIAN_TAG = (short) 0x4d4d; // "MM"
    private static final short TIFF_HEADER_TAIL = 0x002A;

    public IfdParser parse(InputStream inputStream) throws ExifInvalidFormatException, IOException{
        if (!seekTiffData(inputStream)) {
            return null;
        }
        TiffInputStream tiffStream = new TiffInputStream(inputStream);
        parseTiffHeader(tiffStream);
        long offset = tiffStream.readUnsignedInt();
        if (offset > Integer.MAX_VALUE) {
            throw new ExifInvalidFormatException("Offset value is larger than Integer.MAX_VALUE");
        }
        return new IfdParser(tiffStream, (int)offset);
    }

    private void parseTiffHeader(TiffInputStream tiffStream) throws IOException,
            ExifInvalidFormatException {
        short byteOrder = tiffStream.readShort();
        ByteOrder order;
        if (LITTLE_ENDIAN_TAG == byteOrder) {
            tiffStream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        } else if (BIG_ENDIAN_TAG == byteOrder) {
            tiffStream.setByteOrder(ByteOrder.BIG_ENDIAN);
        } else {
            throw new ExifInvalidFormatException("Invalid TIFF header");
        }

        if (tiffStream.readShort() != TIFF_HEADER_TAIL) {
            throw new ExifInvalidFormatException("Invalid TIFF header");
        }
    }

    /**
     * Try to seek the tiff data. If there is no tiff data, return false, else return true and
     * the inputstream will be at the start of tiff data
     */
    private boolean seekTiffData(InputStream inputStream) throws IOException,
            ExifInvalidFormatException {
        DataInputStream dataStream = new DataInputStream(inputStream);

        // SOI and APP1
        if (dataStream.readShort() != SOI) {
            throw new ExifInvalidFormatException("Invalid JPEG format");
        }

        if (dataStream.readShort() != APP1) {
            return false;
        }

        // APP1 length, it's not used for us
        dataStream.readShort();

        // Exif header
        if (dataStream.readInt() != EXIF_HEADER
                || dataStream.readShort() != EXIF_HEADER_TAIL) {
            // There is no EXIF data;
            return false;
        }

        return true;
    }
}