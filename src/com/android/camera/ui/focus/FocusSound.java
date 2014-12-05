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

package com.android.camera.ui.focus;

import com.android.camera.SoundPlayer;

/**
 * Wraps the focus sound and the player into a single object that can
 * be played on demand.
 *
 * TODO: This needs some way to better manage the sound lifetimes
 */
public class FocusSound {
    private static final float DEFAULT_VOLUME = 0.6f;
    private final SoundPlayer mPlayer;
    private final int mSoundId;
    public FocusSound(SoundPlayer player, int soundId) {
        mPlayer = player;
        mSoundId = soundId;

        mPlayer.loadSound(mSoundId);
    }

    /**
     * Play the focus sound with the sound player at the default
     * volume.
     */
    public void play() {
        if(!mPlayer.isReleased()) {
            mPlayer.play(mSoundId, DEFAULT_VOLUME);
        }
    }
}
