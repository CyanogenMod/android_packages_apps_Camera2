/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.photos.data;

import android.graphics.Bitmap;
import android.util.SparseArray;

import android.util.Pools.Pool;

public class SparseArrayBitmapPool {

    private static final int BITMAPS_TO_KEEP_AFTER_UNNEEDED_HINT = 4;
    private int mCapacityBytes;
    private SparseArray<Node> mStore = new SparseArray<Node>();
    private int mSizeBytes = 0;

    private Pool<Node> mNodePool;
    private Node mPoolNodesHead = null;
    private Node mPoolNodesTail = null;

    protected static class Node {
        Bitmap bitmap;
        Node prevInBucket;
        Node nextInBucket;
        Node nextInPool;
        Node prevInPool;
    }

    public SparseArrayBitmapPool(int capacityBytes, Pool<Node> nodePool) {
        mCapacityBytes = capacityBytes;
        mNodePool = nodePool;
    }

    public synchronized void setCapacity(int capacityBytes) {
        mCapacityBytes = capacityBytes;
        freeUpCapacity(0);
    }

    private void freeUpCapacity(int bytesNeeded) {
        int targetSize = mCapacityBytes - bytesNeeded;
        while (mPoolNodesTail != null && mSizeBytes > targetSize) {
            unlinkAndRecycleNode(mPoolNodesTail, true);
        }
    }

    private void unlinkAndRecycleNode(Node n, boolean recycleBitmap) {
        // Remove the node from its spot in its bucket
        if (n.prevInBucket != null) {
            n.prevInBucket.nextInBucket = n.nextInBucket;
        } else {
            mStore.put(n.bitmap.getWidth(), n.nextInBucket);
        }
        if (n.nextInBucket != null) {
            n.nextInBucket.prevInBucket = n.prevInBucket;
        }

        // Remove the node from its spot in the list of pool nodes
        if (n.prevInPool != null) {
            n.prevInPool.nextInPool = n.nextInPool;
        } else {
            mPoolNodesHead = n.nextInPool;
        }
        if (n.nextInPool != null) {
            n.nextInPool.prevInPool = n.prevInPool;
        } else {
            mPoolNodesTail = n.prevInPool;
        }

        // Recycle the node
        n.nextInBucket = null;
        n.nextInPool = null;
        n.prevInBucket = null;
        n.prevInPool = null;
        mSizeBytes -= n.bitmap.getByteCount();
        if (recycleBitmap) n.bitmap.recycle();
        n.bitmap = null;
        mNodePool.release(n);
    }

    public synchronized int getCapacity() {
        return mCapacityBytes;
    }

    public synchronized int getSize() {
        return mSizeBytes;
    }

    public synchronized Bitmap get(int width, int height) {
        Node cur = mStore.get(width);
        while (cur != null) {
            if (cur.bitmap.getHeight() == height) {
                Bitmap b = cur.bitmap;
                unlinkAndRecycleNode(cur, false);
                return b;
            }
            cur = cur.nextInBucket;
        }
        return null;
    }

    public synchronized boolean put(Bitmap b) {
        if (b == null) {
            return false;
        }
        int bytes = b.getByteCount();
        freeUpCapacity(bytes);
        Node newNode = mNodePool.acquire();
        if (newNode == null) {
            newNode = new Node();
        }
        newNode.bitmap = b;
        newNode.prevInBucket = null;
        newNode.prevInPool = null;
        newNode.nextInPool = mPoolNodesHead;
        mPoolNodesHead = newNode;
        int key = b.getWidth();
        newNode.nextInBucket = mStore.get(key);
        if (newNode.nextInBucket != null) {
            newNode.nextInBucket.prevInBucket = newNode;
        }
        mStore.put(key, newNode);
        if (newNode.nextInPool == null) {
            mPoolNodesTail = newNode;
        } else {
            newNode.nextInPool.prevInPool = newNode;
        }
        mSizeBytes += bytes;
        return true;
    }

    public synchronized void clear() {
        freeUpCapacity(mCapacityBytes);
    }
}
