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

package com.android.camera.one.v2.sharedimagereader.imagedistributor;

import com.android.camera.async.RefCountBase;
import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Wraps ImageProxy with reference counting, starting with a fixed number of
 * references.
 */
@ThreadSafe
class RefCountedImageProxy extends ForwardingImageProxy {
    private final RefCountBase<ImageProxy> mRefCount;

    /**
     * @param image The image to wrap
     * @param refCount The initial reference count.
     */
    public RefCountedImageProxy(ImageProxy image, int refCount) {
        super(image);
        mRefCount = new RefCountBase<ImageProxy>(image, refCount);
    }

    @Override
    public void close() {
        mRefCount.close();
    }
}
