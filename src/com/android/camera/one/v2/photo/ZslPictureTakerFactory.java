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

package com.android.camera.one.v2.photo;

import java.util.concurrent.Executor;

import com.android.camera.async.BufferQueue;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.sharedimagereader.ImageStreamFactory;

public class ZslPictureTakerFactory {
    private final PictureTakerImpl mPictureTaker;

    public ZslPictureTakerFactory(Executor mainExecutor,
                                  CameraCommandExecutor commandExecutor,
                                  ImageSaver.Builder imageSaverBuilder,
                                  FrameServer frameServer,
                                  RequestBuilder.Factory rootRequestBuilder,
                                  ImageStreamFactory sharedImageReader,
                                  BufferQueue<ImageProxy> ringBuffer) {
        ImageCaptureCommand fallbackFlashOffCommand = new SimpleImageCaptureCommand(frameServer,
                rootRequestBuilder, sharedImageReader);
        ImageCaptureCommand flashOffCommand = new ZslImageCaptureCommand(ringBuffer,
                fallbackFlashOffCommand);
        // TODO FIXME Implement flash
        ImageCaptureCommand flashOnCommand = flashOffCommand;
        ImageCaptureCommand flashAutoCommand = flashOffCommand;
        mPictureTaker = new PictureTakerImpl(mainExecutor, commandExecutor, imageSaverBuilder,
                flashOffCommand, flashOnCommand, flashAutoCommand);
    }

    public PictureTaker providePictureTaker() {
        return mPictureTaker;
    }
}
