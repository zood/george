package io.pijun.george.interpolator;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public class LinearBezierInterpolator implements Interpolator {

    private PathInterpolator mInterpolator;

    public LinearBezierInterpolator() {
        mInterpolator = new PathInterpolator(0, 0, 0.65f, 1);
    }

    @Override
    public float getInterpolation(float t) {
        return mInterpolator.getInterpolation(t);
    }
}
