/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.settings;

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.FilteredUpdatable;
import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;
import com.android.camera.async.Updatable;
import com.android.camera.util.Callback;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

/**
 * Wraps a {@link SettingsManager} setting with thread-safe interfaces for
 * updating the value and observing changes.
 */
public class Setting<T> implements Updatable<T>, Observable<T>, SafeCloseable {
    /**
     * The SettingsManager instance to wrap.
     * <p>
     * Note that because many SettingsManager operations are not thread-safe at
     * all, access to it must is synchronized on the object, itself.
     */
    private final SettingsManager mSettingsManager;
    private final ConcurrentState<T> mState;
    private final Updatable<T> mFilteredState;
    private final SettingsManager.OnSettingChangedListener mOnSettingChangedListener;
    private final String mScope;
    private final String mKey;
    private final Class<T> mTClass;
    private final AtomicBoolean mClosed;

    private Setting(SettingsManager manager, String scope, String key, Class<T> tClass, T
            defaultValue) {
        mSettingsManager = manager;
        mScope = scope;
        mKey = key;
        mTClass = tClass;
        mState = new ConcurrentState<>(defaultValue);
        mClosed = new AtomicBoolean(false);

        // Wrap mState to only dispatch to listeners when the value actually
        // changes.
        mFilteredState = new FilteredUpdatable<>(mState);

        mOnSettingChangedListener = new SettingsManager.OnSettingChangedListener() {
            @Override
            public void onSettingChanged(SettingsManager settingsManager, String key) {
                updateFromSettings();
            }
        };

        synchronized (mSettingsManager) {
            mSettingsManager.addListener(mOnSettingChangedListener);
        }
    }

    public static Setting<Integer> createForInteger(SettingsManager manager,
            String scope, String key) {
        Integer defaultValue = manager.getIntegerDefault(key);
        return new Setting<Integer>(manager, scope, key, Integer.class, defaultValue);
    }

    public static Setting<String> createForString(SettingsManager manager,
            String scope, String key) {
        String defaultValue = manager.getStringDefault(key);
        return new Setting<String>(manager, scope, key, String.class, defaultValue);
    }

    public static Setting<Boolean> createForBoolean(SettingsManager manager,
            String scope, String key) {
        Boolean defaultValue = manager.getBooleanDefault(key);
        return new Setting<Boolean>(manager, scope, key, Boolean.class, defaultValue);
    }

    @Override
    public SafeCloseable addCallback(Callback callback, Executor executor) {
        return mState.addCallback(callback, executor);
    }

    private void updateFromSettings() {
        mFilteredState.update(getFromSettings());
    }

    private T getFromSettings() {
        synchronized (mSettingsManager) {
            if (mTClass.equals(Integer.class)) {
                return (T) mSettingsManager.getInteger(mScope, mKey);
            } else if (mTClass.equals(String.class)) {
                return (T) mSettingsManager.getString(mScope, mKey);
            } else if (mTClass.equals(Boolean.class)) {
                return (T) new Boolean(mSettingsManager.getBoolean(mScope, mKey));
            }
        }
        return null;
    }

    @Override
    public T get() {
        return getFromSettings();
    }

    @Override
    public void update(@Nonnull T t) {
        synchronized (mSettingsManager) {
            if (mTClass.equals(Integer.class)) {
                mSettingsManager.set(mScope, mKey, (Integer) t);
            } else if (mTClass.equals(String.class)) {
                mSettingsManager.set(mScope, mKey, (String) t);
            } else if (mTClass.equals(Boolean.class)) {
                mSettingsManager.set(mScope, mKey, (Boolean) t);
            }
        }
    }

    @Override
    public void close() {
        if (mClosed.getAndSet(true)) {
            return;
        }
        synchronized (mSettingsManager) {
            mSettingsManager.removeListener(mOnSettingChangedListener);
        }
    }
}
