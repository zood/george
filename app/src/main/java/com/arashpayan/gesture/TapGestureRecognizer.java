package com.arashpayan.gesture;

import android.graphics.PointF;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.View;

/**
 * A recognizer for tap events. By default, it recognizes a single tap, but it can be configured
 * to require any number of taps before recognizing it as a gesture. For example, to detect a
 * triple tap on the view, set the number of required taps to 3.
 */
public class TapGestureRecognizer implements View.OnTouchListener {

    private final int STATE_WAITING_FOR_INITIAL_DOWN = 0;
    private final int STATE_WAITING_FOR_UP = 1;
    private final int STATE_WAITING_FOR_DOWN = 2;
    private final int STATE_ENDED = 3;

    private int mNumReqTaps = 1;
    private final int MAX_WIGGLE_SQUARED = 100;
    private int mState = STATE_WAITING_FOR_INITIAL_DOWN;
    private int mCurrTapsCount = 0;
    private PointF mDownPoint;
    private float mDisplayDensity = -1;

    private final long MAX_PRESS_TIME = 400;
    private long mDownTime;
    private long mUpTime;
    private TapGestureListener mListener;

    /**
     * Returns the number of taps required to activate the gesture. Default is 1.
     * @return
     */
    public int getNumberOfRequiredTaps() {
        return mNumReqTaps;
    }

    public TapGestureRecognizer setNumberOfRequiredTaps(int num) {
        mNumReqTaps = num;
        return this;
    }

    /**
     * Sets the listener that gets called when the tap gesture is recognized.
     * @param l the listener to call, or <code>null</code> to remove a previous listener
     * @return the TapGestureRecognizer, as a convenience for chaining events
     */
    public TapGestureRecognizer setListener(TapGestureListener l) {
        mListener = l;
        return this;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mDisplayDensity == -1) {
            mDisplayDensity = v.getContext().getResources().getDisplayMetrics().density;
        }

        switch (mState) {
            case STATE_WAITING_FOR_INITIAL_DOWN:
                onWaitingForInitialDown(event);
                break;
            case STATE_WAITING_FOR_UP:
                onWaitingForUp(v, event);
                break;
            case STATE_WAITING_FOR_DOWN:
                onWaitingForDown(event);
                break;
            case STATE_ENDED:
                onTouchEnded(event);
                break;
        }
        return true;
    }

    private void onWaitingForInitialDown(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);
        if (action == MotionEvent.ACTION_DOWN) {
            mDownPoint = GestureRecognizer.calculateCentroid(event);
            mDownTime = System.currentTimeMillis();
            mState = STATE_WAITING_FOR_UP;
        }
    }

    private void onWaitingForUp(View v, MotionEvent event) {
        mUpTime = System.currentTimeMillis();
        if ((mUpTime - mDownTime) > MAX_PRESS_TIME) {
//            Log.i(GestureRecognizer.TAG, "you took too long to raise your finger");
            if (GestureRecognizer.isTerminalEvent(event)) {
                mState = STATE_WAITING_FOR_INITIAL_DOWN;
//                Log.i(GestureRecognizer.TAG, "sending to wait for down");
            } else {
                mState = STATE_ENDED;
//                Log.i(GestureRecognizer.TAG, "going to wait state");
            }
            mCurrTapsCount = 0;
            return;
        }

        int action = MotionEventCompat.getActionMasked(event);
        if (action == MotionEvent.ACTION_UP) {
//            Log.i(GestureRecognizer.TAG, "ttr: " + (System.currentTimeMillis() - mDownTime));
            mCurrTapsCount++;
            if (mCurrTapsCount == mNumReqTaps) {
//                Log.i(GestureRecognizer.TAG, "taps recognized!");
                mState = STATE_WAITING_FOR_INITIAL_DOWN;
                mCurrTapsCount = 0;
                if (mListener != null) {
                    mListener.onTapGestureRecognized(v, GestureRecognizer.calculateCentroid(event));
                }
            } else {
                mState = STATE_WAITING_FOR_DOWN;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mState = STATE_ENDED;
            mCurrTapsCount = 0;
        } else if (action == MotionEvent.ACTION_MOVE) {
            // make sure they haven't moved too far from the last down point
            PointF centroid = GestureRecognizer.calculateCentroid(event);
            float distSquared = GestureRecognizer.distanceSquaredInDips(mDownPoint, centroid, mDisplayDensity);
            if (distSquared > MAX_WIGGLE_SQUARED) {
//                Log.i(GestureRecognizer.TAG, "you wiggled too much!!");
                mState = STATE_ENDED;
                mCurrTapsCount = 0;
            }
        }
    }

    private void onWaitingForDown(MotionEvent event) {
        mDownTime = System.currentTimeMillis();
        if ((mDownTime - mUpTime) > MAX_PRESS_TIME) {
//            Log.i(GestureRecognizer.TAG, "too long before tapping down again: " + mDownTime + "-" + mUpTime + " = " + (mDownTime - mUpTime));
            if (GestureRecognizer.isTerminalEvent(event)) {
                mState = STATE_WAITING_FOR_INITIAL_DOWN;
            } else {
                mState = STATE_ENDED;
                // treat this as an initial down event, because the user might be attempting a new tap gesture
//                Log.i(GestureRecognizer.TAG, "treating this as an initial tap");
                onWaitingForInitialDown(event);
            }
            mCurrTapsCount = 0;
            return;
        }

        int action = MotionEventCompat.getActionMasked(event);
        if (action == MotionEvent.ACTION_DOWN) {
            mDownPoint = GestureRecognizer.calculateCentroid(event);
            mState = STATE_WAITING_FOR_UP;
        }
    }

    private void onTouchEnded(MotionEvent event) {
        // wait for all fingers to be removed before resetting
        if (GestureRecognizer.isTerminalEvent(event)) {
//            Log.i(GestureRecognizer.TAG, "last finger went up. resetting to beginning");
            mState = STATE_WAITING_FOR_INITIAL_DOWN;
        }
    }

    /**
     * TapGestureListener is the interface used for callbacks when a tap gesture is recognized.
     */
    public interface TapGestureListener {
        /**
         * Called from the main thread when the user has tapped the view the required number
         * of times.
         * @param v the view the TapGestureRecognizer was installed on
         * @param point the point (in the view's coordinate space) of the tap. If the gesture
         *              required more than one tap for recognition, this the point of the last tap.
         */
        void onTapGestureRecognized(View v, PointF point);
    }
}
