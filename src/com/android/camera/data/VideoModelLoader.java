package com.android.camera.data;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import com.bumptech.glide.Glide;
import com.bumptech.glide.loader.bitmap.model.ModelLoader;
import com.bumptech.glide.loader.bitmap.model.file_descriptor.FileDescriptorModelLoader;
import com.bumptech.glide.loader.bitmap.resource.ResourceFetcher;

import java.io.File;

/**
 * Translates a video data into an InputStream for the Glide library.
 */
public class VideoModelLoader implements FileDescriptorModelLoader<LocalMediaData.VideoData> {
    private final ModelLoader<File, ParcelFileDescriptor> mPathLoader;

    public VideoModelLoader(Context context) {
        mPathLoader = Glide.buildFileDescriptorModelLoader(File.class, context);
    }

    @Override
    public ResourceFetcher<ParcelFileDescriptor> getResourceFetcher(LocalMediaData.VideoData model,
            int width, int height) {
        return mPathLoader.getResourceFetcher(new File(nonNull(model.getPath())), width, height);
    }

    public String getId(LocalMediaData.VideoData model) {
        return mPathLoader.getId(new File(nonNull(model.getPath()))) + model.getSignature();
    }

    private static String nonNull(String input) {
        return (input != null) ? input : "";
    }
}
