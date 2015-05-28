package com.android.camera.stats;

import com.android.camera.session.CaptureSession.ImageLifecycleListener;

/**
 * Simple statistics of internal app behavior during capture.
 */
public class CaptureStats implements ImageLifecycleListener {

    public CaptureStats(boolean isHdrPlus) {
    }

    @Override
    public void onCaptureStarted() {
    }

    @Override
    public void onTinyThumb() {
    }

    @Override
    public void onMediumThumb() {
    }

    @Override
    public void onProcessingStarted() {
    }

    @Override
    public void onProcessingComplete() {
    }

    @Override
    public void onCapturePersisted() {
    }

    @Override
    public void onCaptureCanceled() {
    }

    @Override
    public void onCaptureFailed() {
    }
}
