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
package com.android.gallery3d.filtershow.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.util.Pair;

import com.android.gallery3d.filtershow.data.FilterStackDBHelper.FilterStack;

import java.util.ArrayList;
import java.util.List;

public class FilterStackSource {
    private static final String LOGTAG = "FilterStackSource";

    private SQLiteDatabase database = null;;
    private final FilterStackDBHelper dbHelper;

    public FilterStackSource(Context context) {
        dbHelper = new FilterStackDBHelper(context);
    }

    public void open() {
        try {
            database = dbHelper.getWritableDatabase();
        } catch (SQLiteException e) {
            Log.w(LOGTAG, "could not open database", e);
        }
    }

    public void close() {
        database = null;
        dbHelper.close();
    }

    public boolean insertStack(String stackName, byte[] stackBlob) {
        boolean ret = true;
        ContentValues val = new ContentValues();
        val.put(FilterStack.STACK_ID, stackName);
        val.put(FilterStack.FILTER_STACK, stackBlob);
        database.beginTransaction();
        try {
            ret = (-1 != database.insert(FilterStack.TABLE, null, val));
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return ret;
    }

    public boolean removeStack(String stackName) {
        boolean ret = true;
        database.beginTransaction();
        try {
            ret = (0 != database.delete(FilterStack.TABLE, FilterStack.STACK_ID + " = ?",
                    new String[] { stackName}));
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return ret;
    }

    public void removeAllStacks() {
        database.beginTransaction();
        try {
            database.delete(FilterStack.TABLE, null, null);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public byte[] getStack(String stackName) {
        byte[] ret = null;
        Cursor c = null;
        database.beginTransaction();
        try {
            c = database.query(FilterStack.TABLE,
                    new String[] { FilterStack.FILTER_STACK },
                    FilterStack.STACK_ID + " = ?",
                    new String[] { stackName }, null, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                ret = c.getBlob(0);
            }
            database.setTransactionSuccessful();
        } finally {
            if (c != null) {
                c.close();
            }
            database.endTransaction();
        }
        return ret;
    }

    public List<Pair<String, byte[]>> getAllStacks() {
        List<Pair<String, byte[]>> ret = new ArrayList<Pair<String, byte[]>>();
        Cursor c = null;
        database.beginTransaction();
        try {
            c = database.query(FilterStack.TABLE,
                    new String[] { FilterStack.STACK_ID, FilterStack.FILTER_STACK },
                    null, null, null, null, null, null);
            if (c != null) {
                boolean loopCheck = c.moveToFirst();
                while (loopCheck) {
                    String name = (c.isNull(0)) ?  null : c.getString(0);
                    byte[] b = (c.isNull(1)) ? null : c.getBlob(1);
                    ret.add(new Pair<String, byte[]>(name, b));
                    loopCheck = c.moveToNext();
                }
            }
            database.setTransactionSuccessful();
        } finally {
            if (c != null) {
                c.close();
            }
            database.endTransaction();
        }
        if (ret.size() <= 0) {
            return null;
        }
        return ret;
    }
}
