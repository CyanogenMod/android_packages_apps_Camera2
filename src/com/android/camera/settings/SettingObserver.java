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

package com.android.camera.settings;

import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;

import java.util.concurrent.Executor;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Wraps a {@link SettingsManager} setting with thread-safe interfaces for
 * observing changes.
 */
@ThreadSafe
public final class SettingObserver<T> implements Observable<T> {
    private class Listener implements SettingsManager.OnSettingChangedListener, SafeCloseable {
        private final Runnable mRunnable;
        private final Executor mExecutor;

        private Listener(Runnable runnable, Executor executor) {
            mRunnable = runnable;
            mExecutor = executor;
        }

        @Override
        public void onSettingChanged(SettingsManager settingsManager, String key) {
            mExecutor.execute(mRunnable);
        }

        @Override
        public void close() {
            mSettingsManager.removeListener(this);
        }
    }

    private final SettingsManager mSettingsManager;
    private final String mScope;
    private final String mKey;
    private final Class<T> mTClass;

    private SettingObserver(SettingsManager manager, String scope, String key, Class<T> tClass) {
        mSettingsManager = manager;
        mScope = scope;
        mKey = key;
        mTClass = tClass;
    }

    public static SettingObserver<Integer> ofInteger(SettingsManager manager,
            String scope, String key) {
        return new SettingObserver<>(manager, scope, key,
                Integer.class);
    }

    public static SettingObserver<String> ofString(SettingsManager manager,
            String scope, String key) {
        return new SettingObserver<>(manager, scope, key,
                String.class);
    }

    public static SettingObserver<Boolean> ofBoolean(SettingsManager manager,
            String scope, String key) {
        return new SettingObserver<>(manager, scope, key,
                Boolean.class);
    }

    @CheckReturnValue
    @Nonnull
    @Override
    public SafeCloseable addCallback(@Nonnull Runnable callback, @Nonnull Executor executor) {
        Listener listener = new Listener(callback, executor);
        mSettingsManager.addListener(listener);
        return listener;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        if (mTClass.equals(Integer.class)) {
            return (T) Integer.valueOf(mSettingsManager.getInteger(mScope, mKey));
        } else if (mTClass.equals(String.class)) {
            Object string = mSettingsManager.getString(mScope, mKey);
            if (string == null) {
                return null;
            } else {
                return (T) string;
            }
        } else if (mTClass.equals(Boolean.class)) {
            return (T) Boolean.valueOf(mSettingsManager.getBoolean(mScope, mKey));
        } else {
            // Impossible branch
            throw new IllegalStateException("T must be one of {Integer, Boolean, String}");
        }
    }
}
