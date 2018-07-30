package io.pijun.george;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.ColorRes;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
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
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.ArrayList;

import io.pijun.george.animation.DoubleEvaluator;
import io.pijun.george.animation.LatLngEvaluator;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.PackageWatcher;
import io.pijun.george.api.SearchUserResult;
import io.pijun.george.api.UserComm;
import io.pijun.george.api.locationiq.RevGeocoding;
import io.pijun.george.api.locationiq.ReverseGeocodingCache;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.databinding.ActivityMapBinding;
import io.pijun.george.event.AvatarUpdated;
import io.pijun.george.event.FriendRemoved;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRevoked;
import io.pijun.george.interpolator.LinearBezierInterpolator;
import io.pijun.george.service.FcmTokenRegistrar;
import io.pijun.george.service.LimitedShareService;
import io.pijun.george.view.AvatarView;
import io.pijun.george.view.DrawerActionRecognizer;
import io.pijun.george.view.DrawerSwipesListener;
import io.pijun.george.view.MyLocationView;
import retrofit2.Response;

public final class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, AvatarsAdapter.AvatarsAdapterListener, DrawerSwipesListener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private ActivityMapBinding binding;
    private MapView mMapView;
    private GoogleMap mGoogMap;
    private volatile PackageWatcher mPkgWatcher;
    private MarkerTracker mMarkerTracker = new MarkerTracker();
    private FusedLocationProviderClient mLocationProviderClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private Marker mMeMarker;
    private Circle mMyCircle;
    private long friendForCameraToTrack = -1;
    private long selectedAvatarFriendId = -1;
    private EditText mUsernameField;
    private Circle mCurrentCircle;
    private WeakReference<FriendsSheetFragment> mFriendsSheet;
    private boolean mInitialLayoutDone = false;
    private float mUiHiddenOffset;
    private GestureDetector mGestureDetector;
    private DrawerActionRecognizer mDrawerActionRecognizer;
    private boolean requestLocationOnStart = false;

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, MapActivity.class);
    }

    @Override
    public void onBackPressed() {
        // check if the friends sheet wants to handle the back press
        FriendsSheetFragment fragment = mFriendsSheet.get();
        if (fragment != null) {
            boolean result = fragment.onBackPressed();
            if (result) {
                return;
            }
        }

        super.onBackPressed();
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

        getWindow().getDecorView().setBackground(null);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_map);

        binding.root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mInitialLayoutDone) {
                    return;
                }
                mInitialLayoutDone = true;
                onInitialLayoutDone();
            }
        });

        mMapView = findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(this);

        final View myLocFab = findViewById(R.id.my_location_fab);
        myLocFab.setOnClickListener(v -> {
            myLocFab.setSelected(true);
            friendForCameraToTrack = 0;
            flyCameraToMyLocation();

            // if we're showing the avatar info, hide it
            selectedAvatarFriendId = -1;
            if (binding != null) {
                binding.markerDetails.setVisibility(View.GONE);
            }
        });

        mLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(3 * DateUtils.SECOND_IN_MILLIS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();

        startService(FcmTokenRegistrar.newIntent(this));

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                String username = Prefs.get(MapActivity.this).getUsername();
                binding.username.setText(username);
                binding.avatar.username = username;
                File myAvatar = AvatarManager.getMyAvatar(MapActivity.this);
                Picasso.with(MapActivity.this).load(myAvatar).into(binding.avatar);
            }
        });
    }

    public void onRequestLocation(View v) {
        L.i("onRequestLocation " + selectedAvatarFriendId + " (manual)");
        if (selectedAvatarFriendId < 1) {
            return;
        }
        long friendId = selectedAvatarFriendId;
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                FriendRecord friend = DB.get().getFriendById(friendId);
                if (friend == null) {
                    L.w("no friend found with id " + friendId);
                    return;
                }
                if (friend.receivingBoxId == null) {
                    L.w(friend.user.username + " doesn't share their location with us");
                    return;
                }
                L.i("requesting location from " + friend.user.username);
                UserComm comm = UserComm.newLocationUpdateRequest();
                UpdateStatusTracker.setLastRequestTime(friendId, System.currentTimeMillis());
                String errMsg = OscarClient.queueSendMessage(MapActivity.this, friend.user, comm, true, true);
                if (errMsg != null) {
                    L.w("manual location request failed: " + errMsg);
                }
            }
        });
    }

    @Override
    @UiThread
    protected void onStart() {
        super.onStart();

        App.isInForeground = true;
        checkForLocationPermission();
        App.registerOnBus(this);
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
                mPkgWatcher = PackageWatcher.createWatcher(MapActivity.this, token);
                if (mPkgWatcher == null) {
                    L.w("unable to create package watcher");
                    return;
                }

                ArrayList<FriendRecord> friends = DB.get().getFriends();
                for (FriendRecord fr: friends) {
                    if (fr.receivingBoxId != null) {
                        L.i("\twatching " + Hex.toHexString(fr.receivingBoxId));
                        mPkgWatcher.watch(fr.receivingBoxId);
                    }
                }

                if (requestLocationOnStart) {
                    // Request a location update from any friend that hasn't given us an update for
                    // 3 minutes
                    long now = System.currentTimeMillis();
                    UserComm comm = UserComm.newLocationUpdateRequest();
                    final float DELAY = 1;
                    for (FriendRecord fr : friends) {
                        // check if this friend shares location with us
                        if (fr.receivingBoxId == null) {
                            continue;
                        }
                        FriendLocation loc = DB.get().getFriendLocation(fr.id);
                        if (loc == null || (now - loc.time) > DELAY * DateUtils.SECOND_IN_MILLIS) {
                            UpdateStatusTracker.setLastRequestTime(fr.id, System.currentTimeMillis());
                            L.i("queue location request");
                            String errMsg = OscarClient.queueSendMessage(MapActivity.this, fr.user, comm, true, true);
                            if (errMsg != null) {
                                L.w("failed to queue location_update_request: " + errMsg);
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

        UpdateStatusTracker.removeListener(updateStatusTrackerListener);
        mMapView.onStop();

        if (mGoogMap != null) {
            CameraPosition pos = mGoogMap.getCameraPosition();
            Prefs.get(this).setCameraPosition(pos);
        }

        // hide visible info windows, so outdated info is not visible in case the activity is
        // brought back into view
        for (Marker m : mMarkerTracker.getMarkers()) {
            if (m.isInfoWindowShown()) {
                m.hideInfoWindow();
            }
        }

        // stop receiving location updates
        mLocationProviderClient.removeLocationUpdates(mLocationCallbackHelper);

        App.unregisterFromBus(this);
        App.runInBackground(() -> {
            if (mPkgWatcher != null) {
                mPkgWatcher.disconnect();
                mPkgWatcher = null;
            }
        });

        App.isInForeground = false;
    }

    @Override
    @UiThread
    protected void onDestroy() {
        mMarkerTracker.clear();

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
        CameraPosition cp = new CameraPosition.Builder()
                .target(mMeMarker.getPosition())
                .zoom(Constants.DEFAULT_ZOOM_LEVEL)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        mGoogMap.animateCamera(cu);
    }

    @Override
    @UiThread
    public void onMapReady(GoogleMap mapboxMap ) {
        if (mapboxMap == null) {
            L.i("onMapReady has a null map arg");
            return;
        }
        mGoogMap = mapboxMap;
        CameraPosition pos = Prefs.get(this).getCameraPosition();
        if (pos != null) {
            mGoogMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
        mGoogMap.setOnMarkerClickListener(this);
        mGoogMap.getUiSettings().setCompassEnabled(false);
        mGoogMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                if (reason == REASON_GESTURE) {
                    friendForCameraToTrack = -1;
                    findViewById(R.id.my_location_fab).setSelected(false);
                }
            }
        });
        mGoogMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                L.i("onMapClick");
                binding.markerDetails.setVisibility(View.GONE);
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
            Crashlytics.logException(new Exception("Location updates requested before acquiring permission"));
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
        int thirtySix = getResources().getDimensionPixelSize(R.dimen.thirtySix);
        Bitmap bmp = null;
        try {
            bmp = Picasso.with(this).load(AvatarManager.getAvatar(this, friend.user.username)).resize(thirtySix, thirtySix).get();
        } catch (IOException ignore) {}
        if (bmp == null) {
            bmp = Bitmap.createBitmap(thirtySix, thirtySix, Bitmap.Config.ARGB_8888);
            Identicon.draw(bmp, friend.user.username);
        }

        final Bitmap img = bmp;
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                // check if it's already there
                if (mMarkerTracker.getById(friend.id) != null) {
                    // don't add another one
                    return;
                }

                AvatarView avView = new AvatarView(MapActivity.this);
                avView.setBorderColor(Color.WHITE);
                int spec = View.MeasureSpec.makeMeasureSpec(thirtySix, View.MeasureSpec.AT_MOST);
                avView.measure(spec, spec);
                avView.setImage(img);
                Bitmap avatar = Bitmap.createBitmap(thirtySix, thirtySix, Bitmap.Config.ARGB_8888);
                avView.layout(0, 0, thirtySix, thirtySix);
                Canvas c = new Canvas(avatar);
                avView.draw(c);

                BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(avatar);
                MarkerOptions opts = new MarkerOptions()
                        .position(new LatLng(loc.latitude, loc.longitude))
                        .icon(descriptor)
                        .anchor(0.5f, 0.5f)
                        .title(friend.user.username);
                Marker marker = mGoogMap.addMarker(opts);
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
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
            builder.setTitle("Permission request");
            builder.setMessage("Pijun uses your location to show your position on the map, and to securely share it with friends that you've authorized. It's never used for any other purpose.");
            builder.setPositiveButton(R.string.ok, (dialog, which) -> ActivityCompat.requestPermissions(
                    MapActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION));
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

    @Subscribe
    @Keep
    @UiThread
    public void onAvatarUpdated(AvatarUpdated evt) {
        if (binding == null) {
            // UI isn't loaded, so get outta here
            return;
        }
        // we're only interested in updates to the user profile
        if (evt.username != null) {
            return;
        }
        // Update it
        File myAvatar = AvatarManager.getMyAvatar(this);
        Picasso.with(this).load(myAvatar).into(binding.avatar);
    }

    @UiThread
    public void onCloseDrawerAction(View v) {
        onFlingCloseDrawer();
        mDrawerActionRecognizer.setClosed(true);
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationSharingGranted(final LocationSharingGranted grant) {
        L.i("onLocationSharingGranted");
        if (mPkgWatcher == null) {
            return;
        }

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                final FriendRecord friend = DB.get().getFriendByUserId(grant.userId);
                if (friend == null) {
                    L.w("MapActivity.onLocationSharingGranted didn't find friend record");
                    return;
                }
                // Perform the null check because the location sharing grant may have been revoked.
                // (If the other person is flipping the sharing switch back and forth).
                if (friend.receivingBoxId != null) {
                    L.i("onLocationSharingGranted: friend found. will watch");
                    mPkgWatcher.watch(friend.receivingBoxId);
                }
            }
        });
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationSharingRevoked(final LocationSharingRevoked revoked) {
        // remove the map marker, if there is one
        App.runInBackground(() -> {
            DB db = DB.get();
            FriendRecord friend = db.getFriendByUserId(revoked.userId);
            if (friend == null) {
                return;
            }
            App.runOnUiThread(() -> {
                Marker marker = mMarkerTracker.removeMarker(friend.id);
                if (marker == null) {
                    return;
                }
                marker.remove();
            });
        });
    }

    @Subscribe
    @Keep
    @UiThread
    public void onFriendLocationUpdated(final FriendLocation loc) {
        if (selectedAvatarFriendId == loc.friendId) {
            setAvatarInfo(loc);
        }
        // check if we already have a marker for this friend
        Marker marker = mMarkerTracker.getById(loc.friendId);
        if (marker == null) {
            App.runInBackground(() -> {
                final FriendRecord friend = DB.get().getFriendById(loc.friendId);
                if (friend != null) {
                    addMapMarker(friend, loc);
                }
            });
        } else {
            if (marker.isInfoWindowShown()) {
                marker.hideInfoWindow();
            }
            marker.setPosition(new LatLng(loc.latitude, loc.longitude));
            mMarkerTracker.updateLocation(loc.friendId, loc);

            // if there is an error circle being shown on this marker, adjust it too
            if (mCurrentCircle != null) {
                long friendId = (long) mCurrentCircle.getTag();
                if (loc.friendId == friendId) {
                    if (loc.accuracy != null) {
                        mCurrentCircle.setRadius(loc.accuracy);
                        mCurrentCircle.setCenter(new LatLng(loc.latitude, loc.longitude));
                    } else {
                        // no accuracy, so remove the circle
                        mCurrentCircle.remove();
                    }
                }
            }
        }

        if (friendForCameraToTrack == loc.friendId && mGoogMap != null) {
            CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
            mGoogMap.animateCamera(update);
        }
    }

    @UiThread
    public void onLogOutAction(View v) {
        L.i("onLogOutAction");
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setMessage(R.string.confirm_log_out_msg);
        builder.setCancelable(true);
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.log_out, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AuthenticationManager.get().logOut(MapActivity.this, new AuthenticationManager.LogoutWatcher() {
                    @Override
                    public void onUserLoggedOut() {
                        Intent welcomeIntent = WelcomeActivity.newIntent(MapActivity.this);
                        startActivity(welcomeIntent);
                        finish();
                    }
                });
            }
        });
        builder.show();

        onFlingCloseDrawer();
        mDrawerActionRecognizer.setClosed(true);
    }

    @Override
    @UiThread
    public boolean onMarkerClick(@NonNull final Marker marker) {
        if (marker.equals(mMeMarker)) {
            binding.markerDetails.setVisibility(View.GONE);
            return true;
        }

        final FriendLocation loc = mMarkerTracker.getLocation(marker);
        if (loc == null) {
            binding.markerDetails.setVisibility(View.GONE);
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
                        if (binding != null) {
                            binding.markerUsername.setText(friend.user.username);
                        }
                    }
                });
            }
        });
        selectedAvatarFriendId = loc.friendId;
        friendForCameraToTrack = loc.friendId;
        setAvatarInfo(loc);

        binding.markerDetails.setVisibility(View.VISIBLE);

        if (mGoogMap != null) {
            CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
            mGoogMap.animateCamera(update);
        }

        showFriendErrorCircle(loc);
        return true;
    }

    @Override
    @UiThread
    public void onAvatarSelected(FriendRecord fr) {
        Marker marker = mMarkerTracker.getById(fr.id);
        if (marker == null) {
            binding.markerDetails.setVisibility(View.GONE);
            return;
        }

        binding.markerDetails.setVisibility(View.VISIBLE);
        binding.markerUsername.setText(fr.user.username);

        // Is the avatar info already showing for this user? If so, just center the camera and follow
        if (selectedAvatarFriendId == fr.id) {
            friendForCameraToTrack = fr.id;
            CameraUpdate update = CameraUpdateFactory.newLatLng(marker.getPosition());
            mGoogMap.animateCamera(update);
            return;
        }

        friendForCameraToTrack = fr.id;
        selectedAvatarFriendId = fr.id;
        findViewById(R.id.my_location_fab).setSelected(false);
        CameraPosition cp = new CameraPosition.Builder()
                .target(marker.getPosition())
                .zoom(Constants.DEFAULT_ZOOM_LEVEL)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        mGoogMap.animateCamera(cu);

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                FriendLocation loc = DB.get().getFriendLocation(fr.id);
                if (loc == null) {
                    // shouldn't happen
                    return;
                }

                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        setAvatarInfo(loc);
                        showFriendErrorCircle(loc);
                    }
                });
            }
        });
    }

    @UiThread
    void setFriendsSheetFragment(@Nullable FriendsSheetFragment fragment) {
        if (fragment == null) {
            mFriendsSheet = null;
        } else {
            mFriendsSheet = new WeakReference<>(fragment);
        }
    }

    public void onAddFriendAction(View v) {
        L.i("onAddFriendAction");
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setPositiveButton("Add friend", (dialog, which) -> showAddFriendDialog())
                .setNeutralButton("Limited Share", (dialog, which) -> {
                    Intent i = LimitedShareService.newIntent(this, LimitedShareService.ACTION_START);
                    ContextCompat.startForegroundService(this, i);
                })
                .setNegativeButton("Cancel", null)
                .setCancelable(true).setMessage("Add friend?").show();
    }

    @UiThread
    private void showAddFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(R.string.add_friend);
        builder.setView(R.layout.friend_request_form);
        builder.setPositiveButton(R.string.send, (dialog, which) -> {
            final String username = mUsernameField.getText().toString();
            App.runInBackground(() -> attemptLocationGrant(username));
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.show();

        mUsernameField = dialog.findViewById(R.id.username);
    }

    @UiThread
    private void setAvatarInfo(@NonNull FriendLocation loc) {
        StringBuilder speed = new StringBuilder();
        if (loc.speed != null) {
            speed.append(loc.speed).append(" m/s");
        }
        binding.markerSpeed.setText(speed);

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
        binding.markerTime.setText(String.format("(%s)", relTime));

        binding.markerDetails.setTag(loc);

        updateStatusTrackerListener.onUpdateStatusChanged(loc.friendId);

        if (loc.bearing != null) {
            binding.markerDirection.setVisibility(View.VISIBLE);
            binding.markerDirection.setRotation(loc.bearing);
        } else {
            binding.markerDirection.setVisibility(View.GONE);
        }
        String movement;
        switch (loc.movement) {
            case Bicycle:
                movement = getString(R.string.biking);
                break;
            case OnFoot:
                movement = getString(R.string.on_foot);
                break;
            case Running:
                movement = getString(R.string.running);
                break;
            case Vehicle:
                movement = getString(R.string.in_the_car);
                break;
            case Walking:
                movement = getString(R.string.walking);
                break;
            case Stationary:
                movement = "stationary";
                break;
            case Tilting:
                movement = "tilting";
                break;
            case Unknown:
            default:
                movement = null;
                break;
        }
        binding.markerActivity.setText(movement);
        RevGeocoding rg = ReverseGeocodingCache.get(loc.latitude, loc.longitude);
        if (rg != null) {
            StringBuilder s = new StringBuilder(rg.getAddress());
            if (loc.accuracy != null) {
                s.append(" (±").append(loc.accuracy.intValue()).append(" m)");
            }
            binding.markerLocation.setText(s);
        } else {
            binding.markerLocation.setText(R.string.loading_ellipsis);
            ReverseGeocodingCache.fetch(MapActivity.this, loc.latitude, loc.longitude, new ReverseGeocodingCache.OnCachedListener() {
                @Override
                public void onReverseGeocodingCached(@Nullable RevGeocoding rg) {
                    FriendLocation savedLoc = (FriendLocation) binding.markerDetails.getTag();
                    if (savedLoc != null && savedLoc.latitude == loc.latitude && savedLoc.longitude == loc.longitude) {
                        if (rg != null) {
                            StringBuilder s = new StringBuilder(rg.getAddress());
                            if (loc.accuracy != null) {
                                s.append(" (±").append(loc.accuracy.intValue()).append(" m)");
                            }
                            binding.markerLocation.setText(s);
                        }
                    }
                }
            });
        }
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
            long friendId = (long)mCurrentCircle.getTag();
            if (friendId != loc.friendId) {
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
    public void showProfile(View v) {
        Intent i = ProfileActivity.newIntent(this);
        startActivity(i);

        onFlingCloseDrawer();
        mDrawerActionRecognizer.setClosed(true);
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
            DB db = DB.get();
            UserRecord userRecord = db.getUser(username);
            if (userRecord == null) {
                Response<SearchUserResult> searchResponse = api.searchForUser(username).execute();
                if (!searchResponse.isSuccessful()) {
                    OscarError err = OscarError.fromResponse(searchResponse);
                    Utils.showStringAlert(this, null, "Unable to find username: " + err);
                    return;
                }
                SearchUserResult userToRequest = searchResponse.body();
                if (userToRequest == null) {
                    Utils.showStringAlert(this, null, "Unknown error while retrieving info about username");
                    return;
                }
                userRecord = db.addUser(userToRequest.id, userToRequest.username, userToRequest.publicKey, true, this);
            }

            // check if we already have this user as a friend, and if we're already sharing with them
            final FriendRecord friend = db.getFriendByUserId(userRecord.id);
            if (friend != null) {
                if (friend.sendingBoxId != null) {
                    // send the sending box id to this person one more time, just in case
                    UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                    String errMsg = OscarClient.queueSendMessage(this, userRecord, comm, false, false);
                    if (errMsg != null) {
                        Crashlytics.logException(new RuntimeException(errMsg));
                    }
                    Utils.showStringAlert(this, null, "You're already sharing your location with " + username);
                    return;
                }
            }

            byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
            new SecureRandom().nextBytes(sendingBoxId);
            UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
            String errMsg = OscarClient.queueSendMessage(this, userRecord, comm, false, false);
            if (errMsg != null) {
                Utils.showStringAlert(this, null, "Unable to create sharing grant: " + errMsg);
                return;
            }

            db.startSharingWith(userRecord, sendingBoxId, this);
            try { AvatarManager.sendAvatarToUser(this, userRecord); }
            catch (IOException ex) {
                Crashlytics.logException(ex);
            }
            Utils.showStringAlert(this, null, "You're now sharing with " + username);
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Network problem trying to share your location. Check your connection then try again.");
        } catch (DB.DBException dbe) {
            Utils.showStringAlert(this, null, "Error adding friend into database");
            Crashlytics.logException(dbe);
        }
    }

    @Subscribe
    @Keep
    public void onFriendRemoved(FriendRemoved evt) {
        Marker marker = mMarkerTracker.removeMarker(evt.friendId);
        if (marker != null) {
            marker.remove();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @UiThread
    private void onInitialLayoutDone() {
        // set pivots
        binding.map.setPivotY(binding.map.getHeight()/2);

        // translate the drawer components out of view
        mUiHiddenOffset = -(binding.location.getX() + Utils.dpsToPix(this, 100));
        binding.avatar.setTranslationX(mUiHiddenOffset);
        binding.username.setTranslationX(mUiHiddenOffset);
        binding.location.setTranslationX(mUiHiddenOffset);
        binding.settings.setTranslationX(mUiHiddenOffset);
        binding.about.setTranslationX(mUiHiddenOffset);
        binding.logOut.setTranslationX(mUiHiddenOffset);

        // install the gesture listener
        mDrawerActionRecognizer = new DrawerActionRecognizer(binding.root.getWidth(), this);
        mGestureDetector = new GestureDetector(this, mDrawerActionRecognizer);
        binding.touchInterceptor.setOnTouchListener((View v, MotionEvent event) -> {
            boolean onUp = event.getAction() == MotionEvent.ACTION_UP;
            boolean detectorConsumed = mGestureDetector.onTouchEvent(event);
            if (!detectorConsumed && onUp && mDrawerActionRecognizer.isGesturing()) {
                mDrawerActionRecognizer.onUp();
                return true;
            }
            return detectorConsumed;
        });
    }

    @Override
    public void onCloseDrawer(float pixels, float delta) {
        float range = binding.root.getWidth() * 0.75f;
        float xOffset = range - pixels;
        xOffset = Math.max(xOffset, 0);
        binding.map.setTranslationX(xOffset);

        float progress = xOffset / range;
        float scale = 1 - 0.25f * progress;
        binding.map.setScaleX(scale);
        binding.map.setScaleY(scale);

        float uiOffset = Math.max(mUiHiddenOffset, -pixels);
        uiOffset = Math.min(0, uiOffset);
        binding.avatar.setTranslationX(uiOffset);
        binding.username.setTranslationX(uiOffset);
        binding.location.setTranslationX(uiOffset);
        binding.settings.setTranslationX(uiOffset);
        binding.about.setTranslationX(uiOffset);
        binding.logOut.setTranslationX(uiOffset);

        FriendsSheetFragment fragment = mFriendsSheet.get();
        if (fragment.getView() != null) {
            ConstraintLayout sheet = (ConstraintLayout) fragment.getView();
            float sheetOffset = sheet.getTranslationY() - delta;
            int baseOffset = (int) sheet.getTag();
            sheetOffset = Math.max(sheetOffset, (float) baseOffset);
            sheet.setTranslationY(sheetOffset);
        }

        float friendFABOffset = binding.addFriendFab.getTranslationY() - delta;
        friendFABOffset = Math.max(0, friendFABOffset);
        binding.addFriendFab.setTranslationY(friendFABOffset);

        float locFABOffset = binding.myLocationFab.getTranslationY() + delta;
        locFABOffset = Math.min(0, locFABOffset);
        binding.myLocationFab.setTranslationY(locFABOffset);
    }

    @Override
    public void onOpenDrawer(float pixels) {
        float range = binding.root.getWidth() * 0.75f;
        float xOffset = Math.max(pixels, 0);
        xOffset = Math.min(xOffset, range);
        binding.map.setTranslationX(xOffset);

        float progress = xOffset / range;
        // the smallest we shrink is 75%
        float scale = 1 - 0.25f * progress;
        binding.map.setScaleX(scale);
        binding.map.setScaleY(scale);

        // animate away friends sheet
        FriendsSheetFragment fragment = mFriendsSheet.get();
        if (fragment != null && fragment.getView() != null) {
            ConstraintLayout sheet = (ConstraintLayout) fragment.getView();
            int transY = (int) sheet.getTag();
            float friendsOffset = transY + Math.min(range, pixels);
            sheet.setTranslationY(friendsOffset);
        }

        // the Add Friends fab
        float friendFABOffset = Math.max(0, Math.min(range, pixels));
        binding.addFriendFab.setTranslationY(friendFABOffset);

        // and the 'my location' fab
        float myLocFABOffset = Math.max(0, Math.min(range, pixels));
        myLocFABOffset *= -1;
        binding.myLocationFab.setTranslationY(myLocFABOffset);

        // animate the drawer elements too
        float uiOffset = Math.min(0, mUiHiddenOffset + pixels);
        binding.avatar.setTranslationX(uiOffset);
        binding.username.setTranslationX(uiOffset);
        binding.location.setTranslationX(uiOffset);
        binding.settings.setTranslationX(uiOffset);
        binding.about.setTranslationX(uiOffset);
        binding.logOut.setTranslationX(uiOffset);
    }

    @Override
    public void onFlingCloseDrawer() {
        binding.map.animate().setInterpolator(new LinearBezierInterpolator()).x(0);
        binding.map.animate().setInterpolator(new LinearBezierInterpolator()).scaleX(1).scaleY(1);

        binding.avatar.animate().setInterpolator(new LinearBezierInterpolator()).translationX(mUiHiddenOffset).setDuration(200);
        binding.username.animate().setInterpolator(new LinearBezierInterpolator()).translationX(mUiHiddenOffset).setDuration(200);
        binding.location.animate().setInterpolator(new LinearBezierInterpolator()).translationX(mUiHiddenOffset).setDuration(200);
        binding.settings.animate().setInterpolator(new LinearBezierInterpolator()).translationX(mUiHiddenOffset).setDuration(200);
        binding.about.animate().setInterpolator(new LinearBezierInterpolator()).translationX(mUiHiddenOffset).setDuration(200);
        binding.logOut.animate().setInterpolator(new LinearBezierInterpolator()).translationX(mUiHiddenOffset).setDuration(200);

        FriendsSheetFragment fragment = mFriendsSheet.get();
        if (fragment.getView() != null) {
            ConstraintLayout sheet = (ConstraintLayout) fragment.getView();
            float transY = (int)sheet.getTag(); // int cast for unboxing
            sheet.animate().setInterpolator(new LinearBezierInterpolator()).translationY(transY).setDuration(200);
        }

        binding.myLocationFab.animate().setInterpolator(new LinearBezierInterpolator()).translationY(0).setDuration(200);
        binding.addFriendFab.animate().setInterpolator(new LinearBezierInterpolator()).translationY(0).setDuration(200);
    }

    @Override
    public void onFlingOpenDrawer() {
        binding.map.setPivotY(binding.root.getHeight()/2);

        float range = binding.root.getWidth() * 0.75f;
        binding.map.animate().setInterpolator(new LinearBezierInterpolator()).x(range).setDuration(200);

        // the smallest we shrink is 75%
        float scale = 1 - 0.25f;
        binding.map.animate()
                .setInterpolator(new LinearBezierInterpolator())
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(200);

        // animate the drawer elements too
        binding.avatar.animate().setInterpolator(new LinearBezierInterpolator()).translationX(0).setDuration(200);
        binding.username.animate().setInterpolator(new LinearBezierInterpolator()).translationX(0).setDuration(200);
        binding.location.animate().setInterpolator(new LinearBezierInterpolator()).translationX(0).setDuration(200);
        binding.settings.animate().setInterpolator(new LinearBezierInterpolator()).translationX(0).setDuration(200);
        binding.about.animate().setInterpolator(new LinearBezierInterpolator()).translationX(0).setDuration(200);
        binding.logOut.animate().setInterpolator(new LinearBezierInterpolator()).translationX(0).setDuration(200);

        // friends sheet
        FriendsSheetFragment fragment = mFriendsSheet.get();
        if (fragment.getView() != null) {
            ConstraintLayout sheet = (ConstraintLayout) fragment.getView();
            float transY = (int)sheet.getTag() + range;   // the int cast is for unboxing the tag
            sheet.animate().translationY(transY).setInterpolator(new LinearBezierInterpolator()).setDuration(200);
        }

        // the two fabs
        binding.addFriendFab.animate().translationY(range).setInterpolator(new LinearBezierInterpolator()).setDuration(200);
        binding.myLocationFab.animate().translationY(-range).setInterpolator(new LinearBezierInterpolator()).setDuration(200);
    }

    @Override
    public boolean onSettleDrawer() {
        float transX = binding.map.getTranslationX();
        float range = binding.root.getWidth() * 0.75f;
        float progress = transX / range;
        if (progress >= 0.5) {
            onFlingOpenDrawer();
            return true;
        } else {
            onFlingCloseDrawer();
            return false;
        }
    }

    public void onShowLogs(View v) {
        Intent i = LogActivity.newIntent(this);
        startActivity(i);
    }

    private UpdateStatusTracker.Listener updateStatusTrackerListener = new UpdateStatusTracker.Listener() {
        @Override
        public void onUpdateStatusChanged(long friendId) {
            L.i("updatestatuschanged - friendId: " + friendId + ", trackingId: " + selectedAvatarFriendId);
            if (friendId != selectedAvatarFriendId || binding == null) {
                return;
            }
            UpdateStatusTracker.State status = UpdateStatusTracker.getFriendState(friendId);
            L.i("updatestatuschanged " + friendId + ", state: " + status);
            int vis;
            @ColorRes int colorId = 0;
            switch (status) {
                case NotRequested:
                    vis = View.GONE;
                    break;
                case Requested:
                    vis = View.VISIBLE;
                    colorId = R.color.colorPrimary;
                    break;
                case RequestedAndUnresponsive:
                    vis = View.VISIBLE;
                    colorId = R.color.common_gray;
                    break;
                case RequestDenied:
                    vis = View.VISIBLE;
                    colorId = R.color.red;
                    break;
                case RequestAcknowledged:
                    vis = View.VISIBLE;
                    colorId = R.color.colorAccent;
                    break;
                case RequestFulfilled:
                    vis = View.GONE;
                    break;
                default:
                    vis = View.GONE;
                    break;
            }
            if (colorId != 0) {
                DrawableCompat.setTint(binding.markerProgressBar.getIndeterminateDrawable(),
                        ContextCompat.getColor(MapActivity.this, colorId));
            }
            binding.markerProgressBar.setVisibility(vis);
        }
    };

    private LocationCallback mLocationCallbackHelper = new LocationCallback() {
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
            LocationUtils.upload(MapActivity.this, location, false);
        }
    };
}
