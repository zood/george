package io.pijun.george;

import android.Manifest;
import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.AnyThread;
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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.firebase.crash.FirebaseCrash;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;

import io.pijun.george.api.LocationIQClient;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.PackageWatcher;
import io.pijun.george.api.RevGeocoding;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.MovementType;
import io.pijun.george.models.UserRecord;
import io.pijun.george.service.FcmTokenRegistrar;
import io.pijun.george.service.LimitedShareService;
import io.pijun.george.service.LocationUploadService;
import io.pijun.george.service.MessageQueueService;
import io.pijun.george.view.MyLocationView;
import retrofit2.Call;
import retrofit2.Response;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMarkerClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, AvatarsAdapter.AvatarsAdapterListener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private MapView mMapView;
    private MapboxMap mMapboxMap;
    private volatile PackageWatcher mPkgWatcher;
    private MarkerTracker mMarkerTracker = new MarkerTracker();
    private GoogleApiClient mGoogleClient;
    private MarkerView mMeMarker;
    private boolean mCameraTracksMyLocation = false;
    private AvatarsAdapter mAvatarsAdapter = new AvatarsAdapter();
    private FriendItemsAdapter mFriendItemsAdapter = new FriendItemsAdapter();
    private EditText mUsernameField;

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

        RecyclerView avatarsView = (RecyclerView) findViewById(R.id.avatars);
        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        avatarsView.setLayoutManager(llm);
        mAvatarsAdapter.setListener(this);
        avatarsView.setAdapter(mAvatarsAdapter);

        RecyclerView friendsList = (RecyclerView) findViewById(R.id.friend_items);
        friendsList.setAdapter(mFriendItemsAdapter);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                DB db = DB.get(MapActivity.this);
                final ArrayList<FriendRecord> friends = db.getFriends();
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        mAvatarsAdapter.setFriends(friends);
                        mFriendItemsAdapter.setFriends(friends);
                    }
                });
                for (FriendRecord friend : friends) {
                    FriendLocation loc = db.getFriendLocation(friend.id);
                    if (loc != null) {
                        mFriendItemsAdapter.setFriendLocation(MapActivity.this, loc);
                    }
                }
            }
        });

        mMapView = (MapView) findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(this);

        final View myLocFab = findViewById(R.id.my_location_fab);
        myLocFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myLocFab.setSelected(true);
                mCameraTracksMyLocation = true;
                flyCameraToMyLocation();
            }
        });

        NavigationView navView = (NavigationView) findViewById(R.id.navigation);
        navView.setNavigationItemSelectedListener(navItemListener);

        mGoogleClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        startService(FcmTokenRegistrar.newIntent(this));
//        startService(ActivityMonitor.newIntent(this));
    }

    @Override
    @UiThread
    protected void onStart() {
        super.onStart();

        App.isInForeground = true;
        checkForLocationPermission();
        App.registerOnBus(this);
        mGoogleClient.connect();

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
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

        if (mGoogleClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
        }
        mGoogleClient.disconnect();

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

    private void flyCameraToMyLocation() {
        if (mMapboxMap == null) {
            return;
        }
        if (mMeMarker == null) {
            return;
        }
        CameraPosition cp = new CameraPosition.Builder()
                .target(mMeMarker.getPosition())
                .zoom(13)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        mMapboxMap.animateCamera(cu, 1000);
    }

    @Override
    @UiThread
    public void onMapReady(MapboxMap mapboxMap ) {
        if (mapboxMap == null) {
            L.i("onMapReady has a null map arg");
            FirebaseCrash.report(new Exception("MapboxMap is null"));
            return;
        }
        mMapboxMap = mapboxMap;
        CameraPosition pos = Prefs.get(this).getCameraPosition();
        if (pos != null) {
            mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
        mMapboxMap.setOnMarkerClickListener(this);
        mMapboxMap.setOnScrollListener(new MapboxMap.OnScrollListener() {
            @Override
            public void onScroll() {
                mCameraTracksMyLocation = false;
                findViewById(R.id.my_location_fab).setSelected(false);
            }
        });

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
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            L.i("  failed permission check");
            return;
        }
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleClient);
        if (lastLocation != null) {
            App.postOnBus(lastLocation);
        }

        // these are the location settings we want
        LocationRequest req = LocationRequest.create();
        req.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // check whether the location settings can currently be met by the hardware
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(req);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult result) {
                Status status = result.getStatus();
                if (status.getStatusCode() != LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                    beginLocationUpdates();
                    return;
                }

                try {
                    status.startResolutionForResult(MapActivity.this, REQUEST_LOCATION_SETTINGS);
                } catch (IntentSender.SendIntentException ignore) {}
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        L.w("onConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        L.w("onConnectionFailed: " + connectionResult);
    }

    @AnyThread
    private void beginLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This should never happen. Nobody should be calling this method before permission has been obtained.
            L.w("MapActivity.beginLocationUpdates was called before obtaining location permission");
            FirebaseCrash.report(new Exception("Location updates requested before acquiring permission"));
            return;
        }
        if (!mGoogleClient.isConnected()) {
            return;
        }
        LocationRequest req = LocationRequest.create();
        req.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        PendingResult<Status> pendingResult = LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleClient, req, this);
        pendingResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
            }
        });
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mMeMarker == null) {
            addMyLocation(location);
        } else {
            ValueAnimator posAnimator = ObjectAnimator.ofObject(
                    mMeMarker,
                    "position",
                    new Utils.LatLngEvaluator(),
                    mMeMarker.getPosition(),
                    new LatLng(location.getLatitude(), location.getLongitude()));
            posAnimator.setDuration(300);

            ValueAnimator rotAnimator = ObjectAnimator.ofObject(
                    mMeMarker,
                    "rotation",
                    new FloatEvaluator(),
                    mMeMarker.getRotation(),
                    location.getBearing());
            rotAnimator.setDuration(300);

            posAnimator.start();
            rotAnimator.start();
        }

        if (mCameraTracksMyLocation) {
            if (mMapboxMap != null) {
                CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
                mMapboxMap.animateCamera(update, 300);
            }
        }
        App.postOnBus(location);
    }

    private void addMyLocation(Location location) {
        int sixteen = getResources().getDimensionPixelSize(R.dimen.sixteen);
        Bitmap bitmap = Bitmap.createBitmap(sixteen, sixteen, Bitmap.Config.ARGB_8888);
        MyLocationView.draw(bitmap);

        Icon descriptor = IconFactory.getInstance(this).fromBitmap(bitmap);
        MarkerViewOptions opts = new MarkerViewOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .rotation(location.getBearing())
                .icon(descriptor);
        mMeMarker = mMapboxMap.addMarker(opts);
    }

    @UiThread
    private void addMapMarker(FriendRecord friend, FriendLocation loc) {
        if (mMapboxMap == null) {
            return;
        }
        int fortyEight = getResources().getDimensionPixelSize(R.dimen.thirtyTwo);
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

        beginLocationUpdates();
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
                final FriendRecord friend = DB.get(MapActivity.this).getFriendByUserId(grant.userId);
                if (friend == null) {
                    L.w("MapActivity.onLocationSharingGranted didn't find friend record");
                    return;
                }
                L.i("onLocationSharingGranted: friend found. will watch");
                mPkgWatcher.watch(friend.receivingBoxId);
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        mAvatarsAdapter.addFriend(friend);
                    }
                });
            }
        });
    }

    @Subscribe
    @UiThread
    public void onFriendLocationUpdated(final FriendLocation loc) {
        // check if we already have a marker for this friend
        mFriendItemsAdapter.setFriendLocation(this, loc);
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
        if (marker == mMeMarker) {
            return true;
        }

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
        StringBuilder snippetBuilder = new StringBuilder(relTime.toString() + ", ");
        if (loc.speed != null) {
            snippetBuilder.append(loc.speed).append(" m/s, ");
        }
        if (loc.bearing != null) {
            snippetBuilder.append(loc.bearing).append("°, ");
        }
        if (loc.accuracy != null) {
            snippetBuilder.append("±").append(loc.accuracy).append(" m, ");
        }
        String movements = MovementType.serialize(loc.movements);
        if (movements.length() > 0) {
            snippetBuilder.append(movements).append(", ");
        }
        final String snippet = snippetBuilder.toString();

        marker.setSnippet(snippet);

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
                                marker.setSnippet(snippet + revGeocoding.getArea());
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
    public void onAvatarSelected(FriendRecord fr) {
        L.i("selected: " + fr.user.username);
        Marker marker = mMarkerTracker.getById(fr.id);
        if (marker == null) {
            return;
        }

        CameraPosition cp = new CameraPosition.Builder()
                .target(marker.getPosition())
                .zoom(13)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        mMapboxMap.animateCamera(cu, 1000);
    }

    public void onAddFriendAction(View v) {
        L.i("onAddFriendAction");
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setPositiveButton("Add friend", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showAddFriendDialog();
            }
        }).setNeutralButton("Limited Share", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startService(LimitedShareService.newIntent(MapActivity.this, LimitedShareService.ACTION_START));
            }
        }).setNegativeButton("Cancel", null)
        .setCancelable(true).setMessage("Add friend?").show();
    }

    private void showAddFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(R.string.add_friend);
        builder.setView(R.layout.friend_request_form);
        builder.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String username = mUsernameField.getText().toString();
                App.runInBackground(new WorkerRunnable() {
                    @Override
                    public void run() {
                        attemptLocationGrant(username);
                    }
                });
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.show();

        mUsernameField = (EditText) dialog.findViewById(R.id.username);
    }

    @WorkerThread
    private void attemptLocationGrant(String username) {
        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(this, null, "How are you not logged in right now? (missing access token)");
            return;
        }
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            Utils.showStringAlert(this, null, "How are you not logged in right now? (missing key pair)");
            return;
        }
        OscarAPI api = OscarClient.newInstance(accessToken);
        try {
            DB db = DB.get(this);
            UserRecord userRecord = db.getUser(username);
            if (userRecord == null) {
                Response<User> searchResponse = api.searchForUser(username).execute();
                if (!searchResponse.isSuccessful()) {
                    OscarError err = OscarError.fromResponse(searchResponse);
                    Utils.showStringAlert(this, null, "Unable to find username: " + err);
                    return;
                }
                User userToRequest = searchResponse.body();
                userRecord = db.addUser(userToRequest.id, userToRequest.username, userToRequest.publicKey);
            }

            // check if we already have this user as a friend, and if we're already sharing with them
            final FriendRecord friend = db.getFriendByUserId(userRecord.id);
            if (friend != null) {
                if (friend.sendingBoxId != null) {
                    // send the sending box id to this person one more time, just in case
                    UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                    EncryptedData msg = Sodium.publicKeyEncrypt(comm.toJSON(), userRecord.publicKey, keyPair.secretKey);
                    if (msg != null) {
                        OscarClient.queueSendMessage(this, accessToken, userRecord.userId, msg, false);
                    }
                    Utils.showStringAlert(this, null, "You're already sharing your location with " + username);
                    return;
                }
            }

            byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
            new SecureRandom().nextBytes(sendingBoxId);
            UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
            EncryptedData msg = Sodium.publicKeyEncrypt(comm.toJSON(), userRecord.publicKey, keyPair.secretKey);
            if (msg == null) {
                Utils.showStringAlert(this, null, "Unable to create sharing grant");
                return;
            }
            OscarClient.queueSendMessage(this, accessToken, userRecord.userId, msg, false);

            db.grantSharingTo(userRecord.userId, sendingBoxId);
            final FriendRecord completeFriend = db.getFriendByUserId(userRecord.id);
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    mAvatarsAdapter.addFriend(completeFriend);
                }
            });

            Utils.showStringAlert(this, null, "You're now sharing with " + username);
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Network problem trying to share your location. Check your connection thent ry again.");
        } catch (DB.DBException dbe) {
            Utils.showStringAlert(this, null, "Error adding friend into database");
            FirebaseCrash.report(dbe);
        }
    }
}
