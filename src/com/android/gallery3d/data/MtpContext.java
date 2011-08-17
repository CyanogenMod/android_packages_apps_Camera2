package com.android.gallery3d.data;

import com.android.gallery3d.R;
import com.android.gallery3d.util.GalleryUtils;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.mtp.MtpObjectInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MtpContext implements MtpClient.Listener {
    private static final String TAG = "MtpContext";

    public static final String NAME_IMPORTED_FOLDER = "Imported";

    private ScannerClient mScannerClient;
    private Context mContext;
    private MtpClient mClient;

    private static final class ScannerClient implements MediaScannerConnectionClient {
        ArrayList<String> mPaths = new ArrayList<String>();
        MediaScannerConnection mScannerConnection;
        boolean mConnected;
        Object mLock = new Object();

        public ScannerClient(Context context) {
            mScannerConnection = new MediaScannerConnection(context, this);
        }

        public void scanPath(String path) {
            synchronized (mLock) {
                if (mConnected) {
                    mScannerConnection.scanFile(path, null);
                } else {
                    mPaths.add(path);
                    mScannerConnection.connect();
                }
            }
        }

        @Override
        public void onMediaScannerConnected() {
            synchronized (mLock) {
                mConnected = true;
                if (!mPaths.isEmpty()) {
                    for (String path : mPaths) {
                        mScannerConnection.scanFile(path, null);
                    }
                    mPaths.clear();
                }
            }
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
        }
    }

    public MtpContext(Context context) {
        mContext = context;
        mScannerClient = new ScannerClient(context);
        mClient = new MtpClient(mContext);
    }

    public void pause() {
        mClient.removeListener(this);
    }

    public void resume() {
        mClient.addListener(this);
        notifyDirty();
    }

    public void deviceAdded(android.mtp.MtpDevice device) {
        notifyDirty();
        showToast(R.string.camera_connected);
    }

    public void deviceRemoved(android.mtp.MtpDevice device) {
        notifyDirty();
        showToast(R.string.camera_disconnected);
    }

    private void notifyDirty() {
        mContext.getContentResolver().notifyChange(Uri.parse("mtp://"), null);
    }

    private void showToast(final int msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    public MtpClient getMtpClient() {
        return mClient;
    }

    public boolean copyFile(String deviceName, MtpObjectInfo objInfo) {
        if (GalleryUtils.hasSpaceForSize(objInfo.getCompressedSize())) {
            File dest = Environment.getExternalStorageDirectory();
            dest = new File(dest, NAME_IMPORTED_FOLDER);
            dest.mkdirs();
            String destPath = new File(dest, objInfo.getName()).getAbsolutePath();
            int objectId = objInfo.getObjectHandle();
            if (mClient.importFile(deviceName, objectId, destPath)) {
                mScannerClient.scanPath(destPath);
                return true;
            }
        } else {
            Log.w(TAG, "No space to import " + objInfo.getName() +
                    " whose size = " + objInfo.getCompressedSize());
        }
        return false;
    }

    public boolean copyAlbum(String deviceName, String albumName,
            List<MtpObjectInfo> children) {
        File dest = Environment.getExternalStorageDirectory();
        dest = new File(dest, albumName);
        dest.mkdirs();
        int success = 0;
        for (MtpObjectInfo child : children) {
            if (!GalleryUtils.hasSpaceForSize(child.getCompressedSize())) continue;

            File importedFile = new File(dest, child.getName());
            String path = importedFile.getAbsolutePath();
            if (mClient.importFile(deviceName, child.getObjectHandle(), path)) {
                mScannerClient.scanPath(path);
                success++;
            }
        }
        return success == children.size();
    }
}
