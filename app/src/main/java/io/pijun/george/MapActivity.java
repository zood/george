package io.pijun.george;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;

import io.pijun.george.animation.DoubleEvaluator;
import io.pijun.george.animation.LatLngEvaluator;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.OscarSocket;
import io.pijun.george.api.PushNotification;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.service.BackupDatabaseJob;
import io.pijun.george.service.LimitedShareService;
import io.pijun.george.view.AvatarRenderer;
import io.pijun.george.view.MyLocationView;
import retrofit2.Response;
import xyz.zood.george.AddFriendDialog;
import xyz.zood.george.AvatarManager;
import xyz.zood.george.R;
import xyz.zood.george.databinding.ActivityMapBinding;
import xyz.zood.george.SafetyNumberActivity;
import xyz.zood.george.notifier.BackgroundDataRestrictionNotifier;
import xyz.zood.george.notifier.ClientNotConnectedNotifier;
import xyz.zood.george.notifier.LocationPermissionNotifier;
import xyz.zood.george.widget.InfoPanel;
import xyz.zood.george.widget.RadialMenu;
import xyz.zood.george.widget.ZoodDialog;

public final class MapActivity extends AppCompatActivity implements OnMapReadyCallback, DB.Listener, AuthenticationManager.Listener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private MapView mMapView;
    private GoogleMap mGoogMap;
    private OscarSocket oscarSocket;
    private final MarkerTracker mMarkerTracker = new MarkerTracker();
    private FusedLocationProviderClient mLocationProviderClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private Marker mMeMarker;
    private Circle mMyCircle;
    private long friendForCameraToTrack = -1;
    private Circle mCurrentCircle;
    private boolean isStarted = false;
    private AvatarsAdapter avatarsAdapter;
    private RadialMenu radialMenu;
    private InfoPanel infoPanel;
    private LocationPermissionNotifier locationPermissionNotifier;
    private ClientNotConnectedNotifier notConnectedNotifier;

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, MapActivity.class);
    }

    @Override
    @UiThread
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Is there a user account here? If not, send them to the login/sign up screen
        if (!AuthenticationManager.isLoggedIn(this)) {
            Intent welcomeIntent = WelcomeActivity.newIntent(this);
            startActivity(welcomeIntent);
            finish();
            return;
        }

        ActivityMapBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_map);

        infoPanel = new InfoPanel(binding.infoPanel, this, infoPanelListener);
        radialMenu = new RadialMenu(binding.root);

        mMapView = findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(this);

        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.avatars.setLayoutManager(llm);
        avatarsAdapter = new AvatarsAdapter(avatarsSelectionListener);
        binding.avatars.setAdapter(avatarsAdapter);

        final View myLocFab = findViewById(R.id.my_location_fab);
        myLocFab.setOnClickListener(v -> {
            myLocFab.setSelected(true);
            friendForCameraToTrack = 0;
            flyCameraToMyLocation();

            // if we're showing the avatar info, hide it
            infoPanel.hide();
            radialMenu.setVisible(true);
            radialMenu.flyBack();
        });

        mLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(3 * DateUtils.SECOND_IN_MILLIS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();

        DB.get().addListener(this);
        AuthenticationManager.get().addListener(this);
        AvatarManager.addListener(avatarListener);

        oscarSocket = new OscarSocket(oscarSocketListener);

        getLifecycle().addObserver(new BackgroundDataRestrictionNotifier(this, binding.banners));
        locationPermissionNotifier = new LocationPermissionNotifier(this, binding.banners);
        getLifecycle().addObserver(locationPermissionNotifier);

        notConnectedNotifier = new ClientNotConnectedNotifier(this, binding.banners);
    }

    @Override
    @UiThread
    protected void onStart() {
        super.onStart();

        isStarted = true;
        App.isInForeground = true;
        checkForLocationPermission();
        mMapView.onStart();
        UpdateStatusTracker.addListener(updateStatusTrackerListener);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                Prefs prefs = Prefs.get(MapActivity.this);
                String token = prefs.getAccessToken();
                if (token == null) {
                    return;
                }

                oscarSocket.connect(token);

                ArrayList<FriendRecord> friends = DB.get().getFriends();
                avatarsAdapter.setFriends(friends);
                for (FriendRecord fr: friends) {
                    if (fr.receivingBoxId != null) {
                        oscarSocket.watch(fr.receivingBoxId);
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
                        MessageProcessor.queue(msg);
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
        super.onPause();

        mMapView.onPause();
    }

    @Override
    @UiThread
    protected void onStop() {
        super.onStop();

        isStarted = false;
        UpdateStatusTracker.removeListener(updateStatusTrackerListener);
        mMapView.onStop();

        if (mGoogMap != null) {
            CameraPosition pos = mGoogMap.getCameraPosition();
            Prefs.get(this).setCameraPosition(pos);
        }

        // stop receiving location updates
        mLocationProviderClient.removeLocationUpdates(mLocationCallbackHelper);

        oscarSocket.disconnect();

        App.isInForeground = false;
    }

    @Override
    @UiThread
    protected void onDestroy() {
        DB.get().removeListener(this);
        AuthenticationManager.get().removeListener(this);
        AvatarManager.removeListener(avatarListener);
        mMarkerTracker.clear();
        avatarsAdapter = null;

        super.onDestroy();
        if (mMapView != null) {
            mMapView.onDestroy();
            mMapView = null;
        }
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
        if (mGoogMap == null) {
            return;
        }
        if (mMeMarker == null) {
            return;
        }
        float zoom = Math.max(mGoogMap.getCameraPosition().zoom, Constants.DEFAULT_ZOOM_LEVEL);
        CameraPosition cp = new CameraPosition.Builder()
                .zoom(zoom)
                .target(mMeMarker.getPosition())
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        mGoogMap.animateCamera(cu);
    }

    @Override
    @UiThread
    public void onMapReady(GoogleMap mapboxMap) {
        if (mapboxMap == null) {
            L.i("onMapReady has a null map arg");
            return;
        }
        mGoogMap = mapboxMap;
        CameraPosition pos = Prefs.get(this).getCameraPosition();
        if (pos != null) {
            mGoogMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
        mGoogMap.setOnMarkerClickListener(markerClickListener);
        mGoogMap.getUiSettings().setCompassEnabled(false);
        mGoogMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                if (reason == REASON_GESTURE) {
                    friendForCameraToTrack = -1;
                    findViewById(R.id.my_location_fab).setSelected(false);
                    radialMenu.flyBack();
                }
            }
        });
        mGoogMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                friendForCameraToTrack = -1;
                findViewById(R.id.my_location_fab).setSelected(false);
                infoPanel.hide();
                radialMenu.setVisible(true);
                radialMenu.flyBack();
                if (mCurrentCircle != null) {
                    mCurrentCircle.remove();
                    mCurrentCircle = null;
                }
            }
        });

        // add markers for all friends
        App.runInBackground(() -> {
            DB db = DB.get();
            ArrayList<FriendRecord> friends = db.getFriends();
            for (final FriendRecord f : friends) {
                final FriendLocation location = db.getFriendLocation(f.id);
                if (location == null) {
                    continue;
                }
                addMapMarker(f, location);
            }
        });
    }

    @AnyThread
    private void beginLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This should never happen. Nobody should be calling this method before permission has been obtained.
            L.w("MapActivity.beginLocationUpdates was called before obtaining location permission");
            CloudLogger.log("Location updates requested before acquiring permission");
            return;
        }

        mLocationProviderClient.requestLocationUpdates(mLocationRequest, mLocationCallbackHelper, Looper.getMainLooper());
    }

    @UiThread
    private void addMyErrorCircle(Location location) {
        if (mGoogMap == null) {
            return;
        }

        if (!location.hasAccuracy()) {
            return;
        }

        CircleOptions opts = new CircleOptions()
                .center(new LatLng(location.getLatitude(), location.getLongitude()))
                .radius(location.getAccuracy())
                .strokeColor(Color.TRANSPARENT)
                .fillColor(0x8032b6f4);
        mMyCircle = mGoogMap.addCircle(opts);
    }

    @UiThread
    private void addMyLocation(Location location) {
        if (mGoogMap == null) {
            return;
        }

        Bitmap bitmap = MyLocationView.getBitmap(this);
        BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions opts = new MarkerOptions()
                .position(ll)
                .anchor(0.5f, 0.5f)
                .icon(descriptor);
        mMeMarker = mGoogMap.addMarker(opts);
    }

    @WorkerThread
    private void addMapMarker(@NonNull FriendRecord friend, @NonNull FriendLocation loc) {
        if (mGoogMap == null) {
            return;
        }

        BitmapDescriptor icon = AvatarRenderer.getBitmapDescriptor(this, friend.user.username, R.dimen.thirtySix);

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                // check if it's already there
                if (mMarkerTracker.getById(friend.id) != null) {
                    // don't add another one
                    return;
                }
                MarkerOptions opts = new MarkerOptions()
                        .position(new LatLng(loc.latitude, loc.longitude))
                        .icon(icon)
                        .anchor(0.5f, 0.5f)
                        .title(friend.user.username);
                Marker marker = mGoogMap.addMarker(opts);
                marker.setTag(loc);
                mMarkerTracker.add(marker, friend.id, loc);
            }
        });
    }

    @UiThread
    private void checkForLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionVerified();
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // show the reasoning
            ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.location_permission_reason_msg));
            dialog.setTitle(getString(R.string.permission_request));
            dialog.setButton1(getString(R.string.ok), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ActivityCompat.requestPermissions(
                            MapActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_LOCATION_PERMISSION);
                }
            });
            dialog.show(getSupportFragmentManager(), null);
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
            locationPermissionNotifier.show();
        }
    }

    @UiThread
    private void locationPermissionVerified() {
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnFailureListener(this, e -> {
                    int statusCode = ((ApiException) e).getStatusCode();
                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                ResolvableApiException rae = (ResolvableApiException) e;
                                rae.startResolutionForResult(this, REQUEST_LOCATION_SETTINGS);
                            } catch (IntentSender.SendIntentException sie) {
                                L.w("Unable to start settings resolution", sie);
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String errMsg = "Location settings are inadequate, and cannot be fixed here. Fix in Settings.";
                            Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                            break;
                    }

                });

        beginLocationUpdates();
    }

    @UiThread
    private void showInfoPanel(@NonNull FriendRecord friend, @Nullable FriendLocation loc) {
        radialMenu.setVisible(false);
        infoPanel.show(friend, loc);

        // if the location data for the friend is really old, request a refresh
        if (loc != null) {
            if (System.currentTimeMillis() - loc.time > DateUtils.MINUTE_IN_MILLIS * 20) {
                infoPanelListener.onInfoPanelLocationRequested(friend);
            }
        }
    }

    public void onStartLimitedShareAction(View v) {
        radialMenu.flyBack();
        Intent i = LimitedShareService.newIntent(this, LimitedShareService.ACTION_START);
        ContextCompat.startForegroundService(this, i);
    }

    @UiThread
    private void removeFriendFromMap(long friendId) {
        Marker marker = mMarkerTracker.removeMarker(friendId);
        if (marker != null) {
            marker.remove();
        }
        if (mCurrentCircle != null) {
            Long circleFriendId = (Long) mCurrentCircle.getTag();
            if (circleFriendId != null) {
                if (circleFriendId == friendId) {
                    mCurrentCircle.remove();
                }
            }
        }
    }

    @UiThread
    public void showAddFriendDialog(View v) {
        radialMenu.flyBack();

        AddFriendDialog fragment = AddFriendDialog.newInstance();
        getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .addToBackStack(null)
                .commit();
    }

    @UiThread
    private void showFriendErrorCircle(@NonNull FriendLocation loc) {
        if (mGoogMap == null) {
            return;
        }

        // check if we even need a circle
        if (loc.accuracy == null) {
            // no circle needed
            // check if there's a circle that we need to remove
            if (mCurrentCircle != null) {
                mCurrentCircle.remove();
                mCurrentCircle = null;
            }
            return;
        }
        // check if there is already a circle
        if (mCurrentCircle != null) {
            // does this circle already belong to this friend?
            Long friendId = (Long) mCurrentCircle.getTag();
            if (friendId == null || friendId != loc.friendId) {
                // nope! Remove it.
                mCurrentCircle.remove();
                mCurrentCircle = null;
            }
        }

        // If this friend doesn't have a circle, create it
        if (mCurrentCircle == null) {
            CircleOptions opts = new CircleOptions()
                    .center(new LatLng(loc.latitude, loc.longitude))
                    .radius(loc.accuracy)
                    .strokeColor(Color.TRANSPARENT)
                    .fillColor(ContextCompat.getColor(this, R.color.error_circle_fill));
            mCurrentCircle = mGoogMap.addCircle(opts);
            mCurrentCircle.setTag(loc.friendId);
        } else {
            // This friend already has a circle, so just adjust the radius and position
            mCurrentCircle.setRadius(loc.accuracy);
            mCurrentCircle.setCenter(new LatLng(loc.latitude, loc.longitude));
        }

    }

    @UiThread
    public void onShowSettings(View v) {
        startActivityForResult(SettingsActivity.newIntent(this), SettingsActivity.REQUEST_EXIT);
        radialMenu.flyBack();
    }

    @WorkerThread
    private void startSharingWith(@NonNull FriendRecord friend) {
        byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(sendingBoxId);
        // send the sending box id to the friend
        UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
        String errMsg = OscarClient.queueSendMessage(this, friend.user, comm, false, false);
        if (errMsg != null) {
            CloudLogger.log(new RuntimeException(errMsg));
            return;
        }

        // add this to our database
        DB db = DB.get();
        try {
            db.startSharingWith(friend.user, sendingBoxId);
            AvatarManager.sendAvatarToUser(this, friend.user);
        } catch (DB.DBException ex) {
            CloudLogger.log(ex);
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    Toast.makeText(MapActivity.this, R.string.toast_enable_sharing_error_msg, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (IOException ex) {
            CloudLogger.log(ex);
        }
        BackupDatabaseJob.scheduleBackup(this);
    }

    @WorkerThread
    private void stopSharingWith(@NonNull FriendRecord friend) {
        // remove the sending box id from the database
        DB db = DB.get();
        try {
            db.stopSharingWith(friend.user);
        } catch (DB.DBException ex) {
            CloudLogger.log(ex);
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    Toast.makeText(MapActivity.this, R.string.toast_disable_sharing_error_msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
        BackupDatabaseJob.scheduleBackup(this);

        UserComm comm = UserComm.newLocationSharingRevocation();
        String errMsg = OscarClient.queueSendMessage(this, friend.user, comm, false, false);
        if (errMsg != null) {
            CloudLogger.log(new RuntimeException(errMsg));
        }
    }

    @UiThread
    private void updateMarker(@NonNull FriendLocation loc) {
        // check if we already have a marker for this friend
        Marker marker = mMarkerTracker.getById(loc.friendId);
        if (marker == null) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    FriendRecord friend = DB.get().getFriendById(loc.friendId);
                    if (friend == null) {
                        // should never happen
                        return;
                    }
                    addMapMarker(friend, loc);
                }
            });
            if (friendForCameraToTrack == loc.friendId) {
                showFriendErrorCircle(loc);
            }
        } else {
            marker.setPosition(new LatLng(loc.latitude, loc.longitude));
            marker.setTag(loc);
            mMarkerTracker.updateLocation(loc.friendId, loc);

            // if there is an error circle being shown on this marker, adjust it too
            if (mCurrentCircle != null) {
                Long friendId = (Long) mCurrentCircle.getTag();
                if (friendId != null && loc.friendId == friendId) {
                    if (loc.accuracy != null) {
                        mCurrentCircle.setRadius(loc.accuracy);
                        mCurrentCircle.setCenter(new LatLng(loc.latitude, loc.longitude));
                    } else {
                        // no accuracy, so remove the circle
                        mCurrentCircle.remove();
                        mCurrentCircle = null;
                    }
                }
            }
        }
    }

    private final UpdateStatusTracker.Listener updateStatusTrackerListener = new UpdateStatusTracker.Listener() {
        @Override
        public void onUpdateStatusChanged(long friendId) {
            if (infoPanel.getFriendId() == friendId) {
                infoPanel.updateRefreshProgressBarState(friendId);
            }
        }
    };

    private final LocationCallback mLocationCallbackHelper = new LocationCallback() {
        @Override
        @UiThread
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (mMeMarker == null) {
                addMyLocation(location);
            } else {
                LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
                ValueAnimator posAnimator = ObjectAnimator.ofObject(
                        mMeMarker,
                        "position",
                        new LatLngEvaluator(),
                        mMeMarker.getPosition(),
                        ll);
                posAnimator.setDuration(500);

                posAnimator.start();
            }

            if (mMyCircle == null) {
                addMyErrorCircle(location);
            } else {
                if (!location.hasAccuracy()) {
                    mMyCircle.remove();
                    mMyCircle = null;
                } else {
                    ValueAnimator posAnimator = ObjectAnimator.ofObject(
                            mMyCircle,
                            "center",
                            new LatLngEvaluator(),
                            mMyCircle.getCenter(),
                            new LatLng(location.getLatitude(), location.getLongitude()));
                    posAnimator.setDuration(500);
                    ValueAnimator errAnimator = ObjectAnimator.ofObject(
                            mMyCircle,
                            "radius",
                            new DoubleEvaluator(),
                            mMyCircle.getRadius(),
                            (double)location.getAccuracy());
                    errAnimator.setDuration(500);

                    posAnimator.start();
                    errAnimator.start();
                }
            }

            if (friendForCameraToTrack == 0) {
                if (mGoogMap != null) {
                    CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
                    mGoogMap.animateCamera(update);
                }
            }

            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    LocationUtils.upload(location);
                }
            });
        }
    };

    //region AvatarManager.Listener
    private final AvatarManager.Listener avatarListener = new AvatarManager.Listener() {
        @Override
        public void onAvatarUpdated(@Nullable String username) {
            if (username == null) {
                return;
            }
            avatarsAdapter.onAvatarUpdated(username);
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    UserRecord user = DB.get().getUser(username);
                    if (user == null) {
                        L.w("MapActivity.onAvatarUpdated for a user we don't know about: " + username);
                        return;
                    }
                    FriendRecord friend = DB.get().getFriendByUserId(user.id);
                    if (friend == null) {
                        // This person is no friend of ours. Get outta here.
                        return;
                    }
                    BitmapDescriptor icon = AvatarRenderer.getBitmapDescriptor(MapActivity.this, username, R.dimen.thirtySix);
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            Marker marker = mMarkerTracker.getById(friend.id);
                            if (marker != null) {
                                marker.setIcon(icon);
                            }
                        }
                    });
                }
            });
        }
    };
    //endregion

    //region DB.Listener
    @Override
    public void onFriendLocationUpdated(final FriendLocation loc) {
        // If the info panel is already showing for this friend, update it
        final FriendRecord friend = infoPanel.getFriend();
        if (friend != null && friend.id == loc.friendId) {
            showInfoPanel(friend, loc);
        }

        updateMarker(loc);

        if (friendForCameraToTrack == loc.friendId && mGoogMap != null) {
            CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
            mGoogMap.animateCamera(update);
        }
    }

    @Override
    public void onFriendRemoved(long friendId) {
        removeFriendFromMap(friendId);
        avatarsAdapter.removeFriend(friendId);
        // if the info panel is showing their info, hide it
        if (infoPanel.isHidden()) {
            return;
        }
        FriendRecord friend = infoPanel.getFriend();
        if (friend != null && friend.id == friendId) {
            infoPanel.hide();
            friendForCameraToTrack = -1;
            radialMenu.setVisible(true);
        }
    }

    @Override
    public void onLocationSharingGranted(long userId) {
        L.i("onLocationSharingGranted");
        if (oscarSocket == null) {
            return;
        }
        final FriendRecord friend = DB.get().getFriendByUserId(userId);
        if (friend == null) {
            L.w("MapActivity.onLocationSharingGranted didn't find friend record");
            return;
        }
        // Perform the null check because the location sharing grant may have been revoked.
        // (If the other person is flipping the sharing switch back and forth).
        if (friend.receivingBoxId != null) {
            L.i("onLocationSharingGranted: friend found. will watch");
            oscarSocket.watch(friend.receivingBoxId);
        }
        avatarsAdapter.addFriend(friend);

        // If the friend is currently visible on the info panel, update the panel
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (infoPanel.getFriendId() == friend.id) {
                    showInfoPanel(friend, null);
                }
            }
        });
    }

    @Override
    public void onLocationSharingRevoked(long userId) {
        // remove the map marker, if there is one
        DB db = DB.get();
        FriendRecord friend = db.getFriendByUserId(userId);
        if (friend == null) {
            return;
        }
        App.runOnUiThread(() -> {
            removeFriendFromMap(friend.id);

            // If the friend is currently visible on the info panel, update the panel
            if (infoPanel.getFriendId() == friend.id) {
                showInfoPanel(friend, null);
            }
        });
    }

    @Override
    public void onStartedSharingWithUser(long userId) {
        // This is called when we add a friend, or when we re-enable sharing with a friend
        FriendRecord friend = DB.get().getFriendByUserId(userId);
        if (friend == null) {
            L.w("MapActivity.onStartedSharingWithUser couldn't obtain a FriendRecord");
            return;
        }
        // We need to make sure the friend is in the avatar adapter
        // The adapter handles the case if the friend is already in there.
        avatarsAdapter.addFriend(friend);

        // update the FriendRecord in the info panel, if necessary
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (infoPanel.getFriendId() == friend.id) {
                    infoPanel.updateFriendRecord(friend);
                }
            }
        });
    }

    @Override
    public void onStoppedSharingWithUser(long userId) {
        // This is called when we stopped sharing with another user
        // e.g. we toggled the share switch on the info panel
        FriendRecord friend = DB.get().getFriendByUserId(userId);
        if (friend == null) {
            L.w("MapActivity.onStoppedSharingWithUser couldn't obtain a FriendRecord");
            return;
        }
        // Update the FriendRecord in our avatar list
        avatarsAdapter.addFriend(friend);

        // update the FriendRecord in the info panel, if necessary
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (infoPanel.getFriendId() == friend.id) {
                    infoPanel.updateFriendRecord(friend);
                }
            }
        });
    }

    //endregion

    //region AuthenticationManager.Listener

    @Override
    public void onUserLoggedOut() {
        Intent i = WelcomeActivity.newIntent(this);
        startActivity(i);
        finish();
    }

    //endregion

    //region OscarSocket.Listener

    private final OscarSocket.Listener oscarSocketListener = new OscarSocket.Listener() {

        @Override
        public void onConnect() {
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    notConnectedNotifier.hide();
                }
            });
        }

        @Override
        public void onDisconnect(int code, String reason) {
            if (!isStarted) {
                return;
            }
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    notConnectedNotifier.show();
                }
            });
            try {
                // sleep for a bit before attempting to reconnect
                Thread.sleep(5000);
            } catch (InterruptedException ignore) {}
            // are we still running after our siesta?
            if (!isStarted) {
                return;
            }
            String token = Prefs.get(MapActivity.this).getAccessToken();
            if (token == null) {
                return;
            }
            oscarSocket.connect(token);
            ArrayList<FriendRecord> friends = DB.get().getFriends();
            for (FriendRecord f : friends) {
                if (f.receivingBoxId != null) {
                    oscarSocket.watch(f.receivingBoxId);
                }
            }
        }

        @Override
        public void onPackageReceived(@NonNull byte[] boxId, @NonNull EncryptedData data) {
            FriendRecord friend = DB.get().getFriendByReceivingBoxId(boxId);
            if (friend == null) {
                return;
            }
            MessageProcessor.Result result = MessageProcessor.decryptAndProcess(MapActivity.this, friend.user.userId, data.cipherText, data.nonce);
            if (result != MessageProcessor.Result.Success) {
                L.i("MA error decrypting+processing dropped package: " + result);
            }
        }

        @Override
        public void onPushNotificationReceived(@NonNull PushNotification notification) {
            L.i("MA.onPushNotificationReceived");
            MessageProcessor.Result result = MessageProcessor.decryptAndProcess(MapActivity.this,
                    notification.senderId,
                    notification.cipherText,
                    notification.nonce);
            if (result != MessageProcessor.Result.Success) {
                L.i("MA error decrypting+processing push notification: " + result);
                return;
            }

            // Was this a transient message?
            if (notification.id == null || notification.id.equals("0")) {
                return;
            }

            // Wasn't transient, so let's delete it from the server
            long msgId;
            try {
                msgId = Long.valueOf(notification.id);
                if (msgId == 0) { // technically, a redundant check
                    return;
                }
            } catch (NumberFormatException ex) {
                // something is up on the server
                L.w("failed to parse push message id", ex);
                return;
            }

            String token = Prefs.get(MapActivity.this).getAccessToken();
            if (token == null) {
                return;
            }
            OscarClient.queueDeleteMessage(MapActivity.this, token, msgId);
        }
    };
    //endregion

    //region InfoPanel listener
    private final InfoPanel.Listener infoPanelListener = new InfoPanel.Listener() {

        @Override
        public void onInfoPanelLocationRequested(@NonNull FriendRecord friend) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    if (friend.receivingBoxId == null) {
                        L.w(friend.user.username + " doesn't share their location with us");
                        return;
                    }
                    L.i("requesting location from " + friend.user.username);
                    UserComm comm = UserComm.newLocationUpdateRequest();
                    UpdateStatusTracker.setLastRequestTime(friend.id, System.currentTimeMillis());
                    String errMsg = OscarClient.queueSendMessage(MapActivity.this, friend.user, comm, true, true);
                    if (errMsg != null) {
                        L.w("manual location request failed: " + errMsg);
                    }
                }
            });
        }

        @Override
        public void onInfoPanelRemoveFriend(@NonNull FriendRecord friend) {
            ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.remove_friend_prompt_msg));
            dialog.setTitle(getString(R.string.remove_friend_interrogative));
            dialog.setButton1(getString(R.string.remove), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    App.runInBackground(new WorkerRunnable() {
                        @Override
                        public void run() {
                            stopSharingWith(friend);
                            try {
                                DB.get().removeFriend(friend);
                            } catch (DB.DBException ex) {
                                L.w("Error removing friend: " + friend, ex);
                                Utils.showAlert(MapActivity.this, R.string.unexpected_error, R.string.remove_friend_error_msg, getSupportFragmentManager());
                            }
                        }
                    });
                }
            });
            dialog.setButton2(getString(R.string.cancel), null);
            dialog.show(getSupportFragmentManager(), null);
        }

        @Override
        public void onInfoPanelShareToggled(@NonNull FriendRecord friend, boolean shouldShare) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    if (shouldShare) {
                        startSharingWith(friend);
                    } else {
                        stopSharingWith(friend);
                    }
                }
            });
        }

        @Override
        public void onInfoPanelViewSafetyNumber(@NonNull FriendRecord friend) {
            Intent i = SafetyNumberActivity.newIntent(MapActivity.this, friend.user);
            startActivity(i);
        }
    };
    //endregion

    //region AvatarsAdapter.Listener

    private final AvatarsAdapter.Listener avatarsSelectionListener = new AvatarsAdapter.Listener() {
        @Override
        public void onAvatarSelected(FriendRecord fr) {
            Marker marker = mMarkerTracker.getById(fr.id);
            if (marker == null) {
                showInfoPanel(fr, null);
                return;
            }

            // Is the info panel already showing for this user? If so, just center the camera and follow
            if (infoPanel.getFriendId() == fr.id) {
                friendForCameraToTrack = fr.id;
                CameraUpdate update = CameraUpdateFactory.newLatLng(marker.getPosition());
                mGoogMap.animateCamera(update);
                return;
            }

            friendForCameraToTrack = fr.id;
            findViewById(R.id.my_location_fab).setSelected(false);
            float zoom = Math.max(mGoogMap.getCameraPosition().zoom, Constants.DEFAULT_ZOOM_LEVEL);
            CameraPosition cp = new CameraPosition.Builder()
                    .target(marker.getPosition())
                    .zoom(zoom)
                    .bearing(0)
                    .tilt(0).build();
            CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
            mGoogMap.animateCamera(cu);

            FriendLocation loc = (FriendLocation) marker.getTag();
            if (loc == null) {
                throw new RuntimeException("Marker location should never be null. Should contain the friend's location");
            }
            showInfoPanel(fr, loc);
            showFriendErrorCircle(loc);
        }
    };

    //endregion

    //region GoogleMap.OnMarkerClickListener

    private final GoogleMap.OnMarkerClickListener markerClickListener = new GoogleMap.OnMarkerClickListener() {
        @Override
        @UiThread
        public boolean onMarkerClick(@NonNull final Marker marker) {
            if (marker.equals(mMeMarker)) {
                infoPanel.hide();
                radialMenu.setVisible(true);
                radialMenu.flyBack();
                return true;
            }

            final FriendLocation loc = mMarkerTracker.getLocation(marker);
            if (loc == null) {
                // should never happen
                infoPanel.hide();
                radialMenu.setVisible(true);
                radialMenu.flyBack();
                return true;
            }
            L.i("onMarkerClick found friend " + loc.friendId);
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    FriendRecord friend = DB.get().getFriendById(loc.friendId);
                    if (friend == null) {
                        return;
                    }
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            showInfoPanel(friend, loc);
                        }
                    });
                }
            });
            friendForCameraToTrack = loc.friendId;

            if (mGoogMap != null) {
                CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
                mGoogMap.animateCamera(update);
            }

            showFriendErrorCircle(loc);
            return true;
        }
    };

    //endregion

}
