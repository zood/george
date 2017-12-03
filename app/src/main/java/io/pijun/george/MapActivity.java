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
import android.graphics.Outline;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.Toast;

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
import com.google.firebase.crash.FirebaseCrash;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
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
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.databinding.ActivityMapBinding;
import io.pijun.george.event.AvatarUpdated;
import io.pijun.george.event.FriendRemoved;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRevoked;
import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.MovementType;
import io.pijun.george.models.UserRecord;
import io.pijun.george.service.FcmTokenRegistrar;
import io.pijun.george.service.LimitedShareService;
import io.pijun.george.view.AvatarView;
import io.pijun.george.view.MyLocationView;
import retrofit2.Call;
import retrofit2.Response;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, AvatarsAdapter.AvatarsAdapterListener, Utils.DrawerSwipesListener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private ActivityMapBinding mBinding;
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
    private long mFriendForCameraToTrack = -1;
    private EditText mUsernameField;
    private Circle mCurrentCircle;
    private WeakReference<FriendsSheetFragment> mFriendsSheet;
    private boolean mInitialLayoutDone = false;
    private float mUiHiddenOffset;
    private GestureDetector mGestureDetector;
    private Utils.DrawerActionRecognizer mDrawerActionRecognizer;

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
        if (!Prefs.get(this).isLoggedIn()) {
            Intent welcomeIntent = WelcomeActivity.newIntent(this);
            startActivity(welcomeIntent);
            finish();
            return;
        }

        getWindow().getDecorView().setBackground(null);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_map);

        mBinding.root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
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

        mBinding.coordinator.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
            }
        });

        final View myLocFab = findViewById(R.id.my_location_fab);
        myLocFab.setOnClickListener(v -> {
            myLocFab.setSelected(true);
            mFriendForCameraToTrack = 0;
            flyCameraToMyLocation();
        });

        mLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();

        startService(FcmTokenRegistrar.newIntent(this));
//        startService(ActivityMonitor.newIntent(this));

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                String username = Prefs.get(MapActivity.this).getUsername();
                mBinding.username.setText(username);
                mBinding.avatar.username = username;
                File myAvatar = AvatarManager.getMyAvatar(MapActivity.this);
                Picasso.with(MapActivity.this).load(myAvatar).into(mBinding.avatar);
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
                        L.i("\twatching " + Hex.toHexString(fr.receivingBoxId));
                        mPkgWatcher.watch(fr.receivingBoxId);
                    }
                }

                // Request a location update from any friend that hasn't given us an update for
                // 3 minutes
                long now = System.currentTimeMillis();
                UserComm comm = UserComm.newLocationUpdateRequest();
                for (FriendRecord fr : friends) {
                    // check if this friend shares location with us
                    if (fr.receivingBoxId == null) {
                        continue;
                    }
                    FriendLocation loc = DB.get(MapActivity.this).getFriendLocation(fr.id);
                    if (loc == null || (now-loc.time) > 180 * DateUtils.SECOND_IN_MILLIS) {
                        String errMsg = OscarClient.queueSendMessage(MapActivity.this, fr.user, comm, true, true);
                        if (errMsg != null) {
                            L.w("failed to queue location_update_request: " + errMsg);
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
                        MessageProcessor.get().queue(msg);
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

        mMapView.onStop();

        if (mGoogMap != null) {
            CameraPosition pos = mGoogMap.getCameraPosition();
            L.i("onStop cam pos: " + pos);
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
            FirebaseCrash.log("MapboxMap is null");
            return;
        }
        mGoogMap = mapboxMap;
        CameraPosition pos = Prefs.get(this).getCameraPosition();
        if (pos != null) {
            L.i("found a saved camera position");
            mGoogMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        } else {
            L.i("camera position is null in restore");
        }
        mGoogMap.setOnMarkerClickListener(this);
        mGoogMap.getUiSettings().setCompassEnabled(false);
        mGoogMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                if (reason == REASON_GESTURE) {
                    mFriendForCameraToTrack = -1;
                    findViewById(R.id.my_location_fab).setSelected(false);
                }
            }
        });

        // add markers for all friends
        App.runInBackground(() -> {
            DB db = DB.get(MapActivity.this);
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
            FirebaseCrash.report(new Exception("Location updates requested before acquiring permission"));
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
        if (mBinding == null) {
            // UI isn't loaded, so get outta here
            return;
        }
        // we're only interested in updates to the user profile
        if (evt.username != null) {
            return;
        }
        // Update it
        File myAvatar = AvatarManager.getMyAvatar(this);
        Picasso.with(this).load(myAvatar).into(mBinding.avatar);
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
                final FriendRecord friend = DB.get(MapActivity.this).getFriendByUserId(grant.userId);
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
            DB db = DB.get(this);
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
        // check if we already have a marker for this friend
        Marker marker = mMarkerTracker.getById(loc.friendId);
        if (marker == null) {
            App.runInBackground(() -> {
                final FriendRecord friend = DB.get(MapActivity.this).getFriendById(loc.friendId);
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

        if (mFriendForCameraToTrack == loc.friendId && mGoogMap != null) {
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
                Utils.logOut(MapActivity.this, new UiRunnable() {
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

        onFlingCloseDrawer();
        mDrawerActionRecognizer.setClosed(true);
    }

    @Override
    @UiThread
    public boolean onMarkerClick(@NonNull final Marker marker) {
        if (marker.equals(mMeMarker)) {
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

        showFriendErrorCircle(loc);

        App.runInBackground(() -> {
            try {
                Call<RevGeocoding> call = LocationIQClient.get(MapActivity.this).getReverseGeocoding("" + loc.latitude, "" + loc.longitude);
                Response<RevGeocoding> response = call.execute();
                if (response.isSuccessful()) {
                    final RevGeocoding revGeocoding = response.body();
                    if (revGeocoding == null) {
                        return;
                    }
                    App.runOnUiThread(() -> marker.setSnippet(snippet + revGeocoding.getArea()));
                } else {
                    L.w("error calling locationiq");
                }
            } catch (Exception ex) {
                L.w("network error obtaining reverse geocoding", ex);
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

        mFriendForCameraToTrack = fr.id;
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
                FriendLocation loc = DB.get(MapActivity.this).getFriendLocation(fr.id);
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
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
    private void showFriendErrorCircle(FriendLocation loc) {
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
                if (userToRequest == null) {
                    Utils.showStringAlert(this, null, "Unknown error while retrieving info about username");
                    return;
                }
                userRecord = db.addUser(userToRequest.id, userToRequest.username, userToRequest.publicKey);
            }

            // check if we already have this user as a friend, and if we're already sharing with them
            final FriendRecord friend = db.getFriendByUserId(userRecord.id);
            if (friend != null) {
                if (friend.sendingBoxId != null) {
                    // send the sending box id to this person one more time, just in case
                    UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                    String errMsg = OscarClient.queueSendMessage(this, userRecord, comm, false, false);
                    if (errMsg != null) {
                        FirebaseCrash.report(new RuntimeException(errMsg));
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

            db.startSharingWith(userRecord, sendingBoxId);
            try { AvatarManager.sendAvatarToUser(this, userRecord); }
            catch (IOException ex) {
                FirebaseCrash.report(ex);
            }
            Utils.showStringAlert(this, null, "You're now sharing with " + username);
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Network problem trying to share your location. Check your connection then try again.");
        } catch (DB.DBException dbe) {
            Utils.showStringAlert(this, null, "Error adding friend into database");
            FirebaseCrash.report(dbe);
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
        // translate the drawer components out of view
        mUiHiddenOffset = -(mBinding.location.getX() + Utils.dpsToPix(this, 100));
        mBinding.avatar.setTranslationX(mUiHiddenOffset);
        mBinding.username.setTranslationX(mUiHiddenOffset);
        mBinding.location.setTranslationX(mUiHiddenOffset);
        mBinding.settings.setTranslationX(mUiHiddenOffset);
        mBinding.about.setTranslationX(mUiHiddenOffset);
        mBinding.logOut.setTranslationX(mUiHiddenOffset);

        // install the gesture listener
        // move the interceptor to the front
        mDrawerActionRecognizer = new Utils.DrawerActionRecognizer(mBinding.root.getWidth(), this);
        mGestureDetector = new GestureDetector(this, mDrawerActionRecognizer);
        mBinding.touchInterceptor.setOnTouchListener((View v, MotionEvent event) -> {
            boolean onUp = event.getAction() == MotionEvent.ACTION_UP;
            boolean detectorConsumed = mGestureDetector.onTouchEvent(event);
            if (!detectorConsumed && onUp && mDrawerActionRecognizer.isGesturing()) {
                L.i("touchEvent.onUp");
                mDrawerActionRecognizer.onUp();
                return true;
            }
            return detectorConsumed;
        });
    }

    @Override
    public void onCloseDrawer(float pixels) {
        onOpenDrawer(pixels);
        float range = mBinding.root.getWidth() * 0.75f;
        float xOffset = range - pixels;
        xOffset = Math.max(xOffset, 0);
        mBinding.coordinator.setTranslationX(xOffset);

        float progress = xOffset / range;
        float scale = 1 - 0.25f * progress;
        mBinding.coordinator.setScaleX(scale);
        mBinding.coordinator.setScaleY(scale);

        float uiOffset = Math.max(mUiHiddenOffset, -pixels);
        uiOffset = Math.min(0, uiOffset);
        mBinding.avatar.setTranslationX(uiOffset);
        mBinding.username.setTranslationX(uiOffset);
        mBinding.location.setTranslationX(uiOffset);
        mBinding.settings.setTranslationX(uiOffset);
        mBinding.about.setTranslationX(uiOffset);
        mBinding.logOut.setTranslationX(uiOffset);
    }

    @Override
    public void onOpenDrawer(float pixels) {
        mBinding.coordinator.setPivotY(mBinding.root.getHeight()/2);

        float range = mBinding.root.getWidth() * 0.75f;
        float xOffset = Math.max(pixels, 0);
        xOffset = Math.min(xOffset, range);
        mBinding.coordinator.setTranslationX(xOffset);

        float progress = xOffset / range;
        // the smallest we shrink is 75%
        float scale = 1 - 0.25f * progress;
        mBinding.coordinator.setScaleX(scale);
        mBinding.coordinator.setScaleY(scale);

        // animate the drawer elements too
        float uiOffset = Math.min(0, mUiHiddenOffset + pixels);
        mBinding.avatar.setTranslationX(uiOffset);
        mBinding.username.setTranslationX(uiOffset);
        mBinding.location.setTranslationX(uiOffset);
        mBinding.settings.setTranslationX(uiOffset);
        mBinding.about.setTranslationX(uiOffset);
        mBinding.logOut.setTranslationX(uiOffset);
    }

    @Override
    public void onFlingCloseDrawer() {
        mBinding.coordinator.animate().x(0);
        mBinding.coordinator.animate().scaleX(1).scaleY(1);

        mBinding.avatar.animate().translationX(mUiHiddenOffset).setDuration(200);
        mBinding.username.animate().translationX(mUiHiddenOffset).setDuration(200);
        mBinding.location.animate().translationX(mUiHiddenOffset).setDuration(200);
        mBinding.settings.animate().translationX(mUiHiddenOffset).setDuration(200);
        mBinding.about.animate().translationX(mUiHiddenOffset).setDuration(200);
        mBinding.logOut.animate().translationX(mUiHiddenOffset).setDuration(200);
    }

    @Override
    public void onFlingOpenDrawer() {
        mBinding.coordinator.setPivotY(mBinding.root.getHeight()/2);

        float range = mBinding.root.getWidth() * 0.75f;
        mBinding.coordinator.animate().x(range).setDuration(200);

        // the smallest we shrink is 75%
        float scale = 1 - 0.25f;
        mBinding.coordinator.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(200);

        // animate the drawer elements too
        mBinding.avatar.animate().translationX(0).setDuration(200);
        mBinding.username.animate().translationX(0).setDuration(200);
        mBinding.location.animate().translationX(0).setDuration(200);
        mBinding.settings.animate().translationX(0).setDuration(200);
        mBinding.about.animate().translationX(0).setDuration(200);
        mBinding.logOut.animate().translationX(0).setDuration(200);
    }

    @Override
    public boolean onSettleDrawer() {
        float transX = mBinding.coordinator.getTranslationX();
        float range = mBinding.root.getWidth() * 0.75f;
        float progress = transX / range;
        if (progress >= 0.5) {
            onFlingOpenDrawer();
            return true;
        } else {
            onFlingCloseDrawer();
            return false;
        }
    }

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
                        new Utils.LatLngEvaluator(),
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
                            new Utils.LatLngEvaluator(),
                            mMyCircle.getCenter(),
                            new LatLng(location.getLatitude(), location.getLongitude()));
                    posAnimator.setDuration(500);
                    ValueAnimator errAnimator = ObjectAnimator.ofObject(
                            mMyCircle,
                            "radius",
                            new Utils.DoubleEvaluator(),
                            mMyCircle.getRadius(),
                            (double)location.getAccuracy());
                    errAnimator.setDuration(500);

                    posAnimator.start();
                    errAnimator.start();
                }
            }

            if (mFriendForCameraToTrack == 0) {
                if (mGoogMap != null) {
                    CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
                    mGoogMap.animateCamera(update);
                }
            }
            App.postOnBus(location);
        }
    };
}
