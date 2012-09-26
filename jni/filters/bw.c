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

void JNIFUNCF(ImageFilterBW, nativeApplyFilter, jobject bitmap, jint width, jint height)
{
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap, (void**) &destination);
    int i;
    int len = width * height * 4;
    float Rf = 0.2999f;
    float Gf = 0.587f;
    float Bf = 0.114f;

    for (i = 0; i < len; i+=4)
    {
        int r = destination[RED];
        int g = destination[GREEN];
        int b = destination[BLUE];
        int t = CLAMP(Rf * r + Gf * g + Bf *b);

        destination[RED] = t;
        destination[GREEN] = t;
        destination[BLUE] = t;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

void JNIFUNCF(ImageFilterBWRed, nativeApplyFilter, jobject bitmap, jint width, jint height)
{
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap, (void**) &destination);
    int i;
    int len = width * height * 4;
    for (i = 0; i < len; i+=4)
    {
        int r = destination[RED];
        int g = destination[GREEN];
        int b = destination[BLUE];
        int t = (g + b) / 2;
        r = t;
        g = t;
        b = t;
        destination[RED] = r;
        destination[GREEN] = g;
        destination[BLUE] = b;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

void JNIFUNCF(ImageFilterBWGreen, nativeApplyFilter, jobject bitmap, jint width, jint height)
{
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap, (void**) &destination);
    int i;
    int len = width * height * 4;
    for (i = 0; i < len; i+=4)
    {
        int r = destination[RED];
        int g = destination[GREEN];
        int b = destination[BLUE];
        int t = (r + b) / 2;
        r = t;
        g = t;
        b = t;
        destination[RED] = r;
        destination[GREEN] = g;
        destination[BLUE] = b;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

void JNIFUNCF(ImageFilterBWBlue, nativeApplyFilter, jobject bitmap, jint width, jint height)
{
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap, (void**) &destination);
    int i;
    int len = width * height * 4;
    for (i = 0; i < len; i+=4)
    {
        int r = destination[RED];
        int g = destination[GREEN];
        int b = destination[BLUE];
        int t = (r + g) / 2;
        r = t;
        g = t;
        b = t;
        destination[RED] = r;
        destination[GREEN] = g;
        destination[BLUE] = b;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}
