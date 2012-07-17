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

package com.android.gallery3d.actionbar;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.InflateException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class SimpleMenuInflater {

    private static final String TAG_MENU = "menu";
    private static final String TAG_ITEM = "item";

    private final Context mContext;

    public SimpleMenuInflater(Context context) {
        mContext = context;
    }

    public SimpleMenu inflate(SimpleMenu menu, int menuRes) throws InflateException {
        XmlResourceParser parser = mContext.getResources().getXml(menuRes);
        try {
            return inflateInternal(menu, parser);
        } catch (XmlPullParserException e) {
            throw new InflateException(e);
        } catch (IOException e) {
            throw new InflateException(e);
        } finally {
            if (parser != null) parser.close();
        }
    }

    private SimpleMenu inflateInternal(SimpleMenu menu, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        do {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                if (TAG_MENU.equals(tagName)) {
                    eventType = parser.next();
                    break;
                }
                throw new RuntimeException("unexpected tag: " + tagName);
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        if (menu == null) menu = new SimpleMenu();
        AttributeSet attrs = Xml.asAttributeSet(parser);

        boolean reachedEndOfMenu = false;
        while (!reachedEndOfMenu) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    String tagName = parser.getName();
                    if (TAG_ITEM.equals(tagName)) {
                        menu.addItem(parseItem(attrs));
                    } else if (TAG_MENU.equals(tagName)) {
                        throw new RuntimeException("nested menu not supported");
                    } else {
                        // ignore all other tags
                        parser.next();
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    String tagName = parser.getName();
                    if (TAG_MENU.equals(tagName)) {
                        reachedEndOfMenu = true;
                        break;
                    }
                    break;
                }
                case XmlPullParser.END_DOCUMENT: {
                    throw new RuntimeException("unexpected end of document");
                }
            }
            eventType = parser.next();
        }
        return menu;
    }

    private static final String ATTR_ID = "id";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_VISIBLE = "visible";
    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";

    private SimpleMenu.Item parseItem(AttributeSet attrs) {
        SimpleMenu.Item item = new SimpleMenu.Item();
        Resources res = mContext.getResources();
        for (int i = 0, n = attrs.getAttributeCount(); i < n; ++i) {
            String attrName = attrs.getAttributeName(i);
            if (ATTR_ID.equals(attrName)) {
                item.id = attrs.getAttributeResourceValue(i, 0);
            } else if (ATTR_ICON.equals(attrName)) {
                item.iconId = attrs.getAttributeResourceValue(i, 0);
            } else if (ATTR_TITLE.equals(attrName)) {
                int id = attrs.getAttributeResourceValue(i, 0);
                item.title = id == 0 ? null : res.getString(id);
            } else if (ATTR_VISIBLE.equals(attrName)) {
                item.visible = attrs.getAttributeBooleanValue(i, true);
            } else if (ATTR_SHOW_AS_ACTION.equals(attrName)) {
                item.showAsAction = attrs.getAttributeIntValue(i, 0);
            }
        }
        return item;
    }

}
