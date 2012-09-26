
package com.android.gallery3d.filtershow.ui;

class ControlPoint implements Comparable {
    public ControlPoint(float px, float py) {
        x = px;
        y = py;
    }

    public ControlPoint multiply(float m) {
        return new ControlPoint(x * m, y * m);
    }

    public ControlPoint add(ControlPoint v) {
        return new ControlPoint(x + v.x, y + v.y);
    }

    public ControlPoint sub(ControlPoint v) {
        return new ControlPoint(x - v.x, y - v.y);
    }

    public float x;
    public float y;

    public ControlPoint copy() {
        return new ControlPoint(x, y);
    }

    @Override
    public int compareTo(Object another) {
        ControlPoint p = (ControlPoint) another;
        if (p.x < x) {
            return 1;
        } else if (p.x > x) {
            return -1;
        }
        return 0;
    }
}
