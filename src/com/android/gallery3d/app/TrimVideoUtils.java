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

// Modified example based on mp4parser google code open source project.
// http://code.google.com/p/mp4parser/source/browse/trunk/examples/src/main/java/com/googlecode/mp4parser/ShortenExample.java

package com.android.gallery3d.app;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Shortens/Crops a track
 */
public class TrimVideoUtils {

    public static void startTrim(File src, File dst, int startMs, int endMs) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(src, "r");
        Movie movie = MovieCreator.build(randomAccessFile.getChannel());

        // remove all tracks we will create new tracks from the old
        List<Track> tracks = movie.getTracks();
        movie.setTracks(new LinkedList<Track>());

        double startTime = startMs/1000;
        double endTime = endMs/1000;

        boolean timeCorrected = false;

        // Here we try to find a track that has sync samples. Since we can only start decoding
        // at such a sample we SHOULD make sure that the start of the new fragment is exactly
        // such a frame
        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                if (timeCorrected) {
                    // This exception here could be a false positive in case we have multiple tracks
                    // with sync samples at exactly the same positions. E.g. a single movie containing
                    // multiple qualities of the same video (Microsoft Smooth Streaming file)

                    throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                }
                startTime = correctTimeToSyncSample(track, startTime, false);
                endTime = correctTimeToSyncSample(track, endTime, true);
                timeCorrected = true;
            }
        }

        for (Track track : tracks) {
            long currentSample = 0;
            double currentTime = 0;
            long startSample = -1;
            long endSample = -1;

            for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
                TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
                for (int j = 0; j < entry.getCount(); j++) {
                    // entry.getDelta() is the amount of time the current sample covers.

                    if (currentTime <= startTime) {
                        // current sample is still before the new starttime
                        startSample = currentSample;
                    }
                    if (currentTime <= endTime) {
                        // current sample is after the new start time and still before the new endtime
                        endSample = currentSample;
                    } else {
                        // current sample is after the end of the cropped video
                        break;
                    }
                    currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                    currentSample++;
                }
            }
            movie.addTrack(new CroppedTrack(track, startSample, endSample));
        }
        IsoFile out = new DefaultMp4Builder().build(movie);

        if (!dst.exists()) {
            dst.createNewFile();
        }

        FileOutputStream fos = new FileOutputStream(dst);
        FileChannel fc = fos.getChannel();
        out.getBox(fc);  // This one build up the memory.

        fc.close();
        fos.close();
        randomAccessFile.close();
    }

    protected static long getDuration(Track track) {
        long duration = 0;
        for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
            duration += entry.getCount() * entry.getDelta();
        }
        return duration;
    }

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }


}
