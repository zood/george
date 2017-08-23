package com.arashpayan.gesture;

import android.graphics.PointF;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;

/**
 * GestureRecognizer is a utility class with common functionality needed by the different
 * recognizers. Only useful if you're writing new gesture recognizers.
 */
public class GestureRecognizer {

    protected static final String TAG = "GestureRecognizer";

    protected static PointF calculateCentroid(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        float xSum = 0;
        float ySum = 0;
        for (int i=0; i<pointerCount; i++) {
            xSum += MotionEventCompat.getX(event, i);
            ySum += MotionEventCompat.getY(event, i);
        }

        return new PointF(xSum/(float)pointerCount, ySum/(float)pointerCount);
    }

    protected static float distanceSquared(PointF p1, PointF p2) {
        float xDiff = p1.x - p2.x;
        float yDiff = p1.y - p2.y;
        return xDiff*xDiff + yDiff*yDiff;
    }

    protected static float distanceSquaredInDips(PointF p1, PointF p2, float density) {
        float xDiff = (p1.x - p2.x)/density;
        float yDiff = (p1.y - p2.y)/density;
        return xDiff*xDiff + yDiff*yDiff;
    }

    protected static boolean isTerminalEvent(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        int action = MotionEventCompat.getActionMasked(event);
        if (pointerCount == 1 && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            return true;
        }

        return false;
    }

}
