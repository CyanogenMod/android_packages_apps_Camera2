package com.android.gallery3d.photoeditor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.android.gallery3d.R;

/**
 * Alert dialog builder that builds a simple Yes/No/Cancel dialog.
 */
public class YesNoCancelDialogBuilder extends AlertDialog.Builder {

    public YesNoCancelDialogBuilder(Context context, final Runnable yes, final Runnable no,
            int messageId) {
        super(context);
        setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                yes.run();
            }
        })
        .setNeutralButton(R.string.no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                no.run();
            }
        })
        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // no-op
            }
        }).setMessage(messageId);
    }
}
