/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.device;

import com.android.camera.async.Lifetime;
import com.android.camera.async.SafeCloseable;
import com.android.camera.debug.Log.Tag;
import com.android.camera.debug.Logger;

import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Internal state machine for dealing with the interactions of opening
 * a physical device. Since there are 4 device states and 2 target
 * states the transition table looks like this:
 *
 * Device  | Target
 * Opening   Opened -> Nothing.
 * Opened    Opened -> Execute onDeviceOpened.
 * Closing   Opened -> Nothing.
 * Closed    Opened -> Execute onDeviceOpening.
 *                     Device moves to Opening.
 * Opening   Closed -> Nothing.
 * Opened    Closed -> Execute onDeviceClosing.
 *                     Device moves to Closing.
 * Closing   Closed -> Nothing.
 * Closed    Closed -> Execute onDeviceClosed.
 *
 */
@ThreadSafe
@ParametersAreNonnullByDefault
public class SingleDeviceStateMachine<TDevice, TKey> implements SingleDeviceCloseListener,
      SingleDeviceOpenListener<TDevice> {
    private static final Tag TAG = new Tag("DeviceStateM");

    /** Physical state of the device. */
    private enum DeviceState {
        OPENING,
        OPENED,
        CLOSING,
        CLOSED
    }

    /** Physical state the state machine should reach. */
    private enum TargetState {
        OPENED,
        CLOSED
    }

    private final ReentrantLock mLock;
    private final Lifetime mDeviceLifetime;
    private final SingleDeviceActions<TDevice> mDeviceActions;
    private final SingleDeviceShutdownListener<TKey> mShutdownListener;
    private final TKey mDeviceKey;
    private final Logger mLogger;

    @GuardedBy("mLock")
    private boolean mIsShutdown;

    @GuardedBy("mLock")
    private TargetState mTargetState;

    @GuardedBy("mLock")
    private DeviceState mDeviceState;

    @Nullable
    @GuardedBy("mLock")
    private SingleDeviceRequest<TDevice> mDeviceRequest;

    @Nullable
    @GuardedBy("mLock")
    private TDevice mOpenDevice;

    /**
     * This creates a new state machine with a listener to represent
     * the physical states of a device. Both the target and current
     * state of the device are initially set to "Closed"
     */
    public SingleDeviceStateMachine(SingleDeviceActions<TDevice> deviceActions,
          TKey deviceKey, SingleDeviceShutdownListener<TKey> deviceShutdownListener,
          Logger.Factory logFactory)  {
        mDeviceActions = deviceActions;
        mShutdownListener = deviceShutdownListener;
        mDeviceKey = deviceKey;

        mLock = new ReentrantLock();
        mDeviceLifetime = new Lifetime();
        mLogger = logFactory.create(TAG);

        mIsShutdown = false;
        mTargetState = TargetState.CLOSED;
        mDeviceState = DeviceState.CLOSED;
    }

    /**
     * Request that the state machine move towards an open state.
     */
    public void requestOpen() {
        mLock.lock();
        try {
            if (mIsShutdown) {
               return;
            }

            mTargetState = TargetState.OPENED;
            update();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Request that the state machine move towards a closed state.
     */
    public void requestClose() {
        mLock.lock();
        try {
            if (mIsShutdown) {
                return;
            }

            mTargetState = TargetState.CLOSED;
            update();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * When a new request is set, the previous request should be canceled
     * if it has not been completed.
     */
    public void setRequest(final SingleDeviceRequest<TDevice> deviceRequest) {
        mLock.lock();
        try {
            if (mIsShutdown) {
                deviceRequest.close();
                return;
            }

            SingleDeviceRequest<TDevice> previous = mDeviceRequest;
            mDeviceRequest = deviceRequest;
            mDeviceLifetime.add(deviceRequest);
            deviceRequest.getLifetime().add(new SafeCloseable() {
                    @Override
                    public void close() {
                        requestCloseIfCurrentRequest(deviceRequest);
                    }
                });

            if (mOpenDevice != null) {
                mDeviceRequest.set(mOpenDevice);
            }

            if (previous != null) {
                previous.close();
            }
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onDeviceOpened(TDevice device) {
        mLock.lock();
        try {
            if (mIsShutdown) {
                return;
            }

            mOpenDevice = device;
            mDeviceState = DeviceState.OPENED;

            update();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onDeviceOpenException(Throwable throwable) {
        mLock.lock();
        try {
            if (mIsShutdown) {
                return;
            }

            closeRequestWithException(throwable);
            shutdown();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onDeviceOpenException(TDevice tDevice) {
        mLock.lock();
        try {
            if (mIsShutdown) {
                return;
            }

            closeRequestWithException(new CameraOpenException(-1));
            mDeviceState = DeviceState.CLOSING;
            mTargetState = TargetState.CLOSED;
            executeClose(tDevice);
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onDeviceClosed() {
        mLock.lock();
        try {
            if (mIsShutdown) {
                return;
            }

            mOpenDevice = null;
            mDeviceState = DeviceState.CLOSED;

            update();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onDeviceClosingException(Throwable throwable) {
        mLock.lock();
        try {
            if (mIsShutdown) {
                return;
            }

            closeRequestWithException(throwable);
            shutdown();
        } finally {
            mLock.unlock();
        }
    }


    @GuardedBy("mLock")
    private void update() {
        if (mIsShutdown) {
            return;
        }

        if (mDeviceState == DeviceState.CLOSED && mTargetState == TargetState.OPENED) {
            executeOpen();
        } else if (mDeviceState == DeviceState.OPENED && mTargetState == TargetState.OPENED) {
            executeOpened();
        } else if (mDeviceState == DeviceState.OPENED && mTargetState == TargetState.CLOSED) {
            executeClose();
        }  else if (mDeviceState == DeviceState.CLOSED && mTargetState == TargetState.CLOSED) {
            shutdown();
        }
    }

    @GuardedBy("mLock")
    private void executeOpen() {
        mDeviceState = DeviceState.OPENING;
        try {
            mDeviceActions.executeOpen(this, mDeviceLifetime);
        } catch (Exception e) {
            onDeviceOpenException(e);
        }
        // TODO: Consider adding a timeout to the open call so that requests
        // are not left un-resolved.
    }

    @GuardedBy("mLock")
    private void executeOpened() {
        if(mDeviceRequest != null) {
            mDeviceRequest.set(mOpenDevice);
        }

        // TODO: Consider performing a shutdown if there is no open
        // device request.
    }

    @GuardedBy("mLock")
    private void executeClose() {
        // TODO: Consider adding a timeout to the close call so that requests
        // are not left un-resolved.

        final TDevice device = mOpenDevice;
        mOpenDevice = null;

        executeClose(device);
    }

    @GuardedBy("mLock")
    private void executeClose(@Nullable TDevice device) {
        if (device != null) {
            mDeviceState = DeviceState.CLOSING;
            mTargetState = TargetState.CLOSED;
            closeRequest();

            try {
                mDeviceActions.executeClose(this, device);
            } catch (Exception e) {
                onDeviceClosingException(e);
            }
        } else {
            shutdown();
        }
    }

    @GuardedBy("mLock")
    private void requestCloseIfCurrentRequest(SingleDeviceRequest<TDevice> request) {
        if (mDeviceRequest == null || mDeviceRequest == request) {
            requestClose();
        }
    }

    @GuardedBy("mLock")
    private void closeRequestWithException(Throwable exception) {
        mOpenDevice = null;
        if (mDeviceRequest != null) {
            mLogger.w("There was a problem closing device: " + mDeviceKey, exception);

            mDeviceRequest.closeWithException(exception);
            mDeviceRequest = null;
        }
    }

    @GuardedBy("mLock")
    private void closeRequest() {
        if (mDeviceRequest != null) {
            mDeviceRequest.close();
        }
        mDeviceRequest = null;
    }

    /**
     * Cancel requests, and set internal device state back to
     * a clean set of values.
     */
    private void shutdown() {
        mLock.lock();
        try {
            if (!mIsShutdown) {
                mIsShutdown = true;
                mLogger.i("Shutting down the device lifecycle for: " + mDeviceKey);
                mOpenDevice = null;
                mDeviceState = DeviceState.CLOSED;
                mTargetState = TargetState.CLOSED;

                closeRequest();
                mDeviceLifetime.close();
                mShutdownListener.onShutdown(mDeviceKey);
            } else {
                mLogger.w("Shutdown was called multiple times!");
            }
        } finally {
            mLock.unlock();
        }
    }
}
