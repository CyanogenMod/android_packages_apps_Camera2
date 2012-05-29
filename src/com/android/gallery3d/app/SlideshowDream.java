package com.android.gallery3d.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v13.dreams.BasicDream;

public class SlideshowDream extends BasicDream {
    @Override
    public void onCreate(Bundle bndl) {
        super.onCreate(bndl);
        Intent i = new Intent(
            Intent.ACTION_VIEW,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//            Uri.fromFile(Environment.getExternalStoragePublicDirectory(
//                        Environment.DIRECTORY_PICTURES)))
                .putExtra(Gallery.EXTRA_SLIDESHOW, true)
                .putExtra(Gallery.EXTRA_DREAM, true)
                .setFlags(getIntent().getFlags());
        startActivity(i);
        finish();
    }
}
