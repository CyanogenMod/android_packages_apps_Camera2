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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

public class MediaSetTest extends AndroidTestCase {
    @SuppressWarnings("unused")
    private static final String TAG = "MediaSetTest";

    @SmallTest
    public void testComboAlbumSet() {
        GalleryApp app = new GalleryAppMock(null, null, null);
        Path.clearAll();
        DataManager dataManager = app.getDataManager();

        dataManager.addSource(new ComboSource(app));
        dataManager.addSource(new MockSource(app));

        MockSet set00 = new MockSet(Path.fromString("/mock/00"), dataManager, 0, 2000);
        MockSet set01 = new MockSet(Path.fromString("/mock/01"), dataManager, 1, 3000);
        MockSet set10 = new MockSet(Path.fromString("/mock/10"), dataManager, 2, 4000);
        MockSet set11 = new MockSet(Path.fromString("/mock/11"), dataManager, 3, 5000);
        MockSet set12 = new MockSet(Path.fromString("/mock/12"), dataManager, 4, 6000);

        MockSet set0 = new MockSet(Path.fromString("/mock/0"), dataManager, 7, 7000);
        set0.addMediaSet(set00);
        set0.addMediaSet(set01);

        MockSet set1 = new MockSet(Path.fromString("/mock/1"), dataManager, 8, 8000);
        set1.addMediaSet(set10);
        set1.addMediaSet(set11);
        set1.addMediaSet(set12);

        MediaSet combo = dataManager.getMediaSet("/combo/{/mock/0,/mock/1}");
        assertEquals(5, combo.getSubMediaSetCount());
        assertEquals(0, combo.getMediaItemCount());
        assertEquals("/mock/00", combo.getSubMediaSet(0).getPath().toString());
        assertEquals("/mock/01", combo.getSubMediaSet(1).getPath().toString());
        assertEquals("/mock/10", combo.getSubMediaSet(2).getPath().toString());
        assertEquals("/mock/11", combo.getSubMediaSet(3).getPath().toString());
        assertEquals("/mock/12", combo.getSubMediaSet(4).getPath().toString());

        assertEquals(10, combo.getTotalMediaItemCount());
    }
}
