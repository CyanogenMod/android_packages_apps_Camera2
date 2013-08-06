package com.android.camera.support.glrenderer;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

// This mimics corresponding GL functions.
public interface GLId {
    public int generateTexture();

    public void glGenBuffers(int n, int[] buffers, int offset);

    public void glDeleteTextures(GL11 gl, int n, int[] textures, int offset);

    public void glDeleteBuffers(GL11 gl, int n, int[] buffers, int offset);

    public void glDeleteFramebuffers(GL11ExtensionPack gl11ep, int n, int[] buffers, int offset);
}
