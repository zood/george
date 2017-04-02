package io.pijun.george.interpolator;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public class BezierLinearInterpolator implements Interpolator {

    private PathInterpolator mInterpolator;

    public BezierLinearInterpolator() {
        mInterpolator = new PathInterpolator(0.65f, 0, 1, 1);
    }

    @Override
    public float getInterpolation(float t) {
        return mInterpolator.getInterpolation(t);
    }
}
