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

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorFlip;

import java.io.IOException;

public class FilterMirrorRepresentation extends FilterRepresentation {
    public static final String SERIALIZATION_NAME = "MIRROR";
    private static final String SERIALIZATION_MIRROR_VALUE = "value";
    private static final String TAG = FilterMirrorRepresentation.class.getSimpleName();

    Mirror mMirror = Mirror.NONE;

    public enum Mirror {
        NONE('N'), VERTICAL('V'), HORIZONTAL('H'), BOTH('B');
        char mValue;

        private Mirror(char value) {
            mValue = value;
        }

        public char value() {
            return mValue;
        }

        public static Mirror fromValue(char value) {
            switch (value) {
                case 'N':
                    return NONE;
                case 'V':
                    return VERTICAL;
                case 'H':
                    return HORIZONTAL;
                case 'B':
                    return BOTH;
                default:
                    return null;
            }
        }
    }

    public FilterMirrorRepresentation(Mirror mirror) {
        super(FilterMirrorRepresentation.class.getSimpleName());
        setSerializationName(SERIALIZATION_NAME);
        setShowParameterValue(true);
        setFilterClass(FilterMirrorRepresentation.class);
        setFilterType(FilterRepresentation.TYPE_GEOMETRY);
        setTextId(R.string.mirror);
        setEditorId(EditorFlip.ID);
        setMirror(mirror);
    }

    public FilterMirrorRepresentation(FilterMirrorRepresentation m) {
        this(m.getMirror());
    }

    public FilterMirrorRepresentation() {
        this(Mirror.NONE);
    }

    public Mirror getMirror() {
        return mMirror;
    }

    public void set(FilterMirrorRepresentation r) {
        mMirror = r.mMirror;
    }

    public void setMirror(Mirror mirror) {
        if (mirror == null) {
            throw new IllegalArgumentException("Argument to setMirror is null");
        }
        mMirror = mirror;
    }

    @Override
    public boolean allowsSingleInstanceOnly() {
        return true;
    }

    @Override
    public FilterRepresentation copy() {
        return new FilterMirrorRepresentation(this);
    }

    @Override
    protected void copyAllParameters(FilterRepresentation representation) {
        if (!(representation instanceof FilterMirrorRepresentation)) {
            throw new IllegalArgumentException("calling copyAllParameters with incompatible types!");
        }
        super.copyAllParameters(representation);
        representation.useParametersFrom(this);
    }

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        if (!(a instanceof FilterMirrorRepresentation)) {
            throw new IllegalArgumentException("calling useParametersFrom with incompatible types!");
        }
        setMirror(((FilterMirrorRepresentation) a).getMirror());
    }

    @Override
    public boolean isNil() {
        return mMirror == Mirror.NONE;
    }

    @Override
    public void serializeRepresentation(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name(SERIALIZATION_MIRROR_VALUE).value(mMirror.value());
        writer.endObject();
    }

    @Override
    public void deSerializeRepresentation(JsonReader reader) throws IOException {
        boolean unset = true;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (SERIALIZATION_MIRROR_VALUE.equals(name)) {
                Mirror r = Mirror.fromValue((char) reader.nextInt());
                if (r != null) {
                    setMirror(r);
                    unset = false;
                }
            } else {
                reader.skipValue();
            }
        }
        if (unset) {
            Log.w(TAG, "WARNING: bad value when deserializing " + SERIALIZATION_NAME);
        }
        reader.endObject();
    }
}
