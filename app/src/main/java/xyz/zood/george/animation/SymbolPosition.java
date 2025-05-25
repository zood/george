package xyz.zood.george.animation;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import androidx.annotation.NonNull;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;

public class SymbolPosition {

    @NonNull
    private final SymbolManager sm;
    @NonNull private final Symbol s;

    private SymbolPosition(@NonNull SymbolManager sm, @NonNull Symbol s) {
        this.sm = sm;
        this.s = s;
    }

    public static void animateTo(@NonNull SymbolManager sm, @NonNull Symbol s, LatLng pos) {
        SymbolPosition sp = new SymbolPosition(sm, s);
        ValueAnimator posAnimator = ObjectAnimator.ofObject(sp, "position", new LatLngEvaluator(), s.getLatLng(), pos);
        posAnimator.setDuration(500); // 500 ms
        posAnimator.start();
    }

    public LatLng getPosition() {
        return s.getLatLng();
    }

    public void setPosition(LatLng ll) {
        s.setLatLng(ll);
        sm.update(s);
    }
}
