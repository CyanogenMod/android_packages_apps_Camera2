package com.android.camera.data;

import android.content.Context;
import com.bumptech.glide.Glide;
import com.bumptech.glide.loader.bitmap.model.ModelLoader;
import com.bumptech.glide.loader.bitmap.model.stream.StreamModelLoader;
import com.bumptech.glide.loader.bitmap.resource.ResourceFetcher;

import java.io.InputStream;

/**
 * Translates a local data representing an image into an InputStream that can be loaded by the
 * Glide library.
 */
public class ImageModelLoader implements StreamModelLoader<LocalData> {
    private final ModelLoader<String, InputStream> mPathLoader;

    public ImageModelLoader(Context context) {
        mPathLoader = Glide.buildStreamModelLoader(String.class, context);
    }

    @Override
    public ResourceFetcher<InputStream> getResourceFetcher(LocalData model, int width, int height) {
        return mPathLoader.getResourceFetcher(model.getPath(), width, height);
    }

    @Override
    public String getId(LocalData model) {
        return mPathLoader.getId(model.getPath()) + model.getSignature();
    }
}
