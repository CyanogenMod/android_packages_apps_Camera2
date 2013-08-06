package com.android.camera.support.app;

import android.net.Uri;

public interface StitchingChangeListener {
    public void onStitchingQueued(Uri uri);

    public void onStitchingResult(Uri uri);

    public void onStitchingProgress(Uri uri, int progress);
}
