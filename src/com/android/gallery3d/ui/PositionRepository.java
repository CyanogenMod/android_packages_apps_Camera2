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

package com.android.gallery3d.ui;

import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;

import java.util.HashMap;
import java.util.WeakHashMap;

public class PositionRepository {
    private static final WeakHashMap<GalleryActivity, PositionRepository>
            sMap = new WeakHashMap<GalleryActivity, PositionRepository>();

    public static class Position implements Cloneable {
        public float x;
        public float y;
        public float z;
        public float theta;
        public float alpha;

        public Position() {
        }

        public Position(float x, float y, float z) {
            this(x, y, z, 0f, 1f);
        }

        public Position(float x, float y, float z, float ftheta, float alpha) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.theta = ftheta;
            this.alpha = alpha;
        }

        @Override
        public Position clone() {
            try {
                return (Position) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(); // we do support clone.
            }
        }

        public void set(Position another) {
            x = another.x;
            y = another.y;
            z = another.z;
            theta = another.theta;
            alpha = another.alpha;
        }

        public void set(float x, float y, float z, float ftheta, float alpha) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.theta = ftheta;
            this.alpha = alpha;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Position)) return false;
            Position position = (Position) object;
            return x == position.x && y == position.y && z == position.z
                    && theta == position.theta
                    && alpha == position.alpha;
        }

        public static void interpolate(
                Position source, Position target, Position output, float progress) {
            if (progress < 1f) {
                output.set(
                        Utils.interpolateScale(source.x, target.x, progress),
                        Utils.interpolateScale(source.y, target.y, progress),
                        Utils.interpolateScale(source.z, target.z, progress),
                        Utils.interpolateAngle(source.theta, target.theta, progress),
                        Utils.interpolateScale(source.alpha, target.alpha, progress));
            } else {
                output.set(target);
            }
        }
    }

    public static PositionRepository getInstance(GalleryActivity activity) {
        PositionRepository repository = sMap.get(activity);
        if (repository == null) {
            repository = new PositionRepository();
            sMap.put(activity, repository);
        }
        return repository;
    }

    private HashMap<Long, Position> mData = new HashMap<Long, Position>();
    private int mOffsetX;
    private int mOffsetY;
    private Position mTempPosition = new Position();

    public Position get(Long identity) {
        Position position = mData.get(identity);
        if (position == null) return null;
        mTempPosition.set(position);
        position = mTempPosition;
        position.x -= mOffsetX;
        position.y -= mOffsetY;
        return position;
    }

    public void setOffset(int offsetX, int offsetY) {
        mOffsetX = offsetX;
        mOffsetY = offsetY;
    }

    public void putPosition(Long identity, Position position) {
        Position clone = position.clone();
        clone.x += mOffsetX;
        clone.y += mOffsetY;
        mData.put(identity, clone);
    }

    public void clear() {
        mData.clear();
    }
}
