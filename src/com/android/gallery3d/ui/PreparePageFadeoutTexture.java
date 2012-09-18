package com.android.gallery3d.ui;

import android.os.ConditionVariable;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.ui.GLRoot.OnGLIdleListener;

public class PreparePageFadeoutTexture implements OnGLIdleListener {
    private static final long TIMEOUT = FadeTexture.DURATION;
    public static final String KEY_FADE_TEXTURE = "fade_texture";

    private RawTexture mTexture;
    private ConditionVariable mResultReady = new ConditionVariable(false);
    private boolean mCancelled = false;
    private GLView mRootPane;

    public PreparePageFadeoutTexture(GLView rootPane) {
        mTexture = new RawTexture(rootPane.getWidth(), rootPane.getHeight(), true);
        mRootPane =  rootPane;
    }

    public synchronized RawTexture get() {
        if (mCancelled) {
            return null;
        } else if (mResultReady.block(TIMEOUT)) {
            return mTexture;
        } else {
            mCancelled = true;
            return null;
        }
    }

    @Override
    public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
            if(!mCancelled) {
                canvas.beginRenderTarget(mTexture);
                mRootPane.render(canvas);
                canvas.endRenderTarget();
            } else {
                mTexture = null;
            }
            mResultReady.open();
            return false;
    }

    public static void prepareFadeOutTexture(AbstractGalleryActivity activity,
            GLView rootPane) {
        GLRoot root = activity.getGLRoot();
        PreparePageFadeoutTexture task = new PreparePageFadeoutTexture(rootPane);
        RawTexture texture = null;
        root.unlockRenderThread();
        try {
            root.addOnGLIdleListener(task);
            texture = task.get();
        } finally {
            root.lockRenderThread();
        }

        if (texture != null) {
            activity.getTransitionStore().put(KEY_FADE_TEXTURE, texture);
        }
    }
}
