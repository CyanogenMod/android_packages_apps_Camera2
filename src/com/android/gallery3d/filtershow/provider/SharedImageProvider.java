
package com.android.gallery3d.filtershow.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

public class SharedImageProvider extends ContentProvider {

    private static final String LOGTAG = "SharedImageProvider";

    public static final String MIME_TYPE = "image/jpeg";
    public static final String AUTHORITY = "com.android.gallery3d.filtershow.provider.SharedImageProvider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/image");
    public static final String PREPARE = "prepare";

    private final String[] mMimeStreamType = {
            MIME_TYPE
    };

    private static ConditionVariable mImageReadyCond = new ConditionVariable(false);

    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        return 0;
    }

    @Override
    public String getType(Uri arg0) {
        return MIME_TYPE;
    }

    @Override
    public String[] getStreamTypes(Uri arg0, String mimeTypeFilter) {
        return mMimeStreamType;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (values.containsKey(PREPARE)) {
            if (values.getAsBoolean(PREPARE)) {
                mImageReadyCond.close();
            } else {
                mImageReadyCond.open();
            }
        }
        return null;
    }

    @Override
    public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
        return 0;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String uriPath = uri.getLastPathSegment();
        if (uriPath == null) {
            return null;
        }
        if (projection == null) {
            projection = new String[] {
                    BaseColumns._ID,
                    MediaStore.MediaColumns.DATA,
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE
            };
        }
        // If we receive a query on display name or size,
        // we should block until the image is ready
        mImageReadyCond.block();

        File path = new File(uriPath);

        MatrixCursor cursor = new MatrixCursor(projection);
        Object[] columns = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (projection[i].equalsIgnoreCase(BaseColumns._ID)) {
                columns[i] = 0;
            } else if (projection[i].equalsIgnoreCase(MediaStore.MediaColumns.DATA)) {
                columns[i] = uri;
            } else if (projection[i].equalsIgnoreCase(OpenableColumns.DISPLAY_NAME)) {
                columns[i] = path.getName();
            } else if (projection[i].equalsIgnoreCase(OpenableColumns.SIZE)) {
                columns[i] = path.length();
            }
        }
        cursor.addRow(columns);

        return cursor;
    }

    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        String uriPath = uri.getLastPathSegment();
        if (uriPath == null) {
            return null;
        }
        // Here we need to block until the image is ready
        mImageReadyCond.block();
        File path = new File(uriPath);
        int imode = 0;
        imode |= ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(path, imode);
    }
}
