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

package com.android.camera.debug;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.view.SurfaceHolder;

import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraCapabilities;
import com.android.ex.camera2.portability.CameraDeviceInfo;
import com.android.ex.camera2.portability.CameraSettings;
import com.android.ex.camera2.portability.CameraStateHolder;
import com.android.ex.camera2.portability.DispatchThread;

/**
 * A {@link com.android.ex.camera2.portability.CameraAgent.CameraProxy} which wraps the
 * other and adds logs for all operations.
 */
public class DebugCameraProxy extends CameraAgent.CameraProxy {
    private final Log.Tag mTag;
    private final CameraAgent.CameraProxy mProxy;

    /**
     * Constructor.
     *
     * @param tag The tag to be used for logs.
     * @param proxy The camera proxy to be wrapped.
     */
    public DebugCameraProxy(Log.Tag tag, CameraAgent.CameraProxy proxy) {
        mTag = tag;
        mProxy = proxy;
    }

    @Override
    public Camera getCamera() {
        log("getCamera");
        return mProxy.getCamera();
    }

    @Override
    public int getCameraId() {
        log("getCameraId: " + mProxy.getCameraId());
        return mProxy.getCameraId();
    }

    @Override
    public CameraDeviceInfo.Characteristics getCharacteristics() {
        log("getCharacteristics");
        return mProxy.getCharacteristics();
    }

    @Override
    public CameraAgent getAgent() {
        log("getAgent");
        return mProxy.getAgent();
    }

    @Override
    public CameraCapabilities getCapabilities() {
        log("getCapabilities");
        return mProxy.getCapabilities();
    }

    @Override
    public void reconnect(Handler handler, CameraAgent.CameraOpenCallback cb) {
        log("reconnect");
        mProxy.reconnect(handler, cb);
    }

    @Override
    public void unlock() {
        log("unlock");
        mProxy.unlock();
    }

    @Override
    public void lock() {
        log("lock");
        mProxy.lock();
    }

    @Override
    public void setPreviewTexture(SurfaceTexture surfaceTexture) {
        log("setPreviewTexture");
        mProxy.setPreviewTexture(surfaceTexture);
    }

    @Override
    public void setPreviewTextureSync(SurfaceTexture surfaceTexture) {
        log("setPreviewTextureSync");
        mProxy.setPreviewTextureSync(surfaceTexture);
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder surfaceHolder) {
        log("setPreviewDisplay");
        mProxy.setPreviewDisplay(surfaceHolder);
    }

    @Override
    public void startPreview() {
        log("startPreview");
        mProxy.startPreview();
    }

    @Override
    public void startPreviewWithCallback(Handler h, CameraAgent.CameraStartPreviewCallback cb) {
        log("startPreviewWithCallback");
        mProxy.startPreviewWithCallback(h, cb);
    }

    @Override
    public void stopPreview() {
        log("stopPreview");
        mProxy.stopPreview();
    }

    @Override
    public void setPreviewDataCallback(Handler handler,
            CameraAgent.CameraPreviewDataCallback cb) {
        log("setPreviewDataCallback");
        mProxy.setPreviewDataCallback(handler, cb);
    }

    @Override
    public void setOneShotPreviewCallback(Handler handler,
            CameraAgent.CameraPreviewDataCallback cb) {
        log("setOneShotPreviewCallback");
        mProxy.setOneShotPreviewCallback(handler, cb);
    }

    @Override
    public void setPreviewDataCallbackWithBuffer(Handler handler,
            CameraAgent.CameraPreviewDataCallback cb) {
        log("setPreviewDataCallbackWithBuffer");
        mProxy.setPreviewDataCallbackWithBuffer(handler, cb);
    }

    @Override
    public void addCallbackBuffer(byte[] callbackBuffer) {
        log("addCallbackBuffer");
        mProxy.addCallbackBuffer(callbackBuffer);
    }

    @Override
    public void autoFocus(Handler handler, CameraAgent.CameraAFCallback cb) {
        log("autoFocus");
        mProxy.autoFocus(handler, cb);
    }

    @Override
    public void cancelAutoFocus() {
        log("cancelAutoFocus");
        mProxy.cancelAutoFocus();
    }

    @Override
    public void setAutoFocusMoveCallback(Handler handler, CameraAgent.CameraAFMoveCallback cb) {
        log("setAutoFocusMoveCallback");
        mProxy.setAutoFocusMoveCallback(handler, cb);
    }

    @Override
    public void takePicture(Handler handler, CameraAgent.CameraShutterCallback shutter,
            CameraAgent.CameraPictureCallback raw, CameraAgent.CameraPictureCallback postview,
            CameraAgent.CameraPictureCallback jpeg) {
        log("takePicture");
        mProxy.takePicture(handler, shutter, raw, postview, jpeg);
    }

    @Override
    public void setDisplayOrientation(int degrees) {
        log("setDisplayOrientation:" + degrees);
        mProxy.setDisplayOrientation(degrees);
    }

    @Override
    public void setZoomChangeListener(Camera.OnZoomChangeListener listener) {
        log("setZoomChangeListener");
        mProxy.setZoomChangeListener(listener);
    }

    @Override
    public void setFaceDetectionCallback(Handler handler,
            CameraAgent.CameraFaceDetectionCallback callback) {
        log("setFaceDetectionCallback");
        mProxy.setFaceDetectionCallback(handler, callback);
    }

    @Override
    public void startFaceDetection() {
        log("startFaceDetection");
        mProxy.startFaceDetection();
    }

    @Override
    public void stopFaceDetection() {
        log("stopFaceDetection");
        mProxy.stopFaceDetection();
    }

    @Override
    public void setParameters(Camera.Parameters params) {
        log("setParameters");
        mProxy.setParameters(params);
    }

    @Override
    public Camera.Parameters getParameters() {
        log("getParameters");
        return mProxy.getParameters();
    }

    @Override
    public CameraSettings getSettings() {
        log("getSettings");
        return mProxy.getSettings();
    }

    @Override
    public boolean applySettings(final CameraSettings settings) {
        log("applySettings");
        return mProxy.applySettings(settings);
    }

    @Override
    public void refreshSettings() {
        log("refreshParameters");
        mProxy.refreshSettings();
    }

    @Override
    public void enableShutterSound(boolean enable) {
        log("enableShutterSound:" + enable);
        mProxy.enableShutterSound(enable);
    }

    @Override
    public String dumpDeviceSettings() {
        log("dumpDeviceSettings");
        return mProxy.dumpDeviceSettings();
    }

    @Override
    public Handler getCameraHandler() {
        return mProxy.getCameraHandler();
    }

    @Override
    public DispatchThread getDispatchThread() {
        return mProxy.getDispatchThread();
    }

    @Override
    public CameraStateHolder getCameraState() {
        return mProxy.getCameraState();
    }

    private void log(String msg) {
        Log.v(mTag, msg);
    }
}
