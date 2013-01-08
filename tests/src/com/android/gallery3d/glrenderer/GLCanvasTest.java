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

package com.android.gallery3d.glrenderer;

import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import junit.framework.TestCase;

import java.util.Arrays;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

@SmallTest
public class GLCanvasTest extends TestCase {
    private static final String TAG = "GLCanvasTest";

    private static GLPaint newColorPaint(int color) {
        GLPaint paint = new GLPaint();
        paint.setColor(color);
        return paint;
    }

    @SmallTest
    public void testSetSize() {
        GL11 glStub = new GLStub();
        GLCanvas canvas = new GLES11Canvas(glStub);
        canvas.setSize(100, 200);
        canvas.setSize(1000, 100);
        try {
            canvas.setSize(-1, 100);
            fail();
        } catch (Throwable ex) {
            // expected.
        }
    }

    @SmallTest
    public void testClearBuffer() {
        new ClearBufferTest().run();
    }

    private static class ClearBufferTest extends GLMock {
        void run() {
            GLCanvas canvas = new GLES11Canvas(this);
            assertEquals(0, mGLClearCalled);
            canvas.clearBuffer();
            assertEquals(GL10.GL_COLOR_BUFFER_BIT, mGLClearMask);
            assertEquals(1, mGLClearCalled);
        }
    }

    @SmallTest
    public void testSetColor() {
        new SetColorTest().run();
    }

    // This test assumes we use pre-multipled alpha blending and should
    // set the blending function and color correctly.
    private static class SetColorTest extends GLMock {
        void run() {
            int[] testColors = new int[] {
                0, 0xFFFFFFFF, 0xFF000000, 0x00FFFFFF, 0x80FF8001,
                0x7F010101, 0xFEFEFDFC, 0x017F8081, 0x027F8081, 0x2ADE4C4D
            };

            GLCanvas canvas = new GLES11Canvas(this);
            canvas.setSize(400, 300);
            // Test one color to make sure blend function is set.
            assertEquals(0, mGLColorCalled);
            canvas.drawLine(0, 0, 1, 1, newColorPaint(0x7F804020));
            assertEquals(1, mGLColorCalled);
            assertEquals(0x7F402010, mGLColor);
            assertPremultipliedBlending(this);

            // Test other colors to make sure premultiplication is right
            for (int c : testColors) {
                float a = (c >>> 24) / 255f;
                float r = ((c >> 16) & 0xff) / 255f;
                float g = ((c >> 8) & 0xff) / 255f;
                float b = (c & 0xff) / 255f;
                int pre = makeColor4f(a * r, a * g, a * b, a);

                mGLColorCalled = 0;
                canvas.drawLine(0, 0, 1, 1, newColorPaint(c));
                assertEquals(1, mGLColorCalled);
                assertEquals(pre, mGLColor);
            }
        }
    }

    @SmallTest
    public void testSetGetMultiplyAlpha() {
        GL11 glStub = new GLStub();
        GLCanvas canvas = new GLES11Canvas(glStub);

        canvas.setAlpha(1f);
        assertEquals(1f, canvas.getAlpha());

        canvas.setAlpha(0f);
        assertEquals(0f, canvas.getAlpha());

        canvas.setAlpha(0.5f);
        assertEquals(0.5f, canvas.getAlpha());

        canvas.multiplyAlpha(0.5f);
        assertEquals(0.25f, canvas.getAlpha());

        canvas.multiplyAlpha(0f);
        assertEquals(0f, canvas.getAlpha());

        try {
            canvas.setAlpha(-0.01f);
            fail();
        } catch (Throwable ex) {
            // expected.
        }

        try {
            canvas.setAlpha(1.01f);
            fail();
        } catch (Throwable ex) {
            // expected.
        }
    }

    @SmallTest
    public void testAlpha() {
        new AlphaTest().run();
    }

    private static class AlphaTest extends GLMock {
        void run() {
            GLCanvas canvas = new GLES11Canvas(this);
            canvas.setSize(400, 300);

            assertEquals(0, mGLColorCalled);
            canvas.setAlpha(0.48f);
            canvas.drawLine(0, 0, 1, 1, newColorPaint(0xFF804020));
            assertPremultipliedBlending(this);
            assertEquals(1, mGLColorCalled);
            assertEquals(0x7A3D1F0F, mGLColor);
        }
    }

    @SmallTest
    public void testDrawLine() {
        new DrawLineTest().run();
    }

    // This test assumes the drawLine() function use glDrawArrays() with
    // GL_LINE_STRIP mode to draw the line and the input coordinates are used
    // directly.
    private static class DrawLineTest extends GLMock {
        private int mDrawArrayCalled = 0;
        private final int[] mResult = new int[4];

        @Override
        public void glDrawArrays(int mode, int first, int count) {
            assertNotNull(mGLVertexPointer);
            assertEquals(GL10.GL_LINE_STRIP, mode);
            assertEquals(2, count);
            mGLVertexPointer.bindByteBuffer();

            double[] coord = new double[4];
            mGLVertexPointer.getArrayElement(first, coord);
            mResult[0] = (int) coord[0];
            mResult[1] = (int) coord[1];
            mGLVertexPointer.getArrayElement(first + 1, coord);
            mResult[2] = (int) coord[0];
            mResult[3] = (int) coord[1];
            mDrawArrayCalled++;
        }

        void run() {
            GLCanvas canvas = new GLES11Canvas(this);
            canvas.setSize(400, 300);
            canvas.drawLine(2, 7, 1, 8, newColorPaint(0) /* color */);
            assertTrue(mGLVertexArrayEnabled);
            assertEquals(1, mDrawArrayCalled);

            Log.v(TAG, "result = " + Arrays.toString(mResult));
            int[] answer = new int[] {2, 7, 1, 8};
            for (int i = 0; i < answer.length; i++) {
                assertEquals(answer[i], mResult[i]);
            }
        }
    }

    @SmallTest
    public void testFillRect() {
        new FillRectTest().run();
    }

    // This test assumes the drawLine() function use glDrawArrays() with
    // GL_TRIANGLE_STRIP mode to draw the line and the input coordinates
    // are used directly.
    private static class FillRectTest extends GLMock {
        private int mDrawArrayCalled = 0;
        private final int[] mResult = new int[8];

        @Override
        public void glDrawArrays(int mode, int first, int count) {
            assertNotNull(mGLVertexPointer);
            assertEquals(GL10.GL_TRIANGLE_STRIP, mode);
            assertEquals(4, count);
            mGLVertexPointer.bindByteBuffer();

            double[] coord = new double[4];
            for (int i = 0; i < 4; i++) {
                mGLVertexPointer.getArrayElement(first + i, coord);
                mResult[i * 2 + 0] = (int) coord[0];
                mResult[i * 2 + 1] = (int) coord[1];
            }

            mDrawArrayCalled++;
        }

        void run() {
            GLCanvas canvas = new GLES11Canvas(this);
            canvas.setSize(400, 300);
            canvas.fillRect(2, 7, 1, 8, 0 /* color */);
            assertTrue(mGLVertexArrayEnabled);
            assertEquals(1, mDrawArrayCalled);
            Log.v(TAG, "result = " + Arrays.toString(mResult));

            // These are the four vertics that should be used.
            int[] answer = new int[] {
                2, 7,
                3, 7,
                3, 15,
                2, 15};
            int count[] = new int[4];

            // Count the number of appearances for each vertex.
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (answer[i * 2] == mResult[j * 2] &&
                        answer[i * 2 + 1] == mResult[j * 2 + 1]) {
                        count[i]++;
                    }
                }
            }

            // Each vertex should appear exactly once.
            for (int i = 0; i < 4; i++) {
                assertEquals(1, count[i]);
            }
        }
    }

    @SmallTest
    public void testTransform() {
        new TransformTest().run();
    }

    // This test assumes glLoadMatrixf is used to load the model view matrix,
    // and glOrthof is used to load the projection matrix.
    //
    // The projection matrix is set to an orthogonal projection which is the
    // inverse of viewport transform. So the model view matrix maps input
    // directly to screen coordinates (default no scaling, and the y-axis is
    // reversed).
    //
    // The matrix here are all listed in column major order.
    //
    private static class TransformTest extends GLMock {
        private final float[] mModelViewMatrixUsed = new float[16];
        private final float[] mProjectionMatrixUsed = new float[16];

        @Override
        public void glDrawArrays(int mode, int first, int count) {
            copy(mModelViewMatrixUsed, mGLModelViewMatrix);
            copy(mProjectionMatrixUsed, mGLProjectionMatrix);
        }

        private void copy(float[] dest, float[] src) {
            System.arraycopy(src, 0, dest, 0, 16);
        }

        void run() {
            GLCanvas canvas = new GLES11Canvas(this);
            canvas.setSize(40, 50);
            int color = 0;

            // Initial matrix
            canvas.drawLine(0, 0, 1, 1, newColorPaint(color));
            assertMatrixEq(new float[] {
                    1,  0, 0, 0,
                    0, -1, 0, 0,
                    0,  0, 1, 0,
                    0, 50, 0, 1
                    }, mModelViewMatrixUsed);

            assertMatrixEq(new float[] {
                    2f / 40,       0,  0, 0,
                          0, 2f / 50,  0, 0,
                          0,       0, -1, 0,
                         -1,      -1,  0, 1
                    }, mProjectionMatrixUsed);

            // Translation
            canvas.translate(3, 4, 5);
            canvas.drawLine(0, 0, 1, 1, newColorPaint(color));
            assertMatrixEq(new float[] {
                    1,  0, 0, 0,
                    0, -1, 0, 0,
                    0,  0, 1, 0,
                    3, 46, 5, 1
                    }, mModelViewMatrixUsed);
            canvas.save();

            // Scaling
            canvas.scale(0.7f, 0.6f, 0.5f);
            canvas.drawLine(0, 0, 1, 1, newColorPaint(color));
            assertMatrixEq(new float[] {
                    0.7f,     0,    0, 0,
                    0,    -0.6f,    0, 0,
                    0,        0, 0.5f, 0,
                    3,       46,    5, 1
                    }, mModelViewMatrixUsed);

            // Rotation
            canvas.rotate(90, 0, 0, 1);
            canvas.drawLine(0, 0, 1, 1, newColorPaint(color));
            assertMatrixEq(new float[] {
                        0, -0.6f,    0, 0,
                    -0.7f,     0,    0, 0,
                        0,     0, 0.5f, 0,
                        3,    46,    5, 1
                    }, mModelViewMatrixUsed);
            canvas.restore();

            // After restoring to the point just after translation,
            // do rotation again.
            canvas.rotate(180, 1, 0, 0);
            canvas.drawLine(0, 0, 1, 1, newColorPaint(color));
            assertMatrixEq(new float[] {
                    1,  0,  0, 0,
                    0,  1,  0, 0,
                    0,  0, -1, 0,
                    3, 46,  5, 1
                    }, mModelViewMatrixUsed);
        }
    }

    private static void assertPremultipliedBlending(GLMock mock) {
        assertTrue(mock.mGLBlendFuncCalled > 0);
        assertTrue(mock.mGLBlendEnabled);
        assertEquals(GL11.GL_ONE, mock.mGLBlendFuncSFactor);
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, mock.mGLBlendFuncDFactor);
    }

    private static void assertMatrixEq(float[] expected, float[] actual) {
        try {
            for (int i = 0; i < 16; i++) {
                assertFloatEq(expected[i], actual[i]);
            }
        } catch (Throwable t) {
            Log.v(TAG, "expected = " + Arrays.toString(expected) +
                    ", actual = " + Arrays.toString(actual));
            fail();
        }
    }

    public static void assertFloatEq(float expected, float actual) {
        if (Math.abs(actual - expected) > 1e-6) {
            Log.v(TAG, "expected: " + expected + ", actual: " + actual);
            fail();
        }
    }
}
