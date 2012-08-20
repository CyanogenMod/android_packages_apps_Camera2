package com.android.gallery3d.ui;

import com.android.gallery3d.ui.GLRoot.OnGLIdleListener;

public class PreparePageFadeoutTexture implements OnGLIdleListener {
    private RawTexture mTexture;
    private boolean mResultReady = false;
    private GLView mRootPane;

    public PreparePageFadeoutTexture(int w, int h,  GLView rootPane) {
        mTexture = new RawTexture(w, h, true);
        mRootPane =  rootPane;
    }

    public synchronized RawTexture get() {
        try {
            while (!mResultReady) {
                wait();
            }
        } catch (InterruptedException e) {
            // Since this is just used for a transition, not that important
        }
        return mTexture;
    }

    @Override
    public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
            canvas.beginRenderTarget(mTexture);
            mRootPane.render(canvas);
            canvas.endRenderTarget();
            synchronized (this) {
                mResultReady = true;
                notifyAll();
            }
            return false;
    }
}
