package io.pijun.george;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.mapbox.mapboxsdk.MapboxAccountManager;
import com.mapbox.mapboxsdk.maps.MapView;

public class MapActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapboxAccountManager.start(this, "pk.eyJ1IjoiYXJhc2hwYXlhbiIsImEiOiJjaXJjcmpkeXcwMWZxZzJuazkxYzk0cTFzIn0.mawztj_0sJMB7wnoUCCkQw");

        setContentView(R.layout.activity_map);

        getMapView().onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        getMapView().onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        getMapView().onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        getMapView().onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        getMapView().onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        getMapView().onDestroy();
    }

    private MapView getMapView() {
        return (MapView) findViewById(R.id.map);
    }
}
