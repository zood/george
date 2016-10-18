package io.pijun.george;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
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
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;

import io.pijun.george.service.LocationMonitor;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private MapView mMapView;
    private GoogleMap mGoogleMap;
    private LocationSource.OnLocationChangedListener mMyLocationListener;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, MapActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Is there a user account here? If not, send them to the login/sign up screen
        if (Prefs.get(this).getAccessToken() == null) {
            Intent welcomeIntent = WelcomeActivity.newIntent(this);
            startActivity(welcomeIntent);
            finish();
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

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mMapView.onStart();
        checkForLocationPermission();
        App.registerOnBus(this);
        mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        mMapView.onPause();

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mMapView.onStop();
        App.unregisterFromBus(this);

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onDestroy() {
        mMapView.onDestroy();
        mMapView = null;

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mMapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        mMapView.onLowMemory();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;
        mGoogleMap.setLocationSource(new LocationSource() {
            @Override
            public void activate(OnLocationChangedListener onLocationChangedListener) {
                mMyLocationListener = onLocationChangedListener;
                if (mLastLocation != null) {
                    mMyLocationListener.onLocationChanged(mLastLocation);
                }
            }

            @Override
            public void deactivate() {
                mMyLocationListener = null;
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mGoogleMap.setMyLocationEnabled(true);
        }
    }

//    @Subscribe
//    @AnyThread
//    public void onLocationUpdate(Location location) {
//        L.i("onLocationUpdate: " + location);
//        if (mMyLocationListener != null) {
//            L.i("|   location listener is here");
//            mMyLocationListener.onLocationChanged(location);
//            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
//            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(ll, 16);
//            mGoogleMap.animateCamera(cameraUpdate);
//        }
//    }

    @UiThread
    private void checkForLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            L.i("already have permission");
            locationPermissionVerified();
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // show the reasoning
            L.i("showing reason for permission before asking");
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
            builder.setTitle("Permission request");
            builder.setMessage("Pijun uses your location to show your position on the map, and to securely share it with friends that you've authorized. It's never used for any other purpose.");
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    L.i("requesting permission after explaining");
                    ActivityCompat.requestPermissions(
                            MapActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_LOCATION_PERMISSION);
                }
            });
            builder.show();
        } else {
            L.i("requesting location without showing permission");
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
        startService(LocationMonitor.newIntent(this));
        if (mGoogleMap != null) {
            mGoogleMap.setMyLocationEnabled(true);
            if (mMyLocationListener != null && mLastLocation != null) {
                mMyLocationListener.onLocationChanged(mLastLocation);
            }
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

    @AnyThread
    private void beginLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This should never happen. Nobody should be calling this method before permission has been obtained.
            L.w("MapActivity.beginLocationUpdates was called before obtaining location permission");
            // TODO: log the stack trace to a server for debugging
            return;
        }
        LocationRequest req = LocationRequest.create();
        req.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, req, this);
    }

    @UiThread
    public void onShowDrawerAction(View v) {
        L.i("onShowDrawerAction");
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.openDrawer(GravityCompat.START, true);
    }

    @UiThread
    private void onShowFriends() {
        Intent i = FriendsActivity.newIntent(this);
        startActivity(i);
    }

    /*
    private void getMessagesAction() {
        OscarAPI client = OscarClient.newInstance(Prefs.get(this).getAccessToken());
        try {
            Response<Message[]> response = client.getMessages().execute();
            if (response.isSuccessful()) {
                byte[] secretKey = Prefs.get(MapActivity.this).getKeyPair().secretKey;
                Message[] messages = response.body();
                DB db = new DB(this);
                for (Message msg : messages) {
                    try {
                        byte[] pubKey = Vault.getPublicKey(MapActivity.this, msg.senderId);
                        byte[] msgBytes = Sodium.publicKeyDecrypt(msg.cipherText, msg.nonce, pubKey, secretKey);
                        UserComm comm = UserComm.fromJSON(msgBytes);
                        L.i("comm: " + comm);
                        Response<User> userResp = client.getUser(Hex.toHexString(msg.senderId)).execute();
                        String username = "";
                        if (userResp.isSuccessful()) {
                            username = userResp.body().username;
                        } else {
                            L.i("username is not successful");
                        }
                        long result = db.addShareRequest(username, msg.senderId, comm.note);
                        L.i("add request: " + result);
                    } catch (IOException ex) {
                        L.w("trouble receiving public key  or username for user " + Hex.toHexString(msg.senderId), ex);
                    }
                }
            }
        } catch (IOException ex) {
            L.w("serious problem getting messages", ex);
        }
    }
    */

    /*
    @WorkerThread
    private void approveAllRequests(ArrayList<ShareRequest> requests) {
        byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(boxId);
        DB db = new DB(this);
        OscarAPI client = OscarClient.newInstance(Prefs.get(this).getAccessToken());
        for (ShareRequest r : requests) {
            // add this to our database
            db.addGrantedShare(r.username, r.userId, boxId);
            // send a message letting the user know that we approved the request and the drop box they should check
            UserComm sharingGrant = UserComm.newLocationSharingGrant(boxId);
            byte[] rcvrPubKey;
            try {
                rcvrPubKey = Vault.getPublicKey(this, r.userId);
            } catch (IOException ex) {
                L.w("unable to get public key to approve sharing request", ex);
                continue;
            }
            SecretKeyEncryptedMessage message = Sodium.publicKeyEncrypt(
                    sharingGrant.toJSON(),
                    rcvrPubKey,
                    Prefs.get(this).getKeyPair().secretKey);
            try {
                Response<Void> response = client.sendMessage(Hex.toHexString(r.userId), message).execute();
                if (!response.isSuccessful()) {
                    L.i("sending sharing grant failed: " + OscarError.fromResponse(response));
                    continue;
                }
            } catch (Exception ex) {
                L.w("unable to send message approving sharing request", ex);
            }
        }

    }
    */

    private NavigationView.OnNavigationItemSelectedListener navItemListener = new NavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            L.i("clicked item: " + item.getTitle());
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            drawer.closeDrawers();

            if (item.getItemId() == R.id.your_friends) {
                onShowFriends();
            }

            return false;
        }
    };

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            App.postOnBus(mLastLocation);
            if (mMyLocationListener != null) {
                mMyLocationListener.onLocationChanged(mLastLocation);
            }
        }

        // these are the location settings we want
        LocationRequest req = LocationRequest.create();
        req.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // check whether the location settings can currently be met by the hardware
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(req);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
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

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (mMyLocationListener != null) {
            mMyLocationListener.onLocationChanged(location);
        }

        App.postOnBus(location);
    }
}
