package com.arashpayan.gesture;

import android.graphics.PointF;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.View;

/**
 * A recognizer for detecting a finger or multiple fingers being dragged around the screen. This
 * recognizer lets you specify a minimum and maximum number of fingers required, as well as how far
 * they must move before recognizing the gesture.
 */
public class MoveGestureRecognizer implements View.OnTouchListener {

    private int mMinNumPointers = 1;
    private int mMaxNumPointers = 1;
    private int mActivationThresholdSquared = 25;

    private final int STATE_WAITING_FOR_POINTERS = 0;
    private final int STATE_WAITING_FOR_THRESHOLD = 1;
    private final int STATE_ACTIVE = 2;
    private final int STATE_ENDED = 3;
    private int mState = STATE_WAITING_FOR_POINTERS;
    private PointF mInitialCentroid = null;
    private float mDisplayDensity = -1;

    private MoveGestureListener mListener;

    /**
     * If the gesture has started, returns the start position of the current movement.
     * @return the start position of the current movement. <code>null</code> if there is no motion
     * ongoing.
     */
    public PointF getGestureStart() {
        if (mState == STATE_WAITING_FOR_POINTERS || mState == STATE_WAITING_FOR_THRESHOLD) {
            return null;
        }

        return new PointF(mInitialCentroid.x, mInitialCentroid.y);
    }

    /**
     * Sets the listener that is called for the different stages of movement recognition.
     * @param l the listener to call, or <code>null</code> to remove a previous listener
     * @return the MoveGestureRecognizer, as a convenience for chaining methods
     */
    public MoveGestureRecognizer setListener(MoveGestureListener l) {
        mListener = l;
        return this;
    }

    /**
     * Returns the number of dps (density independent pixels) the user must move their finger(s)
     * before recognizing the gesture
     * @return
     */
    public int getActivationThreshold() {
        return (int)Math.sqrt((double)mActivationThresholdSquared);
    }

    /**
     * Set the distance the user must move their finger(s) before recognizing the gesture
     * @param threshold the distance in density independent pixels
     * @return the MoveGestureRecognizer, as a convenience for chaining methods
     */
    public MoveGestureRecognizer setActivationThreshold(int threshold) {
        mActivationThresholdSquared = threshold * threshold;
        return this;
    }

    /**
     * Returns the minimum number of fingers needed to recognize the gesture. The default is 1.
     * @return
     */
    public int getMinimumNumberOfPointers() {
        return mMinNumPointers;
    }

    /**
     * Sets the minimum number of fingers needed to recognize the gesture.
     * @param num the number of fingers.
     * @return the MoveGestureRecognizer, as a convenience for chaining methods
     */
    public MoveGestureRecognizer setMinimumNumberOfPointers(int num) {
        mMinNumPointers = num;
        return this;
    }

    /**
     * Returns the maximum number of fingers that can be recognized as part of the gesture.
     * The default is 1.
     * @return
     */
    public int getMaximumNumberOfPointers() {
        return mMaxNumPointers;
    }

    /**
     * Sets the maximum number of fingers allowed as part of the gesture. If more than <code>num</code>
     * fingers are placed on the screen, an ongoing gesture will be ended.
     * @param num the maximum number of fingers permitted in the gesture
     * @return the MoveGestureRecognizer, as a convenience for chaining methods
     */
    public MoveGestureRecognizer setMaximumNumberOfPointers(int num) {
        mMaxNumPointers = num;
        return this;
    }

    public boolean onTouch(View v, MotionEvent event) {
        switch (mState) {
            case STATE_WAITING_FOR_POINTERS:
                onTouchWaitingForPointers(event);
                break;
            case STATE_WAITING_FOR_THRESHOLD:
                onTouchWaitingForThreshold(v, event);
                break;
            case STATE_ACTIVE:
                onTouchActive(v, event);
                break;
            case STATE_ENDED:
                onTouchEnded(event);
                break;
        }

        return true;
    }

    private void onTouchWaitingForPointers(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        if (pointerCount >= mMinNumPointers && pointerCount <= mMaxNumPointers) {
            mState = STATE_WAITING_FOR_THRESHOLD;
            mInitialCentroid = GestureRecognizer.calculateCentroid(event);
//            L.i("move started: " + mInitialCentroid.toString());
        }
    }

    private void onTouchWaitingForThreshold(View v, MotionEvent event) {
        if (mDisplayDensity == -1) {
            mDisplayDensity = v.getContext().getResources().getDisplayMetrics().density;
        }

        int pointerCount = event.getPointerCount();
        if (pointerCount < mMinNumPointers || pointerCount > mMaxNumPointers) {
            mState = STATE_ENDED;
//            L.i("ended because of incorrect pointer count");
            return;
        }
        if (GestureRecognizer.isTerminalEvent(event)) {
            mState = STATE_WAITING_FOR_POINTERS;
//            L.i("terminal event while waiting for threshold");
            return;
        }

        PointF centroid = GestureRecognizer.calculateCentroid(event);
        float distanceSq = GestureRecognizer.distanceSquaredInDips(mInitialCentroid, centroid, mDisplayDensity);
//        L.i("distance: " + distanceSq);
        if (distanceSq > mActivationThresholdSquared) {
            mState = STATE_ACTIVE;
            if (mListener != null) {
                mListener.onMoveGestureBegan(v, new PointF(mInitialCentroid.x, mInitialCentroid.y));
                mListener.onMoveGestureChanged(v, centroid);
            }
        }
    }

    private void onTouchActive(View v, MotionEvent event) {
        PointF centroid = GestureRecognizer.calculateCentroid(event);

        int pointerCount = event.getPointerCount();
        if (pointerCount < mMinNumPointers || pointerCount > mMaxNumPointers) {
            mState = STATE_ENDED;
//            L.i("ended from active because of incorrect pointer count");
            if (mListener != null) {
                mListener.onMoveGestureEnded(v, centroid);
            }
            return;
        }
        if (GestureRecognizer.isTerminalEvent(event)) {
//            L.i("terminal event while active");
            mState = STATE_WAITING_FOR_POINTERS;
            if (mListener != null) {
                mListener.onMoveGestureEnded(v, centroid);
            }
            return;
        }
        int action = MotionEventCompat.getActionMasked(event);
        if (action == MotionEvent.ACTION_MOVE) {
            if (mListener != null) {
                mListener.onMoveGestureChanged(v, centroid);
            }
//            L.i("active centroid: " + centroid);
        }
    }

    private void onTouchEnded(MotionEvent event) {
        // wait for all fingers to be removed before resetting
        if (GestureRecognizer.isTerminalEvent(event)) {
//            L.i("last finger went up. resetting to beginning");
            mState = STATE_WAITING_FOR_POINTERS;
        }
    }

    /**
     * MoveGestureListener is the interface used for callbacks of the various parts of a movement
     * gesture.
     */
    public interface MoveGestureListener {
        /**
         * Called when the movement gesture is recognized.
         * @param v the view the recognizer is installed on
         * @param start the point at which the gesture started
         */
        void onMoveGestureBegan(View v, PointF start);

        /**
         * Called when the user has moved further along in the gesture
         * @param v the view the recognizer is installed on
         * @param current the current point of the gesture
         */
        void onMoveGestureChanged(View v, PointF current);

        /**
         * Called when the gesture has ended
         * @param v the view the recognizer is installed on
         * @param end the end point of the gesture
         */
        void onMoveGestureEnded(View v, PointF end);
    }

}
