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

package com.android.camera.stats;

import android.graphics.Rect;
import android.hardware.camera2.params.Face;

/**
 * Wraps the Camera2 required class to insulate Kit-Kat devices from Camera2 API
 * contamination.
 */
public class Camera2FaceProxy {
    private final Rect mFaceRect;
    private final int mScore;

    public Camera2FaceProxy(Rect faceRect, int score) {
        mFaceRect = faceRect;
        mScore = score;
    }

    public static Camera2FaceProxy from(Face face) {
        Camera2FaceProxy convertedFace = new Camera2FaceProxy(face.getBounds(), face.getScore());
        return convertedFace;
    }

    public Rect getFaceRect() {
        return mFaceRect;
    }

    public int getScore() {
        return mScore;
    }
}
