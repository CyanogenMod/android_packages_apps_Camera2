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

package com.android.gallery3d.ui;

import com.android.gallery3d.glrenderer.GLCanvas;

class GLViewMock extends GLView {
    // onAttachToRoot
    int mOnAttachCalled;
    GLRoot mRoot;
    // onDetachFromRoot
    int mOnDetachCalled;
    // onVisibilityChanged
    int mOnVisibilityChangedCalled;
    // onLayout
    int mOnLayoutCalled;
    boolean mOnLayoutChangeSize;
    // renderBackground
    int mRenderBackgroundCalled;
    // onMeasure
    int mOnMeasureCalled;
    int mOnMeasureWidthSpec;
    int mOnMeasureHeightSpec;

    @Override
    public void onAttachToRoot(GLRoot root) {
        mRoot = root;
        mOnAttachCalled++;
        super.onAttachToRoot(root);
    }

    @Override
    public void onDetachFromRoot() {
        mRoot = null;
        mOnDetachCalled++;
        super.onDetachFromRoot();
    }

    @Override
    protected void onVisibilityChanged(int visibility) {
        mOnVisibilityChangedCalled++;
    }

    @Override
    protected void onLayout(boolean changeSize, int left, int top,
            int right, int bottom) {
        mOnLayoutCalled++;
        mOnLayoutChangeSize = changeSize;
        // call children's layout.
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView item = getComponent(i);
            item.layout(left, top, right, bottom);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        mOnMeasureCalled++;
        mOnMeasureWidthSpec = widthSpec;
        mOnMeasureHeightSpec = heightSpec;
        // call children's measure.
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView item = getComponent(i);
            item.measure(widthSpec, heightSpec);
        }
        setMeasuredSize(widthSpec, heightSpec);
    }

    @Override
    protected void renderBackground(GLCanvas view) {
        mRenderBackgroundCalled++;
    }
}
