package io.pijun.george.interpolator;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public class Bezier65Interpolator implements Interpolator {

    private PathInterpolator mPathInterpolator;

    public Bezier65Interpolator() {
        mPathInterpolator = new PathInterpolator(0.65f, 0, 0.35f, 1.0f);
    }

    @Override
    public float getInterpolation(float t) {
        return mPathInterpolator.getInterpolation(t);
    }
}
