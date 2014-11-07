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

package com.android.camera.gl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Allows copying a GL texture to a {@link RenderTarget}.
 */
// TODO: Document this class a bit more and add a test for the class.
public class CopyShader {

    private static final String VERTEX_SHADER =
            "attribute vec4 a_position;\n" +
                    "attribute vec2 a_texcoord;\n" +
                    "varying vec2 v_texcoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = a_position;\n" +
                    "  v_texcoord = a_texcoord;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "uniform sampler2D tex_sampler;\n" +
                    "varying vec2 v_texcoord;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(tex_sampler, v_texcoord);\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_EXTERNAL =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "uniform samplerExternalOES tex_sampler;\n" +
                    "varying vec2 v_texcoord;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(tex_sampler, v_texcoord);\n" +
                    "}\n";

    private static final float[] SOURCE_COORDS = new float[] {
            0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f };
    private static final float[] TARGET_COORDS = new float[] {
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f };

    private final int mProgram;
    private final int mTextureTarget;

    private final FloatBuffer mSourceCoords;
    private final FloatBuffer mTargetCoords;

    private final int mSourceAttrib;
    private final int mTargetAttrib;

    private final int mTextureUniform;

    private CopyShader(int program, int textureTarget) {
        mProgram = program;
        mTextureTarget = textureTarget;
        mSourceCoords = readFloats(SOURCE_COORDS);
        mTargetCoords = readFloats(TARGET_COORDS);
        mSourceAttrib = GLES20.glGetAttribLocation(mProgram, "a_texcoord");
        mTargetAttrib = GLES20.glGetAttribLocation(mProgram, "a_position");
        mTextureUniform = GLES20.glGetUniformLocation(mProgram, "tex_sampler");
    }

    /**
     * Compiles a new shader that is valid in the current context.
     *
     * @return a new shader instance that is valid in the current context
     */
    public static CopyShader compileNewShader() {
        return new CopyShader(createProgram(VERTEX_SHADER, FRAGMENT_SHADER), GLES20.GL_TEXTURE);
    }

    /**
     * Compiles a new shader that binds textures as GL_TEXTURE_EXTERNAL_OES.
     *
     * @return a new shader instance that is valid in the current context
     */
    public static CopyShader compileNewExternalShader() {
        return new CopyShader(createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXTERNAL),
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
    }

    /**
     * Sets the 4x4 transform matrix to apply when copying the texture.
     * <p/>
     * Note: Non-affine components of the transformation are ignored.
     *
     * @param matrix a 16-length float array containing the transform matrix in
     *            column-major order.
     */
    public void setTransform(float[] matrix) {
        /**
         * Multiply transformation matrix by column vectors (s,t, 0, 1) for
         * coordinates {(0,0),(1,0), (0,1), (1,1) } and store (s,t) values of
         * the resulting matrix in column-major order.
         */
        float[] coords = new float[] {
                matrix[12], matrix[13],
                matrix[0] + matrix[12],
                matrix[1] + matrix[13],
                matrix[4] + matrix[12],
                matrix[5] + matrix[13],
                matrix[0] + matrix[4] + matrix[12],
                matrix[1] + matrix[5] + matrix[13]
        };
        mSourceCoords.put(coords).position(0);
    }

    /**
     * Renders the specified texture to the specified target.
     *
     * @param texName name of a valid texture
     * @param target to render into
     * @param width of output
     * @param height of output
     */
    public void renderTextureToTarget(int texName, RenderTarget target, int width, int height) {
        useProgram();
        focusTarget(target, width, height);
        assignAttribute(mSourceAttrib, mSourceCoords);
        assignAttribute(mTargetAttrib, mTargetCoords);
        bindTexture(mTextureUniform, texName, mTextureTarget);
        render();
    }

    /**
     * Releases the current shader.
     * <p>
     * This must be called in the shader's GL thread.
     */
    public void release() {
        GLES20.glDeleteProgram(mProgram);
    }

    private void focusTarget(RenderTarget target, int width, int height) {
        target.focus();
        GLES20.glViewport(0, 0, width, height);
        GLToolbox.checkGlError("focus");
    }

    private void useProgram() {
        GLES20.glUseProgram(mProgram);
        GLToolbox.checkGlError("glUseProgram");
    }

    private void render() {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLToolbox.checkGlError("glDrawArrays");
    }

    private void bindTexture(int uniformName, int texName, int texTarget) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(texTarget, texName);
        GLES20.glUniform1i(uniformName, 0);
        GLToolbox.checkGlError(
                "bindTexture(" + uniformName + "," + texName + "," + texTarget + ")");
    }

    private void assignAttribute(int index, FloatBuffer values) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glVertexAttribPointer(index, 2, GLES20.GL_FLOAT, false, 8, values);
        GLES20.glEnableVertexAttribArray(index);
        GLToolbox.checkGlError("glVertexAttribPointer(" + index + ")");
    }

    private static FloatBuffer readFloats(float[] values) {
        FloatBuffer result = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(values).position(0);
        return result;
    }

    private static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String info = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                shader = 0;
                throw new RuntimeException("Could not compile shader " + shaderType + ":" + info);
            }
        }
        return shader;
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            throw new RuntimeException("Could not create shader-program as vertex shader "
                    + "could not be compiled!");
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            throw new RuntimeException("Could not create shader-program as fragment shader "
                    + "could not be compiled!");
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            GLToolbox.checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            GLToolbox.checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                String info = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                program = 0;
                throw new RuntimeException("Could not link program: " + info);
            }
        }

        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(pixelShader);

        return program;
    }
}
