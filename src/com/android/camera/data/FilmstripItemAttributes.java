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

package com.android.camera.data;

import java.util.EnumSet;

/**
 * Represents an immutable set of item attributes
 */
public class FilmstripItemAttributes {
    public enum Attributes {
        HAS_DETAILED_CAPTURE_INFO,
        CAN_SHARE,
        CAN_EDIT,
        CAN_DELETE,
        CAN_PLAY,
        CAN_OPEN_VIEWER,
        CAN_SWIPE_AWAY,
        CAN_ZOOM_IN_PLACE,
        IS_RENDERING,
        IS_IMAGE,
        IS_VIDEO,
    }

    private final EnumSet<Attributes> mAttributes;

    public static final FilmstripItemAttributes DEFAULT =
          new Builder().build();

    private FilmstripItemAttributes(EnumSet<Attributes> attributes) {
       mAttributes = attributes;
    }

    public boolean hasDetailedCaptureInfo() {
        return mAttributes.contains(Attributes.HAS_DETAILED_CAPTURE_INFO);
    }

    // TODO: Replace this with a command.
    public boolean canShare() {
        return mAttributes.contains(Attributes.CAN_SHARE);
    }

    // TODO: Replace this with a command.
    public boolean canEdit() {
        return mAttributes.contains(Attributes.CAN_EDIT);
    }

    // TODO: Replace this with a command.
    public boolean canDelete() {
        return mAttributes.contains(Attributes.CAN_DELETE);
    }

    public boolean canSwipeAway() {
        return mAttributes.contains(Attributes.CAN_SWIPE_AWAY);
    }

    public boolean canZoomInPlace() {
        return mAttributes.contains(Attributes.CAN_ZOOM_IN_PLACE);
    }

    public boolean isRendering() {
        return mAttributes.contains(Attributes.IS_RENDERING);
    }

    // TODO: Consider replacing video / image with an enum.
    public boolean isImage() {
        return mAttributes.contains(Attributes.IS_IMAGE);
    }

    public boolean isVideo() {
        return mAttributes.contains(Attributes.IS_VIDEO);
    }

    /**
     * Builder for {@code FilmstripItemAttributes}.
     */
    public static class Builder {
        EnumSet<Attributes> mAttributes = EnumSet.noneOf(Attributes.class);
        public Builder with(Attributes attribute) {
            mAttributes.add(attribute);
            return this;
        }

        public FilmstripItemAttributes build() {
            return new FilmstripItemAttributes(EnumSet.copyOf(mAttributes));
        }
    }
}