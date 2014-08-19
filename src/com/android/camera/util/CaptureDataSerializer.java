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

package com.android.camera.util;

import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.TonemapCurve;
import android.util.Pair;
import android.util.Rational;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

/**
 * Can be used for debugging to output details about Camera2 capture request and
 * responses.
 */
public class CaptureDataSerializer {
    private static interface Writeable {
        public void write(Writer writer) throws IOException;
    }

    private static final Tag TAG = new Tag("CaptureDataSerilzr");

    /**
     * Generate a human-readable string of the given capture request and return
     * it.
     */
    public static String toString(String title, CaptureRequest metadata) {
        StringWriter writer = new StringWriter();
        dumpMetadata(title, metadata, writer);
        return writer.toString();
    }

    /**
     * Generate a human-readable string of the given capture request and write
     * it to the given file.
     */
    public static void toFile(String title, CameraMetadata<?> metadata, File file) {
        try {
            // Will append if the file already exists.
            FileWriter writer = new FileWriter(file, true);
            if (metadata instanceof CaptureRequest) {
                dumpMetadata(title, (CaptureRequest) metadata, writer);
            } else if (metadata instanceof CaptureResult) {
                dumpMetadata(title, (CaptureResult) metadata, writer);
            } else {
                writer.close();
                throw new IllegalArgumentException("Cannot generate debug data from type "
                        + metadata.getClass().getName());
            }
            writer.close();
        } catch (IOException ex) {
            Log.e(TAG, "Could not write capture data to file.", ex);
        }
    }

    /**
     * Writes the data about the marker and requests to the given folder for
     * offline debugging.
     */
    private static void dumpMetadata(final String title, final CaptureRequest metadata,
            Writer writer) {
        Writeable writeable = new Writeable() {
            @Override
            public void write(Writer writer) throws IOException {
                List<CaptureRequest.Key<?>> keys = metadata.getKeys();
                writer.write(title + '\n');

                // TODO: move to CameraMetadata#toString ?
                for (CaptureRequest.Key<?> key : keys) {
                    writer.write(String.format("    %s\n", key.getName()));
                    writer.write(String.format("        %s\n",
                            metadataValueToString(metadata.get(key))));
                }
            }
        };
        dumpMetadata(writeable, new BufferedWriter(writer));
    }

    /**
     * Writes the data about the marker and requests to the given folder for
     * offline debugging.
     */
    private static void dumpMetadata(final String title, final CaptureResult metadata,
            Writer writer) {
        Writeable writeable = new Writeable() {
            @Override
            public void write(Writer writer) throws IOException {
                List<CaptureResult.Key<?>> keys = metadata.getKeys();
                writer.write(String.format(title));

                // TODO: move to CameraMetadata#toString ?
                for (CaptureResult.Key<?> key : keys) {
                    writer.write(String.format("    %s\n", key.getName()));
                    writer.write(String.format("        %s\n",
                            metadataValueToString(metadata.get(key))));
                }
            }
        };
        dumpMetadata(writeable, new BufferedWriter(writer));
    }

    private static String metadataValueToString(Object object) {
        if (object == null) {
            return "<null>";
        }
        if (object.getClass().isArray()) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");

            int length = Array.getLength(object);
            for (int i = 0; i < length; ++i) {
                Object item = Array.get(object, i);
                builder.append(metadataValueToString(item));

                if (i != length - 1) {
                    builder.append(", ");
                }
            }
            builder.append(']');

            return builder.toString();
        } else {
            // These classes don't have a toString() method yet
            // See: http://b/16899576
            if (object instanceof RggbChannelVector) {
                return toString((RggbChannelVector) object);
            } else if (object instanceof ColorSpaceTransform) {
                return toString((ColorSpaceTransform) object);
            } else if (object instanceof TonemapCurve) {
                return toString((TonemapCurve) object);
            } else if (object instanceof Pair) {
                return toString((Pair<?, ?>) object);
            }
            return object.toString();
        }
    }

    private static void dumpMetadata(Writeable metadata, Writer writer) {
        /**
         * Save metadata to file, appending if another metadata is already in
         * that file.
         */
        try {
            metadata.write(writer);
        } catch (IOException e) {
            Log.e(TAG, "dumpMetadata - Failed to dump metadata", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "dumpMetadata - Failed to close writer.", e);
            }
        }
    }

    private static String toString(RggbChannelVector vector) {
        StringBuilder str = new StringBuilder();
        str.append("RggbChannelVector:");
        str.append(" R:");
        str.append(vector.getRed());
        str.append(" G(even):");
        str.append(vector.getGreenEven());
        str.append(" G(odd):");
        str.append(vector.getGreenOdd());
        str.append(" B:");
        str.append(vector.getBlue());

        return str.toString();
    }

    private static String toString(ColorSpaceTransform transform) {
        StringBuilder str = new StringBuilder();
        Rational[] rationals = new Rational[9];
        transform.copyElements(rationals, 0);
        str.append("ColorSpaceTransform: ");
        str.append(Arrays.toString(rationals));
        return str.toString();
    }

    private static String toString(TonemapCurve curve) {
        StringBuilder str = new StringBuilder();
        str.append("TonemapCurve:");

        float[] reds = new float[curve.getPointCount(TonemapCurve.CHANNEL_RED)
                * TonemapCurve.POINT_SIZE];
        curve.copyColorCurve(TonemapCurve.CHANNEL_RED, reds, 0);
        float[] greens = new float[curve.getPointCount(TonemapCurve.CHANNEL_GREEN)
                * TonemapCurve.POINT_SIZE];
        curve.copyColorCurve(TonemapCurve.CHANNEL_GREEN, greens, 0);
        float[] blues = new float[curve.getPointCount(TonemapCurve.CHANNEL_BLUE)
                * TonemapCurve.POINT_SIZE];
        curve.copyColorCurve(TonemapCurve.CHANNEL_BLUE, blues, 0);

        str.append("\n\nReds: ");
        str.append(Arrays.toString(reds));
        str.append("\n\nGreens: ");
        str.append(Arrays.toString(greens));
        str.append("\n\nBlues: ");
        str.append(Arrays.toString(blues));

        return str.toString();
    }

    private static String toString(Pair<?, ?> pair) {
        return "Pair: " + metadataValueToString(pair.first) + " / "
                + metadataValueToString(pair.second);
    }
}
