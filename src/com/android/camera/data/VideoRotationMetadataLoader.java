package com.android.camera.data;

import android.media.MediaMetadataRetriever;

import com.android.camera.debug.Log;

public class VideoRotationMetadataLoader {
    private static final Log.Tag TAG = new Log.Tag("VidRotDataLoader");
    private static final String ROTATION_KEY = "metadata_video_rotation";
    private static final String WIDTH_KEY = "metadata_video_width";
    private static final String HEIGHT_KEY = "metadata_video_height";

    private static final String ROTATE_90 = "90";
    private static final String ROTATE_270 = "270";

    static boolean isRotated(LocalData localData) {
        final String rotation = localData.getMetadata().getString(ROTATION_KEY);
        return ROTATE_90.equals(rotation) || ROTATE_270.equals(rotation);
    }

    static int getWidth(LocalData localData) {
        return localData.getMetadata().getInt(WIDTH_KEY);

    }

    static int getHeight(LocalData localData) {
        return localData.getMetadata().getInt(HEIGHT_KEY);
    }

    static void loadRotationMetdata(final LocalData data) {
        final String path = data.getPath();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            String rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            data.getMetadata().putString(ROTATION_KEY, rotation);

            String val = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            int width = Integer.parseInt(val);

            data.getMetadata().putInt(WIDTH_KEY, width);

            val = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            int height = Integer.parseInt(val);

            data.getMetadata().putInt(HEIGHT_KEY, height);
        } catch (RuntimeException ex) {
            // setDataSource() can cause RuntimeException beyond
            // IllegalArgumentException. e.g: data contain *.avi file.
            Log.e(TAG, "MediaMetdataRetriever.setDataSource() fail", ex);
        }
    }
}
