package com.android.camera.device;

import com.android.camera.async.Lifetime;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * This class manages the lifecycle of a single device and API version.
 * A single instance deals with multiple requests for the same device
 * by canceling previous, uncompleted future requests, and tolerates
 * multiple calls to open() and close(). Once the device reaches the
 * shutdown phase (Defined as a close event with no pending open
 * requests) The object is no longer useful and a new instance should
 * be created.
 */
public class CameraDeviceLifecycle<TDevice, TDeviceId> implements
      SingleDeviceLifecycle<TDevice, CameraDeviceKey<TDeviceId>> {

    private final Object mLock;
    private final CameraDeviceKey<TDeviceId> mDeviceKey;

    @GuardedBy("mLock")
    private final SingleDeviceStateMachine<TDevice, CameraDeviceKey<TDeviceId>> mDeviceState;

    @Nullable
    @GuardedBy("mLock")
    private SingleDeviceRequest<TDevice> mDeviceRequest;

    // TODO: Consider passing in parent lifetime to ensure this is
    // ALWAYS shut down.
    public CameraDeviceLifecycle(CameraDeviceKey<TDeviceId> cameraDeviceKey,
          SingleDeviceStateMachine<TDevice, CameraDeviceKey<TDeviceId>> deviceState) {
        mDeviceKey = cameraDeviceKey;
        mDeviceState = deviceState;
        mLock = new Object();
    }

    @Override
    public CameraDeviceKey<TDeviceId> getId() {
        return mDeviceKey;
    }

    @Override
    public ListenableFuture<TDevice> createRequest(Lifetime lifetime) {
        synchronized (mLock) {
            mDeviceRequest = new SingleDeviceRequest<>(lifetime);
            lifetime.add(mDeviceRequest);
            mDeviceState.setRequest(mDeviceRequest);

            return mDeviceRequest.getFuture();
        }
    }

    /**
     * Request that the device represented by this lifecycle should
     * attempt to reach an open state.
     */
    @Override
    public void open() {
        synchronized (mLock) {
            mDeviceState.requestOpen();
        }
    }

    /**
     * Request that the device represented by this lifecycle should
     * attempt to reach a closed state.
     */
    @Override
    public void close() {
        synchronized (mLock) {
            if (mDeviceRequest != null) {
                mDeviceRequest.close();
                mDeviceRequest = null;
            }
            mDeviceState.requestClose();
        }
    }
}
