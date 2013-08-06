package com.android.camera.app;

import android.app.Application;

public class CameraApp extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		com.android.camera.Util.initialize(this);
	}
}
