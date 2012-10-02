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

static int* gVignetteMap = 0;
static int gVignetteWidth = 0;
static int gVignetteHeight = 0;

__inline__ void createVignetteMap(int w, int h)
{
    if (gVignetteMap && (gVignetteWidth != w || gVignetteHeight != h))
    {
        free(gVignetteMap);
        gVignetteMap = 0;
    }
    if (gVignetteMap == 0)
    {
        gVignetteWidth = w;
        gVignetteHeight = h;

        int cx = w / 2;
        int cy = h / 2;
        int i, j;

        gVignetteMap = malloc(w * h * sizeof(int));
        float maxDistance = cx * cx * 2.0f;
        for (i = 0; i < w; i++)
        {
            for (j = 0; j < h; j++)
            {
                float distance = (cx - i) * (cx - i) + (cy - j) * (cy - j);
                gVignetteMap[j * w + i] = (int) (distance / maxDistance * 255);
            }
        }
    }
}

void JNIFUNCF(ImageFilterVignette, nativeApplyFilter, jobject bitmap, jint width, jint height, jfloat strength)
{
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap, (void**) &destination);
    createVignetteMap(width, height);
    int i;
    int len = width * height * 4;
    int vignette = 0;

    for (i = 0; i < len; i += 4)
    {
        vignette = (int) (strength * gVignetteMap[i / 4]);
        destination[RED] = CLAMP(destination[RED] - vignette);
        destination[GREEN] = CLAMP(destination[GREEN] - vignette);
        destination[BLUE] = CLAMP(destination[BLUE] - vignette);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}
