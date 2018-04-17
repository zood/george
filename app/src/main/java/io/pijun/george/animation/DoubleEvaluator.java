package io.pijun.george.animation;

import android.animation.TypeEvaluator;

public final class DoubleEvaluator implements TypeEvaluator<Double> {
    @Override
    public Double evaluate(float fraction, Double startVal, Double endVal) {
        return startVal + ((endVal - startVal) * fraction);
    }
}
