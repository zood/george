package xyz.zood.george;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

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
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;

import io.pijun.george.App;
import io.pijun.george.AuthenticationManager;
import io.pijun.george.Constants;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.MarkerTracker;
import io.pijun.george.MessageProcessor;
import io.pijun.george.Prefs;
import io.pijun.george.SettingsFragment;
import io.pijun.george.UiRunnable;
import io.pijun.george.UpdateStatusTracker;
import io.pijun.george.Utils;
import io.pijun.george.WelcomeActivity;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.animation.DoubleEvaluator;
import io.pijun.george.animation.LatLngEvaluator;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarSocket;
import io.pijun.george.api.PushNotification;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.view.AvatarRenderer;
import io.pijun.george.view.MyLocationView;
import xyz.zood.george.databinding.FragmentMainBinding;
import xyz.zood.george.notifier.ActivityRecognitionPermissionNotifier;
import xyz.zood.george.notifier.BackgroundDataRestrictionNotifier;
import xyz.zood.george.notifier.BackgroundLocationPermissionNotifier;
import xyz.zood.george.notifier.ClientNotConnectedNotifier;
import xyz.zood.george.notifier.ForegroundLocationPermissionNotifier;
import xyz.zood.george.notifier.PreQLocationPermissionNotifier;
import xyz.zood.george.receiver.UserActivityReceiver;
import xyz.zood.george.viewmodels.Event;
import xyz.zood.george.viewmodels.MainViewModel;
import xyz.zood.george.widget.InfoPanel;
import xyz.zood.george.widget.ZoodDialog;

public class MainFragment extends Fragment implements OnMapReadyCallback, DB.Listener, AuthenticationManager.Listener, BackPressInterceptor {

    static final String TAG = "main";

    private static final String ARG_ACCESS_TOKEN = "access_token";
    private static final String ARG_KEY_PAIR = "key_pair";

    private static final int REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 16;
    private static final int REQUEST_BACKGROUND_LOCATION_PERMISSION = 18;
    private static final int REQUEST_BACKGROUND_LOCATION_PERMISSION_THEN_ADD_FRIEND = 26;
    private static final int REQUEST_FOREGROUND_LOCATION_PERMISSION = 22;
    private static final int REQUEST_PRE_Q_LOCATION_PERMISSION = 24;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private String accessToken;
    private FragmentMainBinding binding;
    private Circle currentCircle;
    private long friendForCameraToTrack = -1;
    private FriendshipManager friendshipManager;
    private GoogleMap googMap;
    private InfoPanel infoPanel;
    private boolean isFlyingCameraToMyLocation = false;
    private KeyPair keyPair;
    private ActivityRecognitionPermissionNotifier userActivityPermissionNotifier;
    private BackgroundLocationPermissionNotifier bgLocationPermissionNotifier;
    private ForegroundLocationPermissionNotifier fgLocationPermissionNotifier;
    private PreQLocationPermissionNotifier preQLocPermNotifier;
    private FusedLocationProviderClient locationProviderClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private final MarkerTracker markerTracker = new MarkerTracker();
    private Marker meMarker;
    private Circle myCircle;
    private ClientNotConnectedNotifier notConnectedNotifier;
    private OscarSocket oscarSocket;
    private SettingsClient settingsClient;
    private MainViewModel viewModel;

    @NonNull
    static MainFragment newInstance(@NonNull String accessToken, @NonNull KeyPair keyPair) {
        MainFragment f = new MainFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACCESS_TOKEN, accessToken);
        args.putParcelable(ARG_KEY_PAIR, keyPair);

        f.setArguments(args);

        return f;
    }

    //region Fragment lifecycle

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            throw new RuntimeException("You must create the fragment via newInstance");
        }
        accessToken = args.getString(ARG_ACCESS_TOKEN);
        if (TextUtils.isEmpty(accessToken)) {
            throw new RuntimeException("missing access token");
        }
        keyPair = args.getParcelable(ARG_KEY_PAIR);
        if (keyPair == null) {
            throw new RuntimeException("missing key pair");
        }

        Context ctx = requireContext();
        friendshipManager = new FriendshipManager(ctx, DB.get(), OscarClient.getQueue(ctx), accessToken, keyPair);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext());
        settingsClient = LocationServices.getSettingsClient(ctx);
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(3 * DateUtils.SECOND_IN_MILLIS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();

        DB.get().addListener(this);
        AuthenticationManager.get().addListener(this);

        oscarSocket = new OscarSocket(oscarSocketListener);

        getLifecycle().addObserver(new AppInForegroundObserver());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false);

        infoPanel = new InfoPanel(binding.infoPanel, requireContext(), infoPanelListener);
        binding.map.onCreate(savedInstanceState);
        binding.map.getMapAsync(this);

        viewModel.getSelectedFriend().observe(getViewLifecycleOwner(), new Observer<FriendRecord>() {
            @Override
            public void onChanged(FriendRecord friend) {
                viewModel.onCloseTimedSheetAction();
                onFriendSelected(friend);
            }
        });
        viewModel.getOnAddFriendClicked().observe(getViewLifecycleOwner(), new Observer<Event<Boolean>>() {
            @Override
            public void onChanged(Event<Boolean> evt) {
                Boolean clicked = evt.getEventIfNotHandled();
                if (clicked != null) {
                    onAddFriendClicked();
                }
            }
        });
        viewModel.getTimedShareIsRunning().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isRunning) {
                float transY;
                if (isRunning == null || !isRunning) {
                    transY = 0;
                    binding.timedShareFab.setSelected(false);
                } else {
                    transY = -getResources().getDimension(R.dimen.timed_share_sheet_peek_height);
                    binding.timedShareFab.setSelected(true);
                }
                binding.timedShareFab.animate().translationY(transY);
                binding.infoPanel.animate().translationY(transY);
            }
        });

        binding.myLocationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.myLocationFab.setSelected(true);
                friendForCameraToTrack = 0;
                flyCameraToMyLocation();

                // if we're showing the avatar info, hide it
                infoPanel.hide();
                binding.timedShareFab.setVisibility(View.VISIBLE);
            }
        });
        binding.timedShareFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.notifyTimedShareClicked();
            }
        });
        binding.settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });

        DB.get().addListener(this);
        AvatarManager.addListener(avatarListener);

        getLifecycle().addObserver(new GoogleMapLifecycleObserver(binding.map));
        getLifecycle().addObserver(new BackgroundDataRestrictionNotifier(requireActivity(), binding.banners));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bgLocationPermissionNotifier = new BackgroundLocationPermissionNotifier(requireActivity(), this, binding.banners, REQUEST_BACKGROUND_LOCATION_PERMISSION);
            getLifecycle().addObserver(bgLocationPermissionNotifier);
            fgLocationPermissionNotifier = new ForegroundLocationPermissionNotifier(requireActivity(), this, binding.banners, REQUEST_FOREGROUND_LOCATION_PERMISSION);
            getLifecycle().addObserver(fgLocationPermissionNotifier);
            userActivityPermissionNotifier = new ActivityRecognitionPermissionNotifier(requireActivity(), this, binding.banners, REQUEST_ACTIVITY_RECOGNITION_PERMISSION);
            getLifecycle().addObserver(userActivityPermissionNotifier);
        } else {
            preQLocPermNotifier = new PreQLocationPermissionNotifier(requireActivity(), this, binding.banners, REQUEST_PRE_Q_LOCATION_PERMISSION);
            getLifecycle().addObserver(preQLocPermNotifier);
        }
        notConnectedNotifier = new ClientNotConnectedNotifier(requireActivity(), binding.banners);

        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        oscarSocket = null;
        AuthenticationManager.get().removeListener(this);

        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        DB.get().removeListener(this);
        AvatarManager.removeListener(avatarListener);
        markerTracker.clear();
        googMap = null;
        meMarker = null;
        infoPanel = null;
        binding = null;
        currentCircle = null;
        myCircle = null;

        super.onDestroyView();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (binding != null) {
            binding.map.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (binding != null) {
            binding.map.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Context ctx = requireContext();
        if (ctx instanceof BackPressNotifier) {
            ((BackPressNotifier) ctx).setBackPressInterceptor(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPermissions();
        } else {
            checkPreQPermissions();
        }

        UpdateStatusTracker.addListener(updateStatusTrackerListener);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                oscarSocket.connect(accessToken);

                ArrayList<FriendRecord> friends = DB.get().getFriends();
                for (FriendRecord fr: friends) {
                    if (fr.receivingBoxId != null) {
                        oscarSocket.watch(fr.receivingBoxId);
                    }
                }

                MessageProcessor.retrieveAndProcessNewMessages(accessToken);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();

        UpdateStatusTracker.removeListener(updateStatusTrackerListener);

        // save the camera position
        if (googMap != null) {
            CameraPosition pos = googMap.getCameraPosition();
            Prefs.get(requireContext()).setCameraPosition(pos);
        }

        // stop location updates
        locationProviderClient.removeLocationUpdates(mLocationCallbackHelper);

        oscarSocket.disconnect();

        Context ctx = requireContext();
        if (ctx instanceof BackPressNotifier) {
            ((BackPressNotifier) ctx).setBackPressInterceptor(null);
        }
    }

    //endregion



    @WorkerThread
    private void addMapMarker(@NonNull FriendRecord friend, @NonNull FriendLocation loc) {
        if (googMap == null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        BitmapDescriptor icon = AvatarRenderer.getBitmapDescriptor(ctx, friend.user.username, R.dimen.thirtySix);

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                // check if it's already there
                if (markerTracker.getById(friend.id) != null) {
                    // don't add another one
                    return;
                }
                MarkerOptions opts = new MarkerOptions()
                        .position(new LatLng(loc.latitude, loc.longitude))
                        .icon(icon)
                        .anchor(0.5f, 0.5f)
                        .title(friend.user.username);
                Marker marker = googMap.addMarker(opts);
                marker.setTag(loc);
                markerTracker.add(marker, friend.id, loc);
            }
        });
    }

    @UiThread
    private void addMyErrorCircle(Location location) {
        if (googMap == null) {
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
        myCircle = googMap.addCircle(opts);
    }

    @UiThread
    private void addMyLocation(Location location) {
        if (googMap == null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        Bitmap bitmap = MyLocationView.getBitmap(ctx);
        BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions opts = new MarkerOptions()
                .position(ll)
                .anchor(0.5f, 0.5f)
                .icon(descriptor);
        meMarker = googMap.addMarker(opts);
    }

    private void flyCameraToMyLocation() {
        if (googMap == null) {
            return;
        }
        if (meMarker == null) {
            return;
        }
        float zoom = Math.max(googMap.getCameraPosition().zoom, Constants.DEFAULT_ZOOM_LEVEL);

        CameraPosition cp = new CameraPosition.Builder()
                .zoom(zoom)
                .target(meMarker.getPosition())
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        isFlyingCameraToMyLocation = true;
        googMap.animateCamera(cu, new GoogleMap.CancelableCallback() {
            @Override
            public void onFinish() {
                isFlyingCameraToMyLocation = false;
            }

            @Override
            public void onCancel() {
                isFlyingCameraToMyLocation = false;
            }
        });
    }

    private void onFriendSelected(@NonNull FriendRecord fr) {
        Marker marker = markerTracker.getById(fr.id);
        if (marker == null) {
            showInfoPanel(fr, null);
            return;
        }

        // Is the info panel already showing for this user? If so, just center the camera and follow
        if (infoPanel.getFriendId() == fr.id) {
            friendForCameraToTrack = fr.id;
            CameraUpdate update = CameraUpdateFactory.newLatLng(marker.getPosition());
            googMap.animateCamera(update);
            return;
        }

        friendForCameraToTrack = fr.id;
        binding.myLocationFab.setSelected(false);
        float zoom = Math.max(googMap.getCameraPosition().zoom, Constants.DEFAULT_ZOOM_LEVEL);
        CameraPosition cp = new CameraPosition.Builder()
                .target(marker.getPosition())
                .zoom(zoom)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        googMap.animateCamera(cu);

        FriendLocation loc = (FriendLocation) marker.getTag();
        if (loc == null) {
            throw new RuntimeException("Marker location should never be null. Should contain the friend's location");
        }
        showInfoPanel(fr, loc);
        showFriendErrorCircle(loc);
    }

    @UiThread
    private void onAddFriendClicked() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        // If the user hasn't granted background location permission, we need
        if (!Permissions.checkBackgroundLocationPermission(ctx)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkBackgroundLocationPermission(true);
            } else {
                checkPreQPermissions();
            }
            return;
        }

        AddFriendFragment fragment = AddFriendFragment.newInstance(accessToken, keyPair);
        FragmentManager mgr = getParentFragmentManager();
        mgr.beginTransaction()
                .setCustomAnimations(R.animator.new_enter_from_right,
                        R.animator.new_exit_to_left,
                        R.animator.new_enter_from_left,
                        R.animator.new_exit_to_right)
                .replace(R.id.fragment_host, fragment)
                .addToBackStack(null)
                .commit();
    }

    @UiThread
    private void showFriendErrorCircle(@NonNull FriendLocation loc) {
        if (googMap == null) {
            return;
        }

        // check if we even need a circle
        if (loc.accuracy == null) {
            // no circle needed
            // check if there's a circle that we need to remove
            if (currentCircle != null) {
                currentCircle.remove();
                currentCircle = null;
            }
            return;
        }
        // check if there is already a circle
        if (currentCircle != null) {
            // does this circle already belong to this friend?
            Long friendId = (Long) currentCircle.getTag();
            if (friendId == null || friendId != loc.friendId) {
                // nope! Remove it.
                currentCircle.remove();
                currentCircle = null;
            }
        }

        // If this friend doesn't have a circle, create it
        if (currentCircle == null) {
            CircleOptions opts = new CircleOptions()
                    .center(new LatLng(loc.latitude, loc.longitude))
                    .radius(loc.accuracy)
                    .strokeColor(Color.TRANSPARENT)
                    .fillColor(ContextCompat.getColor(requireContext(), R.color.error_circle_fill));
            currentCircle = googMap.addCircle(opts);
            currentCircle.setTag(loc.friendId);
        } else {
            // This friend already has a circle, so just adjust the radius and position
            currentCircle.setRadius(loc.accuracy);
            currentCircle.setCenter(new LatLng(loc.latitude, loc.longitude));
        }

    }

    @UiThread
    private void showInfoPanel(@NonNull FriendRecord friend, @Nullable FriendLocation loc) {
        binding.timedShareFab.setVisibility(View.INVISIBLE);
        infoPanel.show(friend, loc);

        // if the location data for the friend is really old, request a refresh
        if (loc != null) {
            if (System.currentTimeMillis() - loc.time > DateUtils.MINUTE_IN_MILLIS * 20) {
                infoPanelListener.onInfoPanelLocationRequested(friend);
            }
        }
    }

    private void showSettings() {
        SettingsFragment fragment = SettingsFragment.newInstance();
        FragmentManager mgr = getParentFragmentManager();
        mgr.beginTransaction()
                .setCustomAnimations(R.animator.new_enter_from_right,
                        R.animator.new_exit_to_left,
                        R.animator.new_enter_from_left,
                        R.animator.new_exit_to_right)
                .replace(R.id.fragment_host, fragment)
                .addToBackStack(null)
                .commit();
    }

    //region Permissions

    @UiThread
    private void activityRecognitionPermissionGranted() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        UserActivityReceiver.requestUpdates(ctx);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @UiThread
    private void backgroundLocationPermissionGranted() {
        verifyLocationSettingsResolution();

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                ArrayList<FriendRecord> friends = DB.get().getFriends();
                if (friends.size() == 0) {
                    return;
                }

                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        checkActivityRecognitionPermission();
                    }
                });
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @UiThread
    private void checkActivityRecognitionPermission() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        if (Permissions.checkGrantedActivityRecognitionPermission(ctx)) {
            activityRecognitionPermissionGranted();
            return;
        }

        if (viewModel.isActivityRecognitionRationaleVisible()) {
            return;
        }

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Do we need to show the rationale?
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)) {
            ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.activity_recognition_permission_reason_msg));
            dialog.setTitle(getString(R.string.permission_request));
            dialog.setButton1(getString(R.string.ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    viewModel.setActivityRecognitionRationaleVisible(false);
                    requestPermissions(Permissions.getActivityRecognitionPermissions(), REQUEST_ACTIVITY_RECOGNITION_PERMISSION);
                }
            });
            dialog.setCancelable(false);
            dialog.show(getParentFragmentManager(), null);
            viewModel.setActivityRecognitionRationaleVisible(true);
        } else {
            requestPermissions(Permissions.getActivityRecognitionPermissions(), REQUEST_ACTIVITY_RECOGNITION_PERMISSION);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @UiThread
    private void checkBackgroundLocationPermission(boolean fromAddFriendClick) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        if (Permissions.checkGrantedBackgroundLocationPermission(ctx)) {
            backgroundLocationPermissionGranted();
            return;
        }

        if (viewModel.isBackgroundLocationRationaleVisible()) {
            return;
        }

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        // Do we need to show the rationale for background location?
        if (fromAddFriendClick || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.background_location_permission_reason_msg));
            dialog.setTitle(getString(R.string.permission_request));
            dialog.setButton1(getString(R.string.ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    viewModel.setBackgroundLocationRationaleVisible(false);
                    int code;
                    if (fromAddFriendClick) {
                        code = REQUEST_BACKGROUND_LOCATION_PERMISSION_THEN_ADD_FRIEND;
                    } else {
                        code = REQUEST_BACKGROUND_LOCATION_PERMISSION;
                    }
                    requestPermissions(Permissions.getBackgroundLocationPermissions(), code);
                }
            });
            if (fromAddFriendClick) {
                dialog.setButton2(getString(R.string.no_thanks), new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        viewModel.setBackgroundLocationRationaleVisible(false);
                    }
                });
            }
            // In order to track when the dialog was dismissed, don't allow cancelling
            dialog.setCancelable(false);
            dialog.show(getParentFragmentManager(), null);
            viewModel.setBackgroundLocationRationaleVisible(true);
        } else {
            requestPermissions(Permissions.getBackgroundLocationPermissions(), REQUEST_BACKGROUND_LOCATION_PERMISSION);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void checkPermissions() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        if (Permissions.checkGrantedForegroundLocationPermission(ctx)) {
            foregroundLocationPermissionGranted();
            return;
        }

        if (viewModel.isForegroundLocationRationaleVisible()) {
            return;
        }

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        // Do we need to show the rationale for foreground location?
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.foreground_location_permission_reason_msg));
            dialog.setTitle(getString(R.string.permission_request));
            dialog.setButton1(getString(R.string.ok), new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    viewModel.setForegroundLocationRationaleVisible(false);
                    requestPermissions(Permissions.getForegroundLocationPermissions(), REQUEST_FOREGROUND_LOCATION_PERMISSION);
                }
            });
            // In order to track when the dialog is dismissed, don't allow cancelling
            dialog.setCancelable(false);
            dialog.show(getParentFragmentManager(), null);
            viewModel.setForegroundLocationRationaleVisible(true);
        } else {
            requestPermissions(Permissions.getForegroundLocationPermissions(), REQUEST_FOREGROUND_LOCATION_PERMISSION);
        }
    }

    private void checkPreQPermissions() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        if (Permissions.checkGrantedPreQLocationPermission(ctx)) {
            preQLocationPermissionGranted();
            return;
        }

        if (viewModel.isPreQLocationRationaleVisible()) {
            return;
        }

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Do we need to show the rationale?
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.location_permission_reason_msg));
            dialog.setTitle(getString(R.string.permission_request));
            dialog.setButton1(getString(R.string.ok), new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    viewModel.setPreQLocationRationaleVisible(false);
                    requestPermissions(Permissions.getPreQLocationPermissions(), REQUEST_PRE_Q_LOCATION_PERMISSION);
                }
            });
            // In order to track when the dialog is dismissed, don't allow cancelling
            dialog.setCancelable(false);
            dialog.show(getParentFragmentManager(), null);
            viewModel.setForegroundLocationRationaleVisible(true);
        } else {
            requestPermissions(Permissions.getPreQLocationPermissions(), REQUEST_PRE_Q_LOCATION_PERMISSION);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @UiThread
    private void foregroundLocationPermissionGranted() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        // Start requesting location updates. The permission check is redundant, but we do it quiet the linter
        if (Permissions.checkGrantedForegroundLocationPermission(ctx)) {
            locationProviderClient.requestLocationUpdates(locationRequest, mLocationCallbackHelper, Looper.getMainLooper());
        }

        // If we have at least 1 friend, then we need permission for background location
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                ArrayList<FriendRecord> friends = DB.get().getFriends();
                if (friends.size() == 0) {
                    return;
                }

                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        checkBackgroundLocationPermission(false);
                    }
                });
            }
        });
    }

    @UiThread
    private void preQLocationPermissionGranted() {
        UserActivityReceiver.requestUpdates(App.getApp().getApplicationContext());
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        if (Permissions.checkGrantedPreQLocationPermission(ctx)) {
            locationProviderClient.requestLocationUpdates(locationRequest, mLocationCallbackHelper, Looper.getMainLooper());
        }
        verifyLocationSettingsResolution();
    }

    private void verifyLocationSettingsResolution() {
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnFailureListener(e -> {
                    if (!isVisible()) {
                        return;
                    }
                    FragmentActivity activity = requireActivity();
                    int statusCode = ((ApiException) e).getStatusCode();
                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                ResolvableApiException rae = (ResolvableApiException) e;
                                rae.startResolutionForResult(activity, REQUEST_LOCATION_SETTINGS);
                            } catch (IntentSender.SendIntentException sie) {
                                L.w("Unable to start settings resolution", sie);
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String errMsg = "Location settings are inadequate, and cannot be fixed here. Fix in Settings.";
                            Toast.makeText(activity, errMsg, Toast.LENGTH_LONG).show();
                            break;
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switch (requestCode) {
                case REQUEST_ACTIVITY_RECOGNITION_PERMISSION:
                    if (Permissions.checkGrantedActivityRecognitionPermission(ctx)) {
                        activityRecognitionPermissionGranted();
                    } else {
                        userActivityPermissionNotifier.show();
                    }
                    break;
                case REQUEST_BACKGROUND_LOCATION_PERMISSION_THEN_ADD_FRIEND:
                    if (Permissions.checkGrantedBackgroundLocationPermission(ctx)) {
                        // Just show the add friend dialog - the other permission requests can
                        // happen when they come back to the screen, after they have added their friend
                        onAddFriendClicked();
                    }
                case REQUEST_BACKGROUND_LOCATION_PERMISSION:
                    if (Permissions.checkGrantedBackgroundLocationPermission(ctx)) {
                        backgroundLocationPermissionGranted();
                    } else {
                        bgLocationPermissionNotifier.show();
                    }
                    break;
                case REQUEST_FOREGROUND_LOCATION_PERMISSION:
                    if (Permissions.checkGrantedForegroundLocationPermission(ctx)) {
                        foregroundLocationPermissionGranted();
                    } else {
                        fgLocationPermissionNotifier.show();
                    }
                    break;
            }
        } else {
            if (requestCode == REQUEST_PRE_Q_LOCATION_PERMISSION) {
                if (Permissions.checkGrantedPreQLocationPermission(ctx)) {
                        preQLocationPermissionGranted();
                    } else {
                        preQLocPermNotifier.show();
                    }
            }
        }
    }

    //endregion

    //region OnMapReadyCallback

    @Override
    @UiThread
    public void onMapReady(GoogleMap googleMap) {
        this.googMap = googleMap;
        Context ctx = requireContext();
        boolean success = googMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(ctx, R.raw.map_style));
        if (!success) {
            L.w("Map style parsing failed");
        }
        CameraPosition pos = Prefs.get(ctx).getCameraPosition();
        if (pos != null) {
            googMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
        googMap.setOnMarkerClickListener(markerClickListener);
        googMap.getUiSettings().setCompassEnabled(false);
        googMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                if (reason == REASON_GESTURE) {
                    friendForCameraToTrack = -1;
                    binding.myLocationFab.setSelected(false);
                }
            }
        });
        googMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                friendForCameraToTrack = -1;
                binding.myLocationFab.setSelected(false);
                infoPanel.hide();
                binding.timedShareFab.setVisibility(View.VISIBLE);
                if (currentCircle != null) {
                    currentCircle.remove();
                    currentCircle = null;
                }
            }
        });

        // add markers for all friends
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                DB db = DB.get();
                ArrayList<FriendRecord> friends = db.getFriends();
                for (final FriendRecord f : friends) {
                    final FriendLocation location = db.getFriendLocation(f.id);
                    if (location == null) {
                        continue;
                    }
                    addMapMarker(f, location);
                }
            }
        });
    }

    //endregion

    //region FriendshipManager results

    @WorkerThread
    private void onStartShareFinished(boolean success) {
        if (success) {
            return;
        }

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                Toast.makeText(requireContext(), R.string.toast_enable_sharing_error_msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @WorkerThread
    private void onStopShareFinished(boolean success) {
        if (success) {
            return;
        }

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                Toast.makeText(requireContext(), R.string.toast_disable_sharing_error_msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @WorkerThread
    private void onRemoveFriendFinished(boolean success) {
        if (success) {
            return;
        }

        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (binding != null) {
                    Utils.showAlert(requireContext(), R.string.unexpected_error, R.string.remove_friend_error_msg, getParentFragmentManager());
                }
            }
        });
    }

    @UiThread
    private void removeFriendFromMap(long friendId) {
        Marker marker = markerTracker.removeMarker(friendId);
        if (marker != null) {
            marker.remove();
        }
        if (currentCircle != null) {
            Long circleFriendId = (Long) currentCircle.getTag();
            if (circleFriendId != null) {
                if (circleFriendId == friendId) {
                    currentCircle.remove();
                }
            }
        }
    }

    @UiThread
    private void updateMarker(@NonNull FriendLocation loc) {
        // check if we already have a marker for this friend
        Marker marker = markerTracker.getById(loc.friendId);
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
            markerTracker.updateLocation(loc.friendId, loc);

            // if there is an error circle being shown on this marker, adjust it too
            if (currentCircle != null) {
                Long friendId = (Long) currentCircle.getTag();
                if (friendId != null && loc.friendId == friendId) {
                    if (loc.accuracy != null) {
                        currentCircle.setRadius(loc.accuracy);
                        currentCircle.setCenter(new LatLng(loc.latitude, loc.longitude));
                    } else {
                        // no accuracy, so remove the circle
                        currentCircle.remove();
                        currentCircle = null;
                    }
                }
            }
        }
    }

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
                    String errMsg = OscarClient.queueSendMessage(OscarClient.getQueue(requireContext()), friend.user, keyPair, accessToken, comm.toJSON(), true, true);
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
                            friendshipManager.removeFriend(friend, MainFragment.this::onRemoveFriendFinished);
                        }
                    });
                }
            });
            dialog.setButton2(getString(R.string.cancel), null);
            dialog.show(getParentFragmentManager(), null);
        }

        @Override
        public void onInfoPanelShareToggled(@NonNull FriendRecord friend, boolean shouldShare) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    if (shouldShare) {
                        friendshipManager.startSharingWith(friend, MainFragment.this::onStartShareFinished);
                    } else {
                        friendshipManager.stopSharingWith(friend, MainFragment.this::onStopShareFinished);
                    }
                }
            });
        }

        @Override
        public void onInfoPanelViewSafetyNumber(@NonNull FriendRecord friend) {
            Prefs prefs = Prefs.get(requireContext());
            String username = prefs.getUsername();
            SafetyNumberFragment fragment = SafetyNumberFragment.newInstance(username,
                    keyPair.publicKey,
                    friend.user.username,
                    friend.user.publicKey);
            FragmentManager mgr = getParentFragmentManager();
            mgr.beginTransaction()
                    .setCustomAnimations(R.animator.new_enter_from_right,
                            R.animator.new_exit_to_left,
                            R.animator.new_enter_from_left,
                            R.animator.new_exit_to_right)
                    .replace(R.id.fragment_host, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    };

    //endregion

    //region GoogleMap.OnMarkerClickListener

    private final GoogleMap.OnMarkerClickListener markerClickListener = new GoogleMap.OnMarkerClickListener() {
        @Override
        @UiThread
        public boolean onMarkerClick(@NonNull final Marker marker) {
            if (marker.equals(meMarker)) {
                infoPanel.hide();
                binding.timedShareFab.setVisibility(View.VISIBLE);
                return true;
            }

            final FriendLocation loc = markerTracker.getLocation(marker);
            if (loc == null) {
                // should never happen
                infoPanel.hide();
                binding.timedShareFab.setVisibility(View.VISIBLE);
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

            if (googMap != null) {
                CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
                googMap.animateCamera(update);
            }

            showFriendErrorCircle(loc);
            return true;
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

        if (friendForCameraToTrack == loc.friendId && googMap != null) {
            CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
            googMap.animateCamera(update);
        }
    }

    @Override
    public void onFriendRemoved(long friendId) {
        removeFriendFromMap(friendId);
        if (infoPanel == null) {
            return;
        }
        // if the info panel is showing their info, hide it
        if (infoPanel.isHidden()) {
            return;
        }
        FriendRecord friend = infoPanel.getFriend();
        if (friend != null && friend.id == friendId) {
            infoPanel.hide();
            friendForCameraToTrack = -1;
            binding.timedShareFab.setVisibility(View.VISIBLE);
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
        Intent i = WelcomeActivity.newIntent(requireContext());
        startActivity(i);
        requireActivity().finish();
    }

    //endregion

    //region AvatarManager.Listener

    private final AvatarManager.Listener avatarListener = new AvatarManager.Listener() {
        @Override
        public void onAvatarUpdated(@Nullable String username) {
            if (username == null) {
                return;
            }
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
                    BitmapDescriptor icon = AvatarRenderer.getBitmapDescriptor(requireContext(), username, R.dimen.thirtySix);
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            Marker marker = markerTracker.getById(friend.id);
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
            if (code == OscarSocket.CLOSE_CODE_INVALID_TOKEN) {
                Context ctx = getContext();
                if (ctx != null) {
                    AuthenticationManager.get().logOut(ctx, null);
                }
                return;
            }
            if (!isVisible()) {
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
            if (!isVisible()) {
                return;
            }
            String token = Prefs.get(App.getApp().getApplicationContext()).getAccessToken();
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
            MessageProcessor.Result result = MessageProcessor.decryptAndProcess(App.getApp().getApplicationContext(), friend.user.userId, data.cipherText, data.nonce);
            if (result != MessageProcessor.Result.Success) {
                L.i("MF error decrypting+processing dropped package: " + result);
            }
        }

        @Override
        public void onPushNotificationReceived(@NonNull PushNotification notification) {
            L.i("MF.onPushNotificationReceived");
            Context ctx = App.getApp().getApplicationContext();
            MessageProcessor.Result result = MessageProcessor.decryptAndProcess(ctx,
                    notification.senderId,
                    notification.cipherText,
                    notification.nonce);
            if (result != MessageProcessor.Result.Success) {
                L.i("MF error decrypting+processing push notification: " + result);
            }

            // Was this a transient message?
            if (notification.id == null || notification.id.equals("0")) {
                return;
            }

            // Wasn't transient, so let's delete it from the server
            long msgId;
            try {
                msgId = Long.parseLong(notification.id);
                if (msgId == 0) { // technically, a redundant check
                    return;
                }
            } catch (NumberFormatException ex) {
                // something is up on the server
                L.w("failed to parse push message id", ex);
                return;
            }

            String token = Prefs.get(ctx).getAccessToken();
            if (token == null) {
                return;
            }
            OscarClient.queueDeleteMessage(ctx, token, msgId);
        }
    };

    //endregion

    private final LocationCallback mLocationCallbackHelper = new LocationCallback() {
        @Override
        @UiThread
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (meMarker == null) {
                addMyLocation(location);
            } else {
                LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
                ValueAnimator posAnimator = ObjectAnimator.ofObject(meMarker,
                        "position",
                        new LatLngEvaluator(),
                        meMarker.getPosition(),
                        ll);
                posAnimator.setDuration(500);

                posAnimator.start();
            }

            if (myCircle == null) {
                addMyErrorCircle(location);
            } else {
                if (!location.hasAccuracy()) {
                    myCircle.remove();
                    myCircle = null;
                } else {
                    ValueAnimator posAnimator = ObjectAnimator.ofObject(myCircle,
                            "center",
                            new LatLngEvaluator(),
                            myCircle.getCenter(),
                            new LatLng(location.getLatitude(), location.getLongitude()));
                    posAnimator.setDuration(500);
                    ValueAnimator errAnimator = ObjectAnimator.ofObject(myCircle,
                            "radius",
                            new DoubleEvaluator(),
                            myCircle.getRadius(),
                            (double)location.getAccuracy());
                    errAnimator.setDuration(500);

                    posAnimator.start();
                    errAnimator.start();
                }
            }

            if (friendForCameraToTrack == 0) {
                if (googMap != null && !isFlyingCameraToMyLocation) {
                    CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
                    googMap.animateCamera(update);
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

    private final UpdateStatusTracker.Listener updateStatusTrackerListener = new UpdateStatusTracker.Listener() {
        @Override
        public void onUpdateStatusChanged(long friendId) {
            if (infoPanel == null) {
                return;
            }

            if (infoPanel.getFriendId() == friendId) {
                infoPanel.updateRefreshProgressBarState(friendId);
            }
        }
    };

    //region BackPressInterceptor

    @Override
    public boolean onBackPressed() {
        if (viewModel == null) {
            return false;
        }

        if (viewModel.isTimedShareSheetDismissable()) {
            viewModel.onCloseTimedSheetAction();
            return true;
        }

        return false;
    }

    //endregion
}
