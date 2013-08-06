package com.android.camera;

import android.content.Context;
import android.net.Uri;

class PanoramaStitchingManager implements ImageTaskManager {

    public PanoramaStitchingManager(Context ctx) {
    }

    @Override
    public void addTaskListener(TaskListener l) {
        // do nothing.
    }

    @Override
    public void removeTaskListener(TaskListener l) {
        // do nothing.
    }

    @Override
    public int getTaskProgress(Uri uri) {
        return -1;
    }
}
