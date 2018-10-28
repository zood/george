package io.pijun.george;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupMenu;
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
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import io.pijun.george.animation.DoubleEvaluator;
import io.pijun.george.animation.LatLngEvaluator;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.OscarSocket;
import io.pijun.george.api.PushNotification;
import io.pijun.george.api.SearchUserResult;
import io.pijun.george.api.UserComm;
import io.pijun.george.api.locationiq.RevGeocoding;
import io.pijun.george.api.locationiq.ReverseGeocodingCache;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.databinding.ActivityMapBinding;
import io.pijun.george.service.BackupDatabaseJob;
import io.pijun.george.service.LimitedShareService;
import io.pijun.george.view.AvatarView;
import io.pijun.george.view.MyLocationView;
import retrofit2.Response;

public final class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, AvatarsAdapter.Listener, DB.Listener, AuthenticationManager.Listener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private ActivityMapBinding binding;
    private MapView mMapView;
    private GoogleMap mGoogMap;
    private OscarSocket oscarSocket;
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
    private boolean isStarted = false;
    private AvatarsAdapter avatarsAdapter;

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

        binding = DataBindingUtil.setContentView(this, R.layout.activity_map);

        mMapView = findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(this);

        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.avatars.setLayoutManager(llm);
        avatarsAdapter = new AvatarsAdapter(this);
        binding.avatars.setAdapter(avatarsAdapter);

        binding.infoPanel.setClipToOutline(true);
        binding.infoPanel.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8 * getResources().getDisplayMetrics().density);
            }
        });
        binding.battery.setCompoundDrawablePadding(20);

        final View myLocFab = findViewById(R.id.my_location_fab);
        myLocFab.setOnClickListener(v -> {
            myLocFab.setSelected(true);
            friendForCameraToTrack = 0;
            flyCameraToMyLocation();

            // if we're showing the avatar info, hide it
            selectedAvatarFriendId = -1;
            hideInfoPanel();
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

        // hide visible info windows, so outdated info is not visible in case the activity is
        // brought back into view
        for (Marker m : mMarkerTracker.getMarkers()) {
            if (m.isInfoWindowShown()) {
                m.hideInfoWindow();
            }
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
                selectedAvatarFriendId = -1;
                friendForCameraToTrack = -1;
                findViewById(R.id.my_location_fab).setSelected(false);
                hideInfoPanel();
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
        int thirtySix = getResources().getDimensionPixelSize(R.dimen.thirtySix);
        Bitmap bmp = null;
        try {
            bmp = Picasso.with(this).load(AvatarManager.getAvatar(this, friend.user.username)).resize(thirtySix, thirtySix).get();
        } catch (IOException ignore) {}
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
                avView.setUsername(friend.user.username);
                int spec = View.MeasureSpec.makeMeasureSpec(thirtySix, View.MeasureSpec.AT_MOST);
                avView.measure(spec, spec);
                if (img != null) {
                    avView.setImage(img);
                }
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

    @Override
    @UiThread
    public boolean onMarkerClick(@NonNull final Marker marker) {
        if (marker.equals(mMeMarker)) {
            hideInfoPanel();
            return true;
        }

        final FriendLocation loc = mMarkerTracker.getLocation(marker);
        if (loc == null) {
            // should never happen
            hideInfoPanel();
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
        selectedAvatarFriendId = loc.friendId;
        friendForCameraToTrack = loc.friendId;

        if (mGoogMap != null) {
            CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
            mGoogMap.animateCamera(update);
        }

        showFriendErrorCircle(loc);
        return true;
    }

    private void hideInfoPanel() {
        if (binding == null) {
            return;
        }
        binding.infoPanel.setTag(null);
        binding.infoPanel.setVisibility(View.GONE);
    }

    private boolean isInfoPanelVisible() {
        if (binding == null) {
            return false;
        }
        return binding.infoPanel.getVisibility() == View.VISIBLE;
    }

    @UiThread
    private void showInfoPanel(@NonNull FriendRecord friend, @Nullable FriendLocation loc) {
        if (binding == null) {
            return;
        }

        binding.infoPanel.setVisibility(View.VISIBLE);
        binding.infoPanel.setTag(friend);

        // common stuff
        binding.username.setText(friend.user.username);
        binding.shareSwitch.setOnCheckedChangeListener(null);
        if (friend.sendingBoxId != null) {
            binding.shareSwitch.setChecked(true);
            binding.shareSwitch.setText(R.string.sharing);
        } else {
            binding.shareSwitch.setChecked(false);
            binding.shareSwitch.setText(R.string.not_sharing);
        }
        binding.shareSwitch.setOnCheckedChangeListener(shareCheckedChangeListener);
        binding.address.setTag(loc);

        // If they're not sharing with us
        if (friend.receivingBoxId == null) {
            // show the empty state and get out of here
            binding.address.setText(R.string.not_sharing_with_you);
            binding.refreshProgressBar.setVisibility(View.INVISIBLE);
            binding.refreshButton.setVisibility(View.INVISIBLE);
            binding.bearing.setVisibility(View.GONE);
            binding.motion.setVisibility(View.GONE);
            binding.updateTime.setVisibility(View.INVISIBLE);
            binding.battery.setVisibility(View.INVISIBLE);
            binding.batteryIcon.setVisibility(View.INVISIBLE);
            return;
        }

        // If we don't have location info yet (from a new friend)
        if (loc == null) {
            binding.address.setText(R.string.waiting_for_location_ellipsis);
            binding.refreshButton.setVisibility(View.VISIBLE);
            binding.updateTime.setVisibility(View.INVISIBLE);
            binding.bearing.setVisibility(View.GONE);
            binding.motion.setVisibility(View.GONE);
            binding.battery.setVisibility(View.INVISIBLE);
            binding.batteryIcon.setVisibility(View.INVISIBLE);
            return;
        }

        // == They are sharing with us ==
        // update time
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
        binding.updateTime.setText(String.format("%s", relTime));
        binding.updateTime.setVisibility(View.VISIBLE);

        // refresh status
        updateStatusTrackerListener.onUpdateStatusChanged(loc.friendId);

        // friend activity info
        @DrawableRes int movement = 0;
        switch (loc.movement) {
            case Bicycle:
                movement = R.drawable.ic_sharp_bike_20dp;
                break;
            case OnFoot:
            case Running:
            case Walking:
                movement = R.drawable.ic_sharp_walk_20dp;
                break;
            case Vehicle:
                movement = R.drawable.ic_sharp_car_20dp;
                break;
            default:
                break;
        }
        if (movement != 0) {
            binding.motion.setImageResource(movement);
            binding.motion.setVisibility(View.VISIBLE);
        } else {
            binding.motion.setImageBitmap(null);
            binding.motion.setVisibility(View.GONE);
        }

        // friend bearing
        if (loc.bearing != null) {
            binding.bearing.setVisibility(View.VISIBLE);
            binding.bearing.setRotation(loc.bearing);
        } else {
            binding.bearing.setVisibility(View.GONE);
        }

        // battery status
        if (loc.batteryLevel != null) {
            binding.battery.setText(getString(R.string.number_percent_msg, loc.batteryLevel));
            @DrawableRes int batteryImg;
            if (loc.batteryLevel >= 95) {
                batteryImg = R.drawable.ic_sharp_battery_full_20dp;
            } else if (loc.batteryLevel >= 85) {
                batteryImg = R.drawable.ic_sharp_battery_90_20dp;
            } else if (loc.batteryLevel >= 70) {
                batteryImg = R.drawable.ic_sharp_battery_80_20dp;
            } else if (loc.batteryLevel >= 55) {
                batteryImg = R.drawable.ic_sharp_battery_60_20dp;
            } else if (loc.batteryLevel >= 40) {
                batteryImg = R.drawable.ic_sharp_battery_50_20dp;
            } else if (loc.batteryLevel >= 25) {
                batteryImg = R.drawable.ic_sharp_battery_30_20dp;
            } else {
                batteryImg = R.drawable.ic_sharp_battery_20_20dp;
            }
            Drawable battery = getDrawable(batteryImg);
            binding.batteryIcon.setImageDrawable(battery);
        } else {
            binding.battery.setText(null);
            binding.batteryIcon.setImageDrawable(null);
        }

        // the address
        RevGeocoding rg = ReverseGeocodingCache.get(loc.latitude, loc.longitude);
        if (rg != null) {
            binding.address.setText(rg.getAddress());
        } else {
            binding.address.setText(R.string.loading_ellipsis);
            ReverseGeocodingCache.fetch(MapActivity.this, loc.latitude, loc.longitude, new ReverseGeocodingCache.OnCachedListener() {
                @Override
                public void onReverseGeocodingCached(@Nullable RevGeocoding rg) {
                    if (rg == null) {
                        return;
                    }
                    FriendLocation savedLoc = (FriendLocation) binding.address.getTag();
                    if (savedLoc == null) {
                        return;
                    }
                    if (savedLoc.latitude == loc.latitude && savedLoc.longitude == loc.longitude) {
                        binding.address.setText(rg.getAddress());
                    }
                }
            });
        }

        binding.refreshButton.setVisibility(View.VISIBLE);
    }

    @Override
    @UiThread
    public void onAvatarSelected(FriendRecord fr) {
        Marker marker = mMarkerTracker.getById(fr.id);
        binding.infoPanel.setVisibility(View.VISIBLE);
        if (marker == null) {
            selectedAvatarFriendId = -1;
            showInfoPanel(fr, null);
            return;
        }

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
                        showInfoPanel(fr, loc);
                        showFriendErrorCircle(loc);
                    }
                });
            }
        });
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
    public void onInfoOverflowAction(View v) {
        L.i("onInfoOverflowAction");
        PopupMenu menu = new PopupMenu(this, v);
        getMenuInflater().inflate(R.menu.info_panel_overflow, menu.getMenu());
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.remove_friend) {
                    onRemoveFriendAction();
                }
                return true;
            }
        });
        menu.show();
    }

    @UiThread
    private void onRemoveFriendAction() {
        final FriendRecord friend = (FriendRecord) binding.infoPanel.getTag();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(R.string.remove_friend_interrogative)
                .setMessage(R.string.remove_friend_prompt_msg)
                .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        App.runInBackground(new WorkerRunnable() {
                            @Override
                            public void run() {
                                try {
                                    DB.get().removeFriend(friend);
                                } catch (DB.DBException ex) {
                                    L.w("Error removing friend: " + friend, ex);
                                    Utils.showAlert(MapActivity.this, R.string.unexpected_error, R.string.remove_friend_error_msg);
                                }
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(true)
                .show();
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

    public void onShowSettings(View v) {
        startActivityForResult(SettingsActivity.newIntent(this), SettingsActivity.REQUEST_EXIT);
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
                userRecord = db.addUser(userToRequest.id, userToRequest.username, userToRequest.publicKey);
                BackupDatabaseJob.scheduleBackup(this);
            }

            // check if we already have this user as a friend, and if we're already sharing with them
            final FriendRecord friend = db.getFriendByUserId(userRecord.id);
            if (friend != null) {
                if (friend.sendingBoxId != null) {
                    // send the sending box id to this person one more time, just in case
                    UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                    String errMsg = OscarClient.queueSendMessage(this, userRecord, comm, false, false);
                    if (errMsg != null) {
                        CloudLogger.log(errMsg);
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
                CloudLogger.log(ex);
            }
            BackupDatabaseJob.scheduleBackup(this);
            Utils.showStringAlert(this, null, "You're now sharing with " + username);
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Network problem trying to share your location. Check your connection then try again.");
        } catch (DB.DBException dbe) {
            Utils.showStringAlert(this, null, "Error adding friend into database");
            CloudLogger.log(dbe);
        }
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

    private UpdateStatusTracker.Listener updateStatusTrackerListener = new UpdateStatusTracker.Listener() {
        @Override
        public void onUpdateStatusChanged(long friendId) {
            if (friendId != selectedAvatarFriendId || binding == null) {
                return;
            }
            UpdateStatusTracker.State status = UpdateStatusTracker.getFriendState(friendId);
            int vis;
            @ColorRes int colorId = 0;
            switch (status) {
                case NotRequested:
                    vis = View.GONE;
                    break;
                case Requested:
                    vis = View.VISIBLE;
                    colorId = R.color.pijun_canary;
                    break;
                case RequestedAndUnresponsive:
                    vis = View.VISIBLE;
                    colorId = R.color.pijun_grey;
                    break;
                case RequestDenied:
                    vis = View.VISIBLE;
                    colorId = R.color.pijun_red;
                    break;
                case RequestAcknowledged:
                    vis = View.VISIBLE;
                    colorId = R.color.pijun_blue;
                    break;
                case RequestFulfilled:
                    vis = View.GONE;
                    break;
                default:
                    vis = View.GONE;
                    break;
            }
            if (colorId != 0) {
                DrawableCompat.setTint(binding.refreshProgressBar.getIndeterminateDrawable(),
                        ContextCompat.getColor(MapActivity.this, colorId));
            }
            binding.refreshProgressBar.setVisibility(vis);
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

            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    LocationUtils.upload(location);
                }
            });
        }
    };

    //region AvatarManager.Listener
    private AvatarManager.Listener avatarListener = new AvatarManager.Listener() {
        @Override
        public void onAvatarUpdated(@Nullable String username) {
            avatarsAdapter.onAvatarUpdated(username);
        }
    };
    //endregion

    //region DB.Listener
    @Override
    public void onFriendLocationUpdated(final FriendLocation loc) {
        if (selectedAvatarFriendId == loc.friendId) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    FriendRecord friend = DB.get().getFriendById(loc.friendId);
                    if (friend != null) {
                        App.runOnUiThread(new UiRunnable() {
                            @Override
                            public void run() {
                                showInfoPanel(friend, loc);
                            }
                        });
                    }
                }
            });
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
                Long friendId = (Long) mCurrentCircle.getTag();
                if (friendId != null && loc.friendId == friendId) {
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

    @Override
    public void onFriendRemoved(long friendId) {
        removeFriendFromMap(friendId);
        avatarsAdapter.removeFriend(friendId);
        // if the info panel is showing their info, hide it
        if (!isInfoPanelVisible()) {
            return;
        }
        FriendRecord friend = (FriendRecord) binding.infoPanel.getTag();
        if (friend.id == friendId) {
            hideInfoPanel();
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
                if (selectedAvatarFriendId == friend.id && binding != null && binding.infoPanel.getVisibility() == View.VISIBLE) {
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
            if (selectedAvatarFriendId == friend.id && binding != null && binding.infoPanel.getVisibility() == View.VISIBLE) {
                showInfoPanel(friend, null);
            }
        });
    }

    @Override
    public void onStartedSharingWithUser(long userId) {
        // TODO: This is when we add a friend, or when we re-enable sharing with a friend
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

    private OscarSocket.Listener oscarSocketListener = new OscarSocket.Listener() {
        @Override
        public void onDisconnect(int code, String reason) {
            if (!isStarted) {
                return;
            }
            try {
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

    //region Share switch checked change listener
    private CompoundButton.OnCheckedChangeListener shareCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            FriendRecord friend = (FriendRecord) binding.infoPanel.getTag();
            if (friend == null) {
                L.i("Share switch changed, but no FriendRecord found");
                return;
            }
            if (isChecked) {
                startSharingWith(friend);
            } else {
                stopSharingWith(friend);
            }
        }
    };
    //endregion
}
