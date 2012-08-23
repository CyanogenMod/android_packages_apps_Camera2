package com.android.gallery3d.ui;

import android.os.ConditionVariable;

import com.android.gallery3d.ui.GLRoot.OnGLIdleListener;

public class PreparePageFadeoutTexture implements OnGLIdleListener {
    private static final long TIMEOUT = FadeTexture.DURATION;
    private RawTexture mTexture;
    private ConditionVariable mResultReady = new ConditionVariable(false);
    private boolean mCancelled = false;
    private GLView mRootPane;

    public PreparePageFadeoutTexture(int w, int h,  GLView rootPane) {
        mTexture = new RawTexture(w, h, true);
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
}
