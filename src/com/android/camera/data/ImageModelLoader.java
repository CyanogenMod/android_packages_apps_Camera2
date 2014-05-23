package com.android.camera.data;

import android.content.Context;
import android.net.Uri;
import com.bumptech.glide.loader.bitmap.model.stream.StreamModelLoader;
import com.bumptech.glide.loader.bitmap.resource.ResourceFetcher;
import com.bumptech.glide.loader.bitmap.resource.StreamLocalUriFetcher;

import java.io.File;
import java.io.InputStream;

/**
 * Translates a local data representing an image into an InputStream that can be loaded by the
 * Glide library.
 */
public class ImageModelLoader implements StreamModelLoader<LocalData> {
    private final Context context;

    public ImageModelLoader(Context context) {
        this.context = context;
    }

    @Override
    public ResourceFetcher<InputStream> getResourceFetcher(LocalData model, int width, int height) {
        return new UriFetcherWithId(context, getUri(model), model.getSignature());
    }

    @Override
    public String getId(LocalData model) {
        return model.getPath() + model.getSignature();
    }

    private static Uri getUri(LocalData model) {
        return Uri.fromFile(new File(nonNull(model.getPath())));
    }

    private static String nonNull(String input) {
        return (input != null) ? input : "";
    }

    private static class UriFetcherWithId implements ResourceFetcher<InputStream> {

        private final StreamLocalUriFetcher uriFetcher;
        private final String signature;

        public UriFetcherWithId(Context context, Uri uri, String signature) {
            uriFetcher = new StreamLocalUriFetcher(context, uri);
            this.signature = signature;
        }

        @Override
        public InputStream loadResource() throws Exception {
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
