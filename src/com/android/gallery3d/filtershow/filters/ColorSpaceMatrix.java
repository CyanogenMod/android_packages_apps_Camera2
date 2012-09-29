
package com.android.gallery3d.filtershow.filters;

import java.util.Arrays;

public class ColorSpaceMatrix {
    private final float[] matrix = new float[16];
    private static final float RLUM = 0.3086f;
    private static final float GLUM = 0.6094f;
    private static final float BLUM = 0.0820f;

    public ColorSpaceMatrix() {
        identity();
    }

    /**
     * get the matrix
     *
     * @return the internal matrix
     */
    public float[] getMatrix() {
        return matrix;
    }

    /**
     * set matrix to identity
     */
    public void identity() {
        Arrays.fill(matrix, 0);
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1;
    }

    public void convertToLuminance() {
        matrix[0] = matrix[1] = matrix[2] = 0.3086f;
        matrix[4] = matrix[5] = matrix[6] = 0.6094f;
        matrix[8] = matrix[9] = matrix[10] = 0.0820f;
    }

    private void multiply(float[] a)
    {
        int x, y;
        float[] temp = new float[16];

        for (y = 0; y < 4; y++) {
            int y4 = y * 4;
            for (x = 0; x < 4; x++) {
                temp[y4 + x] = matrix[y4 + 0] * a[x]
                        + matrix[y4 + 1] * a[4 + x]
                        + matrix[y4 + 2] * a[8 + x]
                        + matrix[y4 + 3] * a[12 + x];
            }
        }
        for (int i = 0; i < 16; i++)
            matrix[i] = temp[i];
    }

    private void xRotateMatrix(float rs, float rc)
    {
        ColorSpaceMatrix c = new ColorSpaceMatrix();
        float[] tmp = c.matrix;

        tmp[5] = rc;
        tmp[6] = rs;
        tmp[9] = -rs;
        tmp[10] = rc;

        multiply(tmp);
    }

    private void yRotateMatrix(float rs, float rc)
    {
        ColorSpaceMatrix c = new ColorSpaceMatrix();
        float[] tmp = c.matrix;

        tmp[0] = rc;
        tmp[2] = -rs;
        tmp[8] = rs;
        tmp[10] = rc;

        multiply(tmp);
    }

    private void zRotateMatrix(float rs, float rc)
    {
        ColorSpaceMatrix c = new ColorSpaceMatrix();
        float[] tmp = c.matrix;

        tmp[0] = rc;
        tmp[1] = rs;
        tmp[4] = -rs;
        tmp[5] = rc;
        multiply(tmp);
    }

    private void zShearMatrix(float dx, float dy)
    {
        ColorSpaceMatrix c = new ColorSpaceMatrix();
        float[] tmp = c.matrix;

        tmp[2] = dx;
        tmp[6] = dy;
        multiply(tmp);
    }

    /**
     * sets the transform to a shift in Hue
     *
     * @param rot rotation in degrees
     */
    public void setHue(float rot)
    {
        float mag = (float) Math.sqrt(2.0);
        float xrs = 1 / mag;
        float xrc = 1 / mag;
        xRotateMatrix(xrs, xrc);
        mag = (float) Math.sqrt(3.0);
        float yrs = -1 / mag;
        float yrc = (float) Math.sqrt(2.0) / mag;
        yRotateMatrix(yrs, yrc);

        float lx = getRedf(RLUM, GLUM, BLUM);
        float ly = getGreenf(RLUM, GLUM, BLUM);
        float lz = getBluef(RLUM, GLUM, BLUM);
        float zsx = lx / lz;
        float zsy = ly / lz;
        zShearMatrix(zsx, zsy);

        float zrs = (float) Math.sin(rot * Math.PI / 180.0);
        float zrc = (float) Math.cos(rot * Math.PI / 180.0);
        zRotateMatrix(zrs, zrc);
        zShearMatrix(-zsx, -zsy);
        yRotateMatrix(-yrs, yrc);
        xRotateMatrix(-xrs, xrc);
    }

    /**
     * set it to a saturation matrix
     *
     * @param s
     */
    public void changeSaturation(float s) {
        matrix[0] = (1 - s) * RLUM + s;
        matrix[1] = (1 - s) * RLUM;
        matrix[2] = (1 - s) * RLUM;
        matrix[4] = (1 - s) * GLUM;
        matrix[5] = (1 - s) * GLUM + s;
        matrix[6] = (1 - s) * GLUM;
        matrix[8] = (1 - s) * BLUM;
        matrix[9] = (1 - s) * BLUM;
        matrix[10] = (1 - s) * BLUM + s;
    }

    /**
     * Transform RGB value
     *
     * @param r red pixel value
     * @param g green pixel value
     * @param b blue pixel value
     * @return computed red pixel value
     */
    public float getRed(int r, int g, int b) {
        return r * matrix[0] + g * matrix[4] + b * matrix[8] + matrix[12];
    }

    /**
     * Transform RGB value
     *
     * @param r red pixel value
     * @param g green pixel value
     * @param b blue pixel value
     * @return computed green pixel value
     */
    public float getGreen(int r, int g, int b) {
        return r * matrix[1] + g * matrix[5] + b * matrix[9] + matrix[13];
    }

    /**
     * Transform RGB value
     *
     * @param r red pixel value
     * @param g green pixel value
     * @param b blue pixel value
     * @return computed blue pixel value
     */
    public float getBlue(int r, int g, int b) {
        return r * matrix[2] + g * matrix[6] + b * matrix[10] + matrix[14];
    }

    private float getRedf(float r, float g, float b) {
        return r * matrix[0] + g * matrix[4] + b * matrix[8] + matrix[12];
    }

    private float getGreenf(float r, float g, float b) {
        return r * matrix[1] + g * matrix[5] + b * matrix[9] + matrix[13];
    }

    private float getBluef(float r, float g, float b) {
        return r * matrix[2] + g * matrix[6] + b * matrix[10] + matrix[14];
    }

}
