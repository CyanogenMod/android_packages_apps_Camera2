package com.android.camera.data;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.bumptech.glide.loader.bitmap.model.file_descriptor.FileDescriptorModelLoader;
import com.bumptech.glide.loader.bitmap.resource.FileDescriptorLocalUriFetcher;
import com.bumptech.glide.loader.bitmap.resource.ResourceFetcher;

import java.io.File;

/**
 * Translates a video data into an InputStream for the Glide library.
 */
public class VideoModelLoader implements FileDescriptorModelLoader<LocalMediaData.VideoData> {
    private final Context context;

    public VideoModelLoader(Context context) {
        this.context = context;
    }

    @Override
    public ResourceFetcher<ParcelFileDescriptor> getResourceFetcher(LocalMediaData.VideoData model,
            int width, int height) {
        return new UriFetcherWithId(context, getUri(model), model.getSignature());
    }

    public String getId(LocalMediaData.VideoData model) {
        return model.getPath() + model.getSignature();
    }

    private static Uri getUri(LocalData model) {
        return Uri.fromFile(new File(nonNull(model.getPath())));
    }

    private static String nonNull(String input) {
        return (input != null) ? input : "";
    }

    private static class UriFetcherWithId implements ResourceFetcher<ParcelFileDescriptor> {

        private final FileDescriptorLocalUriFetcher uriFetcher;
        private final String signature;

        public UriFetcherWithId(Context context, Uri uri, String signature) {
            uriFetcher = new FileDescriptorLocalUriFetcher(context, uri);
            this.signature = signature;
        }

        @Override
        public ParcelFileDescriptor loadResource() throws Exception {
            return uriFetcher.loadResource();
        }

        @Override
        public String getId() {
            return uriFetcher.getId() + signature;
        }

        @Override
        public void cancel() {
            uriFetcher.cancel();
        }
    }
}
