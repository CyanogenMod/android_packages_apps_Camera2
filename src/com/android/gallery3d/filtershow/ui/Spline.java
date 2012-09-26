
package com.android.gallery3d.filtershow.ui;

import java.util.Collections;
import java.util.Vector;

public class Spline {
    public Spline() {
        mPoints = new Vector<ControlPoint>();
    }

    public Spline(Spline spline) {
        mPoints = new Vector<ControlPoint>();
        for (int i = 0; i < spline.mPoints.size(); i++) {
            ControlPoint p = spline.mPoints.elementAt(i);
            mPoints.add(p);
        }
        Collections.sort(mPoints);
        delta_t = 1.0f / mPoints.size();
    }

    public ControlPoint interpolate(float t, ControlPoint p1,
            ControlPoint p2, ControlPoint p3, ControlPoint p4) {

        float t3 = t * t * t;
        float t2 = t * t;
        float b1 = 0.5f * (-t3 + 2 * t2 - t);
        float b2 = 0.5f * (3 * t3 - 5 * t2 + 2);
        float b3 = 0.5f * (-3 * t3 + 4 * t2 + t);
        float b4 = 0.5f * (t3 - t2);

        ControlPoint b1p1 = p1.multiply(b1);
        ControlPoint b2p2 = p2.multiply(b2);
        ControlPoint b3p3 = p3.multiply(b3);
        ControlPoint b4p4 = p4.multiply(b4);

        return b1p1.add(b2p2.add(b3p3.add(b4p4)));
    }

    public void addPoint(float x, float y) {
        addPoint(new ControlPoint(x, y));
    }

    public void addPoint(ControlPoint v) {
        mPoints.add(v);
        Collections.sort(mPoints);
        delta_t = 1.0f / mPoints.size();
    }

    public ControlPoint getPoint(float t) {
        int p = (int) (t / delta_t);
        int p0 = p - 1;
        int max = mPoints.size() - 1;

        if (p0 < 0) {
            p0 = 0;
        } else if (p0 >= max) {
            p0 = max;
        }
        int p1 = p;
        if (p1 < 0) {
            p1 = 0;
        } else if (p1 >= max) {
            p1 = max;
        }
        int p2 = p + 1;
        if (p2 < 0) {
            p2 = 0;
        } else if (p2 >= max) {
            p2 = max;
        }
        int p3 = p + 2;
        if (p3 < 0) {
            p3 = 0;
        } else if (p3 >= max) {
            p3 = max;
        }
        float lt = (t - delta_t * (float) p) / delta_t;
        return interpolate(lt, mPoints.elementAt(p0),
                mPoints.elementAt(p1), mPoints.elementAt(p2),
                mPoints.elementAt(p3));

    }

    public int getNbPoints() {
        return mPoints.size();
    }

    public ControlPoint getPoint(int n) {
        return mPoints.elementAt(n);
    }

    public boolean isPointContained(float x, int n) {
        for (int i = 0; i < n; i++) {
            ControlPoint point = mPoints.elementAt(i);
            if (point.x > x) {
                return false;
            }
        }
        for (int i = n + 1; i < mPoints.size(); i++) {
            ControlPoint point = mPoints.elementAt(i);
            if (point.x < x) {
                return false;
            }
        }
        return true;
    }

    public void deletePoint(int n) {
        mPoints.remove(n);
        Collections.sort(mPoints);
        delta_t = 1.0f / (mPoints.size() - 1f);
    }

    private Vector<ControlPoint> mPoints;
    private float delta_t;

    public Spline copy() {
        Spline spline = new Spline();
        for (int i = 0; i < mPoints.size(); i++) {
            ControlPoint point = mPoints.elementAt(i);
            spline.addPoint(point.copy());
        }
        return spline;
    }
}
