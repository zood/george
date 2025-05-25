package xyz.zood.george;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;

import java.util.HashMap;

import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;

public class FriendSymbolTracker {

    @NonNull private final SymbolManager sm;
    @NonNull private final Style style;
    @NonNull private final MapLibreMap map;
    @NonNull private final HashMap<Long, FriendSymbol> idToSymbol = new HashMap<>();

    public FriendSymbolTracker(@NonNull SymbolManager sm, @NonNull MapLibreMap map, @NonNull Style style) {
        this.map = map;
        this.sm = sm;
        this.style = style;
    }

    @UiThread
    public void clear() {
        idToSymbol.clear();
    }


    @Nullable @UiThread
    public FriendSymbol get(long friendId) {
        return idToSymbol.get(friendId);
    }

    @Nullable @UiThread
    public FriendSymbol get(Symbol symbol) {
        for (var s : idToSymbol.values()) {
            if (s.getSymbol().equals(symbol)) {
                return s;
            }
        }

        return null;
    }

    @UiThread
    public void hideErrorCircle() {
        for (var fs: idToSymbol.values()) {
            fs.setErrorCircleVisible(false);
        }
    }

    @UiThread
    public FriendSymbol put(@NonNull Context ctx, @NonNull Bitmap icon, @NonNull FriendRecord f, FriendLocation l) {
        FriendSymbol fs = idToSymbol.get(f.id);
        if (fs != null) {
            // the friend was already added
            return fs;
        }

        fs = new FriendSymbol(ctx, icon, map, sm, style, f, l);
        idToSymbol.put(f.id, fs);
        return fs;
    }

    @UiThread
    public void removeFriend(long friendId) {
        var fs = idToSymbol.get(friendId);
        if (fs == null) {
            return;
        }

        fs.deleteSymbol(); // clean up it's avatar, error circles and style components
        idToSymbol.remove(friendId); // remove it so we don't try to interact with it anymore
    }
}
