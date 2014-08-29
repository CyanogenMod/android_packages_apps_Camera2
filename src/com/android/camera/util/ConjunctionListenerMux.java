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
import java.util.List;

/**
 * Enables thread-safe multiplexing of multiple input boolean states into a
 * single listener to be invoked upon change in the conjunction (logical AND) of
 * all inputs.
 */
public class ConjunctionListenerMux {
    /**
     * Callback for listening to changes to the conjunction of all inputs.
     */
    public static interface OutputChangeListener {
        /**
         * Called whenever the conjunction of all inputs changes. Listeners MUST
         * NOT call {@link #setInput} while still registered as a listener, as
         * this will result in infinite recursion.
         *
         * @param state the conjunction of all input values.
         */
        public void onOutputChange(boolean state);
    }

    /** Mutex for mValues and mState. */
    private final Object mLock = new Object();
    /** Stores the current input state. */
    private final ArrayList<Boolean> mInputs;
    /** The current output state */
    private boolean mOutput;
    /**
     * The set of listeners to notify when the output (the conjunction of all
     * inputs) changes.
     */
    private final List<OutputChangeListener> mListeners = Collections.synchronizedList(
            new ArrayList<OutputChangeListener>());

    public void addListener(OutputChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(OutputChangeListener listener) {
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
    public boolean setInput(int index, boolean newValue) {
        synchronized (mLock) {
            mInputs.set(index, newValue);

            // If the new input value is the same as the existing output,
            // then nothing will change.
            if (newValue == mOutput) {
                return mOutput;
            } else {
                boolean oldOutput = mOutput;

                // Recompute the output by AND'ing all the inputs.
                mOutput = true;
                for (Boolean b : mInputs) {
                    mOutput &= b;
                }

                // If the output has changed, notify the listeners.
                if (oldOutput != mOutput) {
                    for (OutputChangeListener listener : mListeners) {
                        listener.onOutputChange(mOutput);
                    }
                }

                return mOutput;
            }
        }
    }

    public ConjunctionListenerMux(int numInputs) {
        mInputs = new ArrayList<Boolean>(numInputs);
        for (int i = 0; i < numInputs; i++) {
            mInputs.add(false);
        }

        mOutput = false;
    }
}
