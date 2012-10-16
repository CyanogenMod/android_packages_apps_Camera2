
package com.android.gallery3d.filtershow.ui;

public class ControlPoint implements Comparable {
    public float x;
    public float y;

    public ControlPoint(float px, float py) {
        x = px;
        y = py;
    }

    public ControlPoint(ControlPoint point) {
        x = point.x;
        y = point.y;
    }

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
