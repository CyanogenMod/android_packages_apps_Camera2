/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.graphics.RectF;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorRedEye;

import java.util.Vector;

public class FilterRedEyeRepresentation extends FilterRepresentation {
    private static final String LOGTAG = "FilterRedEyeRepresentation";
    private Vector<RedEyeCandidate> mCandidates = new Vector<RedEyeCandidate>();

    public FilterRedEyeRepresentation() {
        super("RedEye");
        setFilterClass(ImageFilterRedEye.class);
        setFilterType(FilterRepresentation.TYPE_NORMAL);
        setButtonId(R.id.redEyeButton);
        setTextId(R.string.redeye);
        setEditorId(EditorRedEye.ID);
    }

    @Override
    public FilterRepresentation clone() throws CloneNotSupportedException {
        FilterRedEyeRepresentation representation = (FilterRedEyeRepresentation) super
                .clone();
        representation.mCandidates = (Vector<RedEyeCandidate>) mCandidates.clone();
        return representation;
    }

    public boolean hasCandidates() {
        return mCandidates != null;
    }

    public Vector<RedEyeCandidate> getCandidates() {
        return mCandidates;
    }

    public void setCandidates(Vector<RedEyeCandidate> mCandidates) {
        this.mCandidates = mCandidates;
    }

    public RedEyeCandidate getCandidate(int index) {
        return this.mCandidates.get(index);
    }

    public void addCandidate(RedEyeCandidate c) {
        this.mCandidates.add(c);
    }

    public void removeCandidate(RedEyeCandidate c) {
        this.mCandidates.remove(c);
    }

    public void clearCandidates() {
        this.mCandidates.clear();
    }

    public int getNumberOfCandidates() {
        if (mCandidates == null) {
            return 0;
        }
        return mCandidates.size();
    }

    public void addRect(RectF rect, RectF bounds) {
        if (!hasCandidates()) {
            setCandidates(new Vector<RedEyeCandidate>());
        }
        Vector<RedEyeCandidate> intersects = new Vector<RedEyeCandidate>();
        for (int i = 0; i < getCandidates().size(); i++) {
            RedEyeCandidate r = getCandidate(i);
            if (r.intersect(rect)) {
                intersects.add(r);
            }
        }
        for (int i = 0; i < intersects.size(); i++) {
            RedEyeCandidate r = intersects.elementAt(i);
            rect.union(r.mRect);
            bounds.union(r.mBounds);
            removeCandidate(r);
        }
        addCandidate(new RedEyeCandidate(rect, bounds));
    }

}
