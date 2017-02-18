package io.pijun.george;

import android.Manifest;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.ArrayList;

import io.pijun.george.api.LocationIQClient;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.PackageWatcher;
import io.pijun.george.api.RevGeocoding;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRequested;
import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.service.FcmTokenRegistrar;
import io.pijun.george.service.LocationUploadService;
import io.pijun.george.service.MessageQueueService;
import retrofit2.Call;
import retrofit2.Response;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMarkerClickListener, MapboxMap.OnMyLocationChangeListener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;

    private MapView mMapView;
    private MapboxMap mMapboxMap;
    private volatile PackageWatcher mPkgWatcher;
    private MarkerTracker mMarkerTracker = new MarkerTracker();

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, MapActivity.class);
    }

    @Override
    @UiThread
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Is there a user account here? If not, send them to the login/sign up screen
        if (!Prefs.get(this).isLoggedIn()) {
            Intent welcomeIntent = WelcomeActivity.newIntent(this);
            startActivity(welcomeIntent);
            finish();
            return;
        }

        getWindow().getDecorView().setBackground(null);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_map);

        final Button button = (Button) findViewById(R.id.drawer_button);
        final ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) button.getLayoutParams();
        params.topMargin = getStatusBarHeight();

        mMapView = (MapView) findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(this);

        findViewById(R.id.bottom_textview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onShowFriends();
            }
        });

        NavigationView navView = (NavigationView) findViewById(R.id.navigation);
        navView.setNavigationItemSelectedListener(navItemListener);

        startService(FcmTokenRegistrar.newIntent(this));

    }

    @Override
    @UiThread
    protected void onStart() {
        super.onStart();

        App.isInForeground = true;
        checkForLocationPermission();
        App.registerOnBus(this);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                loadFriendRequests();

                Prefs prefs = Prefs.get(MapActivity.this);
                String token = prefs.getAccessToken();
                if (token == null) {
                    return;
                }
                mPkgWatcher = PackageWatcher.createWatcher(MapActivity.this, token);
                if (mPkgWatcher == null) {
                    L.w("unable to create package watcher");
                    return;
                }

                ArrayList<FriendRecord> friends = DB.get(MapActivity.this).getFriends();
                for (FriendRecord fr: friends) {
                    if (fr.receivingBoxId != null) {
                        mPkgWatcher.watch(fr.receivingBoxId);
                    }
                }

                // Request a location update from any friend that hasn't given us an update for
                // 3 minutes
                long now = System.currentTimeMillis();
                KeyPair keypair = prefs.getKeyPair();
                for (FriendRecord fr : friends) {
                    // check if this friend shares location with us
                    if (fr.receivingBoxId == null) {
                        continue;
                    }
                    FriendLocation loc = DB.get(MapActivity.this).getFriendLocation(fr.id);
                    if (loc == null || (now-loc.time) > 180 * DateUtils.SECOND_IN_MILLIS) {
                        if (keypair != null) {
                            UserComm comm = UserComm.newLocationUpdateRequest();
                            byte[] msgBytes = comm.toJSON();
                            EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, fr.user.publicKey, keypair.secretKey);
                            if (encMsg != null) {
                                OscarClient.queueSendMessage(MapActivity.this, token, fr.user.userId, encMsg, true);
                            } else {
                                L.w("Failed to encrypt a location update request message to " + fr.user.username);
                            }
                        }
                    }
                }

                OscarAPI api = OscarClient.newInstance(token);
                try {
                    Response<Message[]> response = api.getMessages().execute();
                    if (!response.isSuccessful()) {
                        OscarError err = OscarError.fromResponse(response);
                        L.w("error checking for messages: " + err);
                        return;
                    }
                    Message[] msgs = response.body();
                    if (msgs == null) {
                        return;
                    }
                    for (Message msg : msgs) {
                        MessageQueueService.queueMessage(MapActivity.this, msg);
                    }
                } catch (IOException ignore) {
                    // meh, we'll try again later
                }
            }
        });
    }

    @Override
    @UiThread
    protected void onResume() {
        super.onResume();

        mMapView.onResume();
    }

    @Override
    @UiThread
    protected void onPause() {
        mMapView.onPause();

        super.onPause();
    }

    @Override
    @UiThread
    protected void onStop() {
        super.onStop();

        if (mMapboxMap != null) {
            CameraPosition pos = mMapboxMap.getCameraPosition();
            Prefs.get(this).setCameraPosition(pos);
        }

        // hide visible info windows, so outdated info is not visible in case the activity is
        // brought back into view
        for (Marker m : mMarkerTracker.getMarkers()) {
            if (m.isInfoWindowShown()) {
                m.hideInfoWindow();
            }
        }

        App.unregisterFromBus(this);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                if (mPkgWatcher != null) {
                    mPkgWatcher.disconnect();
                    mPkgWatcher = null;
                }
            }
        });

        App.isInForeground = false;
    }

    @Override
    @UiThread
    protected void onDestroy() {
        if (mMapView != null) {
            mMapView.onDestroy();
            mMapView = null;
        }

        mMarkerTracker.clear();

        super.onDestroy();
    }

    @Override
    @UiThread
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mMapView.onSaveInstanceState(outState);
    }

    @Override
    @UiThread
    public void onLowMemory() {
        super.onLowMemory();

        mMapView.onLowMemory();
    }

    @Override
    @UiThread
    public void onMapReady(MapboxMap googleMap) {
        if (googleMap == null) {
            L.i("onMapReady has a null map arg");
        }
        mMapboxMap = googleMap;
        CameraPosition pos = Prefs.get(this).getCameraPosition();
        if (pos != null) {
            mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
        mMapboxMap.setOnMyLocationChangeListener(this);
        mMapboxMap.setOnMarkerClickListener(this);

        // add markers for all friends
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                DB db = DB.get(MapActivity.this);
                ArrayList<FriendRecord> friends = db.getFriends();
                for (final FriendRecord f : friends) {
                    final FriendLocation location = db.getFriendLocation(f.id);
                    if (location == null) {
                        continue;
                    }

                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            addMapMarker(f, location);
                        }
                    });
                }
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMapboxMap.setMyLocationEnabled(true);
        }
    }

    @UiThread
    private void addMapMarker(FriendRecord friend, FriendLocation loc) {
        if (mMapboxMap == null) {
            return;
        }
        int fortyEight = getResources().getDimensionPixelSize(R.dimen.fortyEight);
        Bitmap bitmap = Bitmap.createBitmap(fortyEight, fortyEight, Bitmap.Config.ARGB_8888);
        Identicon.draw(bitmap, friend.user.username);

        Icon descriptor = IconFactory.getInstance(this).fromBitmap(bitmap);
        MarkerOptions opts = new MarkerOptions()
                .position(new LatLng(loc.latitude, loc.longitude))
                .icon(descriptor)
                .title(friend.user.username);
        Marker marker = mMapboxMap.addMarker(opts);
        mMarkerTracker.add(marker, friend.id, loc);
    }

    @UiThread
    private void checkForLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionVerified();
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // show the reasoning
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
            builder.setTitle("Permission request");
            builder.setMessage("Pijun uses your location to show your position on the map, and to securely share it with friends that you've authorized. It's never used for any other purpose.");
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ActivityCompat.requestPermissions(
                            MapActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_LOCATION_PERMISSION);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            L.w("onRequestPermissionsResult called with unknown request code: " + requestCode);
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            L.i("permission granted");
            locationPermissionVerified();
        } else {
            L.i("permission denied");
        }
    }

    @SuppressWarnings("MissingPermission")
    @UiThread
    private void locationPermissionVerified() {
        startService(LocationUploadService.newIntent(this));
        if (mMapboxMap != null) {
            mMapboxMap.setMyLocationEnabled(true);
        }
    }

    @UiThread
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }

        return result;
    }

    @WorkerThread
    private void loadFriendRequests() {
        final int count = DB.get(this).getFriendRequestsCount();
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                TextView tv = (TextView) findViewById(R.id.bottom_textview);
                if (tv == null) {
                    return;
                }

                int drawable = 0;
                if (count > 0) {
                    drawable = R.drawable.common_google_signin_btn_icon_dark;
                }
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, drawable, 0);
            }
        });
    }

    @Subscribe
    @UiThread
    public void onLocationSharingGranted(final LocationSharingGranted grant) {
        L.i("onLocationSharingGranted");
        if (mPkgWatcher == null) {
            return;
        }

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                FriendRecord friend = DB.get(MapActivity.this).getFriendByUserId(grant.userId);
                if (friend == null) {
                    L.w("MapActivity.onLocationSharingGranted didn't find friend record");
                    return;
                }
                L.i("onLocationSharingGranted: friend found. will watch");
                mPkgWatcher.watch(friend.receivingBoxId);
            }
        });
    }

    @Subscribe
    @UiThread
    public void onLocationSharingRequested(LocationSharingRequested req) {
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                loadFriendRequests();
            }
        });
    }

    @Subscribe
    @UiThread
    public void onFriendLocationUpdated(final FriendLocation loc) {
        // check if we already have a marker for this friend
        Marker marker = mMarkerTracker.getById(loc.friendId);
        if (marker == null) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    final FriendRecord friend = DB.get(MapActivity.this).getFriendById(loc.friendId);
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            addMapMarker(friend, loc);
                        }
                    });
                }
            });
        } else {
            if (marker.isInfoWindowShown()) {
                marker.hideInfoWindow();
            }
            marker.setPosition(new LatLng(loc.latitude, loc.longitude));
            mMarkerTracker.updateLocation(loc.friendId, loc);
        }
    }

    @UiThread
    public void onShowDrawerAction(View v) {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.openDrawer(GravityCompat.START, true);
    }

    @UiThread
    private void onShowFriends() {
        Intent i = FriendsActivity.newIntent(this);
        startActivity(i);
    }

    @UiThread
    private void onLogOutAction() {
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setMessage(R.string.confirm_log_out_msg);
        builder.setCancelable(true);
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.log_out, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Prefs.get(MapActivity.this).logOut(MapActivity.this, new UiRunnable() {
                    @Override
                    public void run() {
                        Intent welcomeIntent = WelcomeActivity.newIntent(MapActivity.this);
                        startActivity(welcomeIntent);
                        finish();
                    }
                });
            }
        });
        builder.show();
    }

    @UiThread
    private void onShowLogs() {
        Intent i = LogActivity.newIntent(this);
        startActivity(i);
    }

    private NavigationView.OnNavigationItemSelectedListener navItemListener = new NavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            drawer.closeDrawers();

            if (item.getItemId() == R.id.your_friends) {
                onShowFriends();
            } else if (item.getItemId() == R.id.log_out) {
                onLogOutAction();
            } else if (item.getItemId() == R.id.view_logs) {
                onShowLogs();
            }

            return false;
        }
    };

    @Override
    @UiThread
    public boolean onMarkerClick(@NonNull final Marker marker) {
        final FriendLocation loc = mMarkerTracker.getLocation(marker);
        long now = System.currentTimeMillis();
        final CharSequence relTime;
        if (loc.time >= now-60*DateUtils.SECOND_IN_MILLIS) {
            relTime = getString(R.string.now);
        } else {
            relTime = DateUtils.getRelativeTimeSpanString(
                    loc.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
        }
        marker.setSnippet("(" + relTime.toString() + ")");

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                try {
                    Call<RevGeocoding> call = LocationIQClient.get(MapActivity.this).getReverseGeocoding("" + loc.latitude, "" + loc.longitude);
                    Response<RevGeocoding> response = call.execute();
                    if (response.isSuccessful()) {
                        final RevGeocoding revGeocoding = response.body();
                        App.runOnUiThread(new UiRunnable() {
                            @Override
                            public void run() {
                                marker.setSnippet(revGeocoding.getArea() + " (" + relTime.toString() + ")");
                            }
                        });
                    } else {
                        L.w("error calling locationiq");
                    }
                } catch (Exception ex) {
                    L.w("network error obtaining reverse geocoding", ex);
                }
            }
        });
        return false;
    }

    @Override
    public void onMyLocationChange(@Nullable Location location) {
        App.postOnBus(location);
    }
}
