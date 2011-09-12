/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.photoeditor.actions;

import android.content.Context;
import android.util.AttributeSet;

import com.android.gallery3d.photoeditor.filters.FlipFilter;

/**
 * An action handling flip effect.
 */
public class FlipAction extends EffectAction {

    private boolean flipHorizontal;
    private boolean flipVertical;
    private TouchView touchView;

    public FlipAction(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void doBegin() {
        final FlipFilter filter = new FlipFilter();

        touchView = factory.createTouchView();
        touchView.setSwipeListener(new TouchView.SwipeListener() {

            @Override
            public void onSwipeDown() {
                flipFilterVertically(filter);
            }

            @Override
            public void onSwipeLeft() {
                flipFilterHorizontally(filter);
            }

            @Override
            public void onSwipeRight() {
                flipFilterHorizontally(filter);
            }

            @Override
            public void onSwipeUp() {
                flipFilterVertically(filter);
            }
        });

        flipHorizontal = false;
        flipVertical = false;
        flipFilterHorizontally(filter);
    }

    @Override
    public void doEnd() {
        touchView.setSwipeListener(null);
    }

    private void flipFilterHorizontally(final FlipFilter filter) {
        flipHorizontal = !flipHorizontal;
        filter.setFlip(flipHorizontal, flipVertical);
        notifyFilterChanged(filter, true);
    }

    private void flipFilterVertically(final FlipFilter filter) {
        flipVertical = !flipVertical;
        filter.setFlip(flipHorizontal, flipVertical);
        notifyFilterChanged(filter, true);
    }
}
