package com.android.camera.data;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import com.bumptech.glide.Glide;
import com.bumptech.glide.loader.bitmap.model.ModelLoader;
import com.bumptech.glide.loader.bitmap.model.file_descriptor.FileDescriptorModelLoader;
import com.bumptech.glide.loader.bitmap.resource.ResourceFetcher;

/**
 * Translates a video data into an InputStream for the Glide library.
 */
public class VideoModelLoader implements FileDescriptorModelLoader<LocalMediaData.VideoData> {
    private final ModelLoader<String, ParcelFileDescriptor> mPathLoader;

    public VideoModelLoader(Context context) {
        mPathLoader = Glide.buildFileDescriptorModelLoader(String.class, context);
    }

    @Override
    public ResourceFetcher<ParcelFileDescriptor> getResourceFetcher(LocalMediaData.VideoData model,
            int width, int height) {
        return mPathLoader.getResourceFetcher(model.getPath(), width, height);
    }

    public String getId(LocalMediaData.VideoData model) {
        return mPathLoader.getId(model.getPath()) + model.getSignature();
    }
}
