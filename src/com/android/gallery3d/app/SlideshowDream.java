package com.android.gallery3d.app;

import android.app.Activity;
import android.content.Intent;
import android.support.v13.dreams.BasicDream;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageView;
import android.widget.ViewFlipper;

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
                .setFlags(getIntent().getFlags());
        startActivity(i);
        finish();
    }
}
