
package com.android.camera.async;

/**
 * A thread that runs at the given Android thread priority.
 */
public class AndroidPriorityThread extends Thread {
    private final int mAndroidThreadPriority;

    /**
     * Constructs the new thread.
     *
     * @param androidThreadPriority the android priority the thread should run
     *            at. This has to be one of the
     *            android.os.Process.THREAD_PRIORITY_* values.
     * @param runnable the runnable to run at this thread priority.
     */
    public AndroidPriorityThread(int androidThreadPriority, Runnable runnable) {
        super(runnable);
        mAndroidThreadPriority = androidThreadPriority;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(mAndroidThreadPriority);
        super.run();
    }
}
