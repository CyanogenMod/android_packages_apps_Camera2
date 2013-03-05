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

package com.android.gallery3d.filtershow.filters;

import android.graphics.Path;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorDraw;

import java.util.Vector;

public class FilterDrawRepresentation extends FilterRepresentation {
    private static final String LOGTAG = "FilterDrawRepresentation";

    public static class StrokeData implements Cloneable {
        public byte mType;
        public Path mPath;
        public float mRadius;
        public int mColor;
        public int noPoints = 0;
        @Override
        public String toString() {
            return "stroke(" + mType + ", path(" + (mPath) + "), " + mRadius + " , "
                    + Integer.toHexString(mColor) + ")";
        }
        @Override
        public StrokeData clone() throws CloneNotSupportedException {
            return (StrokeData) super.clone();
        }
    }

    private Vector<StrokeData> mDrawing = new Vector<StrokeData>();
    private StrokeData mCurrent; // used in the currently drawing style

    public FilterDrawRepresentation() {
        super("Draw");
        setFilterClass(ImageFilterDraw.class);
        setPriority(FilterRepresentation.TYPE_VIGNETTE);
        setTextId(R.string.imageDraw);
        setButtonId(R.id.drawOnImageButton);
        setEditorId(EditorDraw.ID);
        setOverlayId(R.drawable.filtershow_drawing);
        setOverlayOnly(true);
    }

    @Override
    public String toString() {
        return getName() + " : strokes=" + mDrawing.size()
                + ((mCurrent == null) ? " no current "
                        : ("draw=" + mCurrent.mType + " " + mCurrent.noPoints));
    }

    public Vector<StrokeData> getDrawing() {
        return mDrawing;
    }

    public StrokeData getCurrentDrawing() {
        return mCurrent;
    }

    @Override
    public FilterRepresentation clone() throws CloneNotSupportedException {
        FilterDrawRepresentation representation = (FilterDrawRepresentation) super.clone();
        return representation;
    }

    @Override
    public boolean isNil() {
        return getDrawing().isEmpty();
    }

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        if (a instanceof FilterDrawRepresentation) {
            FilterDrawRepresentation representation = (FilterDrawRepresentation) a;
            try {
                if (representation.mCurrent != null) {
                    mCurrent = (StrokeData) representation.mCurrent.clone();
                } else {
                    mCurrent = null;
                }
                if (representation.mDrawing != null) {
                    mDrawing = (Vector<StrokeData>) representation.mDrawing.clone();
                } else {
                    mDrawing = null;
                }

            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        } else {
            Log.v(LOGTAG, "cannot use parameters from " + a);
        }
    }

    @Override
    public boolean equals(FilterRepresentation representation) {
        if (!super.equals(representation)) {
            return false;
        }
        if (representation instanceof FilterDrawRepresentation) {
            FilterDrawRepresentation fdRep = (FilterDrawRepresentation) representation;
            if (fdRep.mDrawing.size() != mDrawing.size())
                return false;
            if (fdRep.mCurrent == null && mCurrent.mPath == null) {
                return true;
            }
            if (fdRep.mCurrent != null && mCurrent.mPath != null) {
                if (fdRep.mCurrent.noPoints == mCurrent.noPoints) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public void startNewSection(byte type, int color, float size, float x, float y) {
        mCurrent = new StrokeData();
        mCurrent.mColor = color;
        mCurrent.mRadius = size;
        mCurrent.mType = type;
        mCurrent.mPath = new Path();
        mCurrent.mPath.moveTo(x, y);
        mCurrent.noPoints = 0;
    }

    public void addPoint(float x, float y) {
        mCurrent.noPoints++;
        mCurrent.mPath.lineTo(x, y);
    }

    public void endSection(float x, float y) {
        mCurrent.mPath.lineTo(x, y);
        mCurrent.noPoints++;
        mDrawing.add(mCurrent);
        mCurrent = null;
    }

    public void clearCurrentSection() {
        mCurrent = null;
    }

    public void clear() {
        mCurrent = null;
        mDrawing.clear();
    }

}
