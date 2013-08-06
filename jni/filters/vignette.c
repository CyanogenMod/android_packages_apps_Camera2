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

#include "filters.h"
#include <math.h>

static int* gVignetteMap = 0;
static int gVignetteWidth = 0;
static int gVignetteHeight = 0;

void JNIFUNCF(ImageFilterVignette, nativeApplyFilter, jobject bitmap, jint width, jint height, jint centerx, jint centery, jfloat radiusx, jfloat radiusy, jfloat strength)
{
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap, (void**) &destination);
    int i;
    int len = width * height * 4;
    int vignette = 0;
    float d = centerx;
    if (radiusx == 0) radiusx = 10;
    if (radiusy == 0) radiusy = 10;
    float scalex = 1/radiusx;
    float scaley = 1/radiusy;

    for (i = 0; i < len; i += 4)
    {
        int p = i/4;
        float x = ((p%width)-centerx)*scalex;
        float y = ((p/width)-centery)*scaley;
        float dist = sqrt(x*x+y*y)-1;
        vignette = (int) (strength*256*MAX(dist,0));
        destination[RED] = CLAMP(destination[RED] - vignette);
        destination[GREEN] = CLAMP(destination[GREEN] - vignette);
        destination[BLUE] = CLAMP(destination[BLUE] - vignette);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}
