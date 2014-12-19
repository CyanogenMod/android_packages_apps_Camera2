/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.one.v2.sharedimagereader.ticketpool;

import com.android.camera.async.SafeCloseable;

/**
 * Tickets store the logical-ability to hold on to an instance of a managed,
 * finite resource (e.g. hardware-backed media images).
 * <p>
 * They may be released by calling {@link #close}.
 * <p>
 */
public interface Ticket extends SafeCloseable {
    /**
     * Releases the ticket. Implementations must be idempotent.
     */
    @Override
    public void close();
}
