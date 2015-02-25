/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.burst;

import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.session.StackSaver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class BurstResultsSaver {
    private static final Tag TAG = new Tag("BurstResultsSaver");

    /**
     * The format string of burst media item file name (without extension).
     * <p/>
     * An media item file name has the following format: "Burst_" + artifact
     * type + "_" + index of artifact + "_" + index of media item + "_" +
     * timestamp
     */
    private static final String MEDIA_ITEM_FILENAME_FORMAT_STRING = "Burst_%s_%d_%d_%d";

    /**
     * Generates sequential timestamp with 1 second difference.
     */
    private static class SequentialTimestampGenerator {
        private long mSeedTimestampMillis;

        /**
         * New instance of generator.
         *
         * @param seedTimestampMillis the timestamp in milliseconds for
         *            initializing the generator.
         */
        public SequentialTimestampGenerator(long seedTimestampMillis) {
            mSeedTimestampMillis = seedTimestampMillis;
        }

        /**
         * Returns the next timestamp.
         */
        public synchronized long getNextTimestampMillis() {
            mSeedTimestampMillis += TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);
            return mSeedTimestampMillis;
        }
    }

    public static void logArtifactCount(final Map<String, Integer> artifactTypeCount) {
        final String prefix = "Finished burst. Creating ";
        List<String> artifactDescription = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : artifactTypeCount.entrySet()) {
            artifactDescription.add(entry.getValue() + " " + entry.getKey());
        }

        String message = prefix + TextUtils.join(" and ", artifactDescription) + ".";
        Log.d(TAG, message);
    }

    /**
     * Saves the burst result and on completion re-enables the shutter button.
     *
     * @param burstResult the result of the burst.
     */
    public static void saveBurstResultsInBackground(final BurstResult burstResult,
            final StackSaver stackSaver, final Runnable onCompletetionCallback) {
        Log.i(TAG, "Saving results of of the burst.");

        AsyncTask<Void, String, Void> saveTask =
                new AsyncTask<Void, String, Void>() {
                    @Override
                    protected Void doInBackground(Void... arg0) {
                        // The timestamp with which a media item is saved
                        // determines its place in the film strip. The newer
                        // items appear first.
                        // We save artifacts and their corresponding media
                        // items sequentially in the desired order. The order
                        // of the artifacts is implicitly defined by
                        // burstResult.getTypes() and the media items inside the
                        // artifacts are assumed to be sorted in ascending order
                        // by timestamps.
                        // We create a timestamp-generator that generates
                        // timestamps in order and use it to save timestamps.
                        SequentialTimestampGenerator timestampGen =
                                new SequentialTimestampGenerator(System.currentTimeMillis());
                        for (String artifactType : burstResult.getTypes()) {
                            publishProgress(artifactType);
                            saveArtifacts(stackSaver, burstResult, artifactType,
                                    timestampGen);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        onCompletetionCallback.run();
                    }

                    @Override
                    protected void onProgressUpdate(String... artifactTypes) {
                        logProgressUpdate(artifactTypes, burstResult);
                    }
                };
        saveTask.execute(null, null, null);
    }

    /**
     * Save individual artifacts for bursts.
     */
    private static void saveArtifacts(final StackSaver stackSaver, final BurstResult burstResult,
            final String artifactType, SequentialTimestampGenerator timestampGenerator) {
        List<BurstArtifact> artifactList = burstResult.getArtifactsByType(artifactType);
        for (int artifactIndex = 0; artifactIndex < artifactList.size(); artifactIndex++) {
            List<BurstMediaItem> mediaItems = artifactList.get(artifactIndex).getMediaItems();
            for (int index = 0; index < mediaItems.size(); index++) {
                saveBurstMediaItem(stackSaver, mediaItems.get(index),
                        artifactType, artifactIndex + 1, index + 1, timestampGenerator);
            }
        }
    }

    private static void saveBurstMediaItem(StackSaver stackSaver,
            BurstMediaItem mediaItem,
            String artifactType,
            int artifactIndex,
            int index,
            SequentialTimestampGenerator timestampGenerator) {
        // Use ordered timestamp for saving the media item, this way media
        // items appear to be in the correct order when user swipes to the
        // film strip.
        long timestamp = timestampGenerator.getNextTimestampMillis();
        final String title = String.format(MEDIA_ITEM_FILENAME_FORMAT_STRING,
                artifactType, artifactIndex, index, mediaItem.getTimestamp());
        String mimeType = mediaItem.getMimeType();

        stackSaver.saveStackedImage(mediaItem.getFilePath(),
                title,
                mediaItem.getWidth(),
                mediaItem.getHeight(),
                0, // Artifacts returned from burst have upright orientation.
                timestamp,
                mimeType);
    }

    private static void logProgressUpdate(String[] artifactTypes, BurstResult burstResult) {
        for (String artifactType : artifactTypes) {
            List<BurstArtifact> artifacts =
                    burstResult.getArtifactsByType(artifactType);
            if (!artifacts.isEmpty()) {
                Log.d(TAG, "Saving " + artifacts.size()
                        + " " + artifactType + "s.");
            }
        }
    }

}
