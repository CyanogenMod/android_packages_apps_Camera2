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
#include "kmeans.h"

#ifdef __cplusplus
extern "C" {
#endif

void JNIFUNCF(ImageFilterKMeans, nativeApplyFilter, jobject bitmap, jint width, jint height, jint p)
{
    char* destination = 0;
    AndroidBitmap_lockPixels(env, bitmap, (void**) &destination);
    unsigned char * dst = (unsigned char *) destination;

    int len = width * height * 4;
    int dimension = 3;
    int stride = 4;
    int iterations = 4;
    int k = p;
    unsigned char finalCentroids[k * stride];

    // TODO: add downsampling and better heuristic to improve speed, then up iterations

    // does K-Means clustering on rgb bitmap colors
    runKMeans<unsigned char, int>(k, finalCentroids, dst, len, dimension, stride, iterations);
    applyCentroids<unsigned char, int>(k, finalCentroids, dst, len, dimension, stride);

    AndroidBitmap_unlockPixels(env, bitmap);
}
#ifdef __cplusplus
}
#endif
