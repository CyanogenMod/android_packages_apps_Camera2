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

package com.android.camera.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * Enables thread-safe multiplexing of multiple input boolean states into a
 * single listener to be invoked upon change in the conjunction (logical AND) of
 * all inputs.
 */
public class ListenerCombiner<Input extends Enum<Input>> {
    /**
     * Callback for listening to changes to the conjunction of all inputs.
     */
    public static interface StateChangeListener {
        /**
         * Called whenever the conjunction of all inputs changes. Listeners MUST
         * NOT call {@link #setInput} while still registered as a listener, as
         * this will result in infinite recursion.
         *
         * @param state the conjunction of all input values.
         */
        public void onStateChange(boolean state);
    }

    /** Mutex for mValues and mState. */
    private final Object mLock = new Object();
    /** Stores the current input state. */
    private final EnumMap<Input, Boolean> mInputs;
    /** The current output state */
    private boolean mOutput;
    /**
     * The set of listeners to notify when the output (the conjunction of all
     * inputs) changes.
     */
    private final List<StateChangeListener> mListeners = Collections.synchronizedList(
            new ArrayList<StateChangeListener>());

    public void addListener(StateChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(StateChangeListener listener) {
        mListeners.remove(listener);
    }

    public boolean getOutput() {
        synchronized (mLock) {
            return mOutput;
        }
    }

    /**
     * Updates the state of the given input, dispatching to all output change
     * listeners if the output changes.
     *
     * @param index the index of the input to change.
     * @param newValue the new value of the input.
     * @return The new output.
     */
    public boolean setInput(Input input, boolean newValue) {
        synchronized (mLock) {
            mInputs.put(input, newValue);

            // If the new input value is the same as the existing output,
            // then nothing will change.
            if (newValue == mOutput) {
                return mOutput;
            } else {
                boolean oldOutput = mOutput;

                // Recompute the output by AND'ing all the inputs.
                mOutput = true;
                for (Boolean b : mInputs.values()) {
                    mOutput &= b;
                }

                // If the output has changed, notify the listeners.
                if (oldOutput != mOutput) {
                    notifyListeners();
                }

                return mOutput;
            }
        }
    }

    public ListenerCombiner(Class<Input> clazz, StateChangeListener listener) {
        this(clazz);
        addListener(listener);
    }

    public ListenerCombiner(Class<Input> clazz) {
        mInputs = new EnumMap<Input, Boolean>(clazz);

        for (Input i : clazz.getEnumConstants()) {
            mInputs.put(i, false);
        }

        mOutput = false;
    }

    /**
     * Notifies all listeners of the current state, regardless of whether or not
     * it has actually changed.
     */
    public void notifyListeners() {
        synchronized (mLock) {
            for (StateChangeListener listener : mListeners) {
                listener.onStateChange(mOutput);
            }
        }
    }
}
