/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.data;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.provider.GalleryProvider;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.hardware.usb.UsbDevice;
import android.mtp.MtpObjectInfo;
import android.net.Uri;
import android.util.Log;

import java.text.DateFormat;
import java.util.Date;

public class MtpImage extends MediaItem {
    private static final String TAG = "MtpImage";

    private final int mDeviceId;
    private int mObjectId;
    private int mObjectSize;
    private long mDateTaken;
    private String mFileName;
    private final ThreadPool mThreadPool;
    private final MtpContext mMtpContext;
    private final MtpObjectInfo mObjInfo;
    private final int mImageWidth;
    private final int mImageHeight;
    private final Context mContext;

    MtpImage(Path path, GalleryApp application, int deviceId,
            MtpObjectInfo objInfo, MtpContext mtpContext) {
        super(path, nextVersionNumber());
        mContext = application.getAndroidContext();
        mDeviceId = deviceId;
        mObjInfo = objInfo;
        mObjectId = objInfo.getObjectHandle();
        mObjectSize = objInfo.getCompressedSize();
        mDateTaken = objInfo.getDateCreated();
        mFileName = objInfo.getName();
        mImageWidth = objInfo.getImagePixWidth();
        mImageHeight = objInfo.getImagePixHeight();
        mThreadPool = application.getThreadPool();
        mMtpContext = mtpContext;
    }

    MtpImage(Path path, GalleryApp app, int deviceId, int objectId, MtpContext mtpContext) {
        this(path, app, deviceId, MtpDevice.getObjectInfo(mtpContext, deviceId, objectId),
                mtpContext);
    }

    @Override
    public long getDateInMs() {
        return mDateTaken;
    }

    @Override
    public Job<Bitmap> requestImage(int type) {
        return new Job<Bitmap>() {
            public Bitmap run(JobContext jc) {
                byte[] thumbnail = mMtpContext.getMtpClient().getThumbnail(
                        UsbDevice.getDeviceName(mDeviceId), mObjectId);
                if (thumbnail == null) {
                    Log.w(TAG, "decoding thumbnail failed");
                    return null;
                }
                return DecodeUtils.requestDecode(jc, thumbnail, null);
            }
        };
    }

    @Override
    public Job<BitmapRegionDecoder> requestLargeImage() {
        return new Job<BitmapRegionDecoder>() {
            public BitmapRegionDecoder run(JobContext jc) {
                byte[] bytes = mMtpContext.getMtpClient().getObject(
                        UsbDevice.getDeviceName(mDeviceId), mObjectId, mObjectSize);
                return DecodeUtils.requestCreateBitmapRegionDecoder(
                        jc, bytes, 0, bytes.length, false);
            }
        };
    }

    public byte[] getImageData() {
        return mMtpContext.getMtpClient().getObject(
                UsbDevice.getDeviceName(mDeviceId), mObjectId, mObjectSize);
    }

    @Override
    public boolean Import() {
        return mMtpContext.copyFile(UsbDevice.getDeviceName(mDeviceId), mObjInfo);
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_FULL_IMAGE | SUPPORT_IMPORT;
    }

    public void updateContent(MtpObjectInfo info) {
        if (mObjectId != info.getObjectHandle() || mDateTaken != info.getDateCreated()) {
            mObjectId = info.getObjectHandle();
            mDateTaken = info.getDateCreated();
            mDataVersion = nextVersionNumber();
        }
    }

    @Override
    public String getMimeType() {
        // Currently only JPEG is supported in MTP.
        return "image/jpeg";
    }

    @Override
    public int getMediaType() {
        return MEDIA_TYPE_IMAGE;
    }

    @Override
    public long getSize() {
        return mObjectSize;
    }

    @Override
    public Uri getContentUri() {
        return GalleryProvider.getUriFor(mContext, mPath);
    }

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        DateFormat formater = DateFormat.getDateTimeInstance();
        details.addDetail(MediaDetails.INDEX_TITLE, mFileName);
        details.addDetail(MediaDetails.INDEX_DATETIME, formater.format(new Date(mDateTaken)));
        details.addDetail(MediaDetails.INDEX_WIDTH, mImageWidth);
        details.addDetail(MediaDetails.INDEX_HEIGHT, mImageHeight);
        details.addDetail(MediaDetails.INDEX_SIZE, Long.valueOf(mObjectSize));
        return details;
    }

    @Override
    public int getWidth() {
        return mImageWidth;
    }

    @Override
    public int getHeight() {
        return mImageHeight;
    }
}
