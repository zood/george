package xyz.zood.george;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.app.ActivityCompat;
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
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdate;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.plugins.annotation.OnSymbolClickListener;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.style.sources.GeoJsonOptions;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import io.pijun.george.App;
import io.pijun.george.AuthenticationManager;
import io.pijun.george.Constants;
import io.pijun.george.L;
import io.pijun.george.LocationUtils;
import io.pijun.george.MessageProcessor;
import io.pijun.george.Prefs;
import io.pijun.george.SettingsFragment;
import io.pijun.george.UiRunnable;
import io.pijun.george.UpdateStatusTracker;
import io.pijun.george.Utils;
import io.pijun.george.WelcomeActivity;
import io.pijun.george.WorkerRunnable;
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
import xyz.zood.george.databinding.FragmentMainBinding;
import xyz.zood.george.notifier.ActivityRecognitionPermissionNotifier;
import xyz.zood.george.notifier.BackgroundDataRestrictionNotifier;
import xyz.zood.george.notifier.BackgroundLocationPermissionNotifier;
import xyz.zood.george.notifier.ClientNotConnectedNotifier;
import xyz.zood.george.notifier.ForegroundLocationPermissionNotifier;
import xyz.zood.george.receiver.UserActivityReceiver;
import xyz.zood.george.viewmodels.Event;
import xyz.zood.george.viewmodels.MainViewModel;
import xyz.zood.george.widget.InfoPanel;
import xyz.zood.george.widget.ZoodDialog;

public class MainFragment extends Fragment implements OnMapReadyCallback, DB.Listener, AuthenticationManager.Listener, BackPressInterceptor, OnSymbolClickListener {

    static final String TAG = "main";

    private static final String ARG_ACCESS_TOKEN = "access_token";
    private static final String ARG_KEY_PAIR = "key_pair";

    private String accessToken;
    private FragmentMainBinding binding;
    private long friendForCameraToTrack = -1;
    private FriendshipManager friendshipManager;
    private MapLibreMap mlMap;
    private InfoPanel infoPanel;
    private boolean isFlyingCameraToMyLocation = false;
    private KeyPair keyPair;
    private ActivityRecognitionPermissionNotifier userActivityPermissionNotifier;
    private BackgroundLocationPermissionNotifier bgLocationPermissionNotifier;
    private ForegroundLocationPermissionNotifier fgLocationPermissionNotifier;
    public ActivityResultLauncher<String[]> fgLocationPermLauncher;
    public ActivityResultLauncher<String[]> bgLocationPermLauncher;
    public ActivityResultLauncher<String[]> bgLocationPermToAddFriendLauncher;
    public ActivityResultLauncher<String[]> activityRecognitionPermLauncher;
    private FusedLocationProviderClient locationProviderClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    @Nullable private FriendSymbolTracker symbolTracker;
    private MyLocationSymbol myLocationSymbol;
    private ClientNotConnectedNotifier notConnectedNotifier;
    private OscarSocket oscarSocket;
    private SettingsClient settingsClient;
    private SymbolManager symbolManager;
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
        MapLibre.getInstance(ctx);
        friendshipManager = new FriendshipManager(ctx, DB.get(), OscarClient.getQueue(ctx), accessToken, keyPair);

        fgLocationPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<>() {
            @Override
            public void onActivityResult(Map<String, Boolean> grants) {
                if (Permissions.checkForegroundLocationPermission(ctx)) {
                    foregroundLocationPermissionGranted();
                } else {
                    fgLocationPermissionNotifier.show();
                }
            }
        });
        bgLocationPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<>() {
            @Override
            public void onActivityResult(Map<String, Boolean> o) {
                if (Permissions.checkBackgroundLocationPermission(ctx)) {
                    backgroundLocationPermissionGranted();
                } else {
                    bgLocationPermissionNotifier.show();
                }
            }
        });
        bgLocationPermToAddFriendLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<>() {
            @Override
            public void onActivityResult(Map<String, Boolean> o) {
                if (Permissions.checkBackgroundLocationPermission(ctx)) {
                    // Just show the add friend dialog - the other permission requests can
                    // happen when they come back to the screen, after they have added their friend
                    onAddFriendClicked();
                }
            }
        });
        activityRecognitionPermLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<>() {
            @Override
            public void onActivityResult(Map<String, Boolean> o) {
                if (Permissions.checkActivityRecognitionPermission(ctx)) {
                    activityRecognitionPermissionGranted();
                } else {
                    userActivityPermissionNotifier.show();
                }
            }
        });

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext());
        settingsClient = LocationServices.getSettingsClient(ctx);
        locationRequest = new LocationRequest.Builder(3*DateUtils.SECOND_IN_MILLIS).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();

        DB.get().addListener(this);
        AuthenticationManager.get().addListener(this);

        getLifecycle().addObserver(new AppInForegroundObserver());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false);

        infoPanel = new InfoPanel(binding.infoPanel, requireContext(), infoPanelListener);
        binding.map.getMapAsync(this);
        binding.map.onCreate(savedInstanceState);

        viewModel.getSelectedFriend().observe(getViewLifecycleOwner(), new Observer<>() {
            @Override
            public void onChanged(FriendRecord friend) {
                viewModel.onCloseTimedSheetAction();
                onFriendSelected(friend);
            }
        });
        viewModel.getOnAddFriendClicked().observe(getViewLifecycleOwner(), new Observer<>() {
            @Override
            public void onChanged(Event<Boolean> evt) {
                Boolean clicked = evt.getEventIfNotHandled();
                if (clicked != null) {
                    onAddFriendClicked();
                }
            }
        });
        viewModel.getTimedShareIsRunning().observe(getViewLifecycleOwner(), new Observer<>() {
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

        getLifecycle().addObserver(new MapLibreMapLifecycleObserver(binding.map));
        getLifecycle().addObserver(new BackgroundDataRestrictionNotifier(requireActivity(), binding.banners));
        bgLocationPermissionNotifier = new BackgroundLocationPermissionNotifier(requireActivity(), binding.banners);
        getLifecycle().addObserver(bgLocationPermissionNotifier);
        fgLocationPermissionNotifier = new ForegroundLocationPermissionNotifier(requireActivity(), this, binding.banners);
        getLifecycle().addObserver(fgLocationPermissionNotifier);
        userActivityPermissionNotifier = new ActivityRecognitionPermissionNotifier(requireActivity(), this, binding.banners);
        getLifecycle().addObserver(userActivityPermissionNotifier);
        notConnectedNotifier = new ClientNotConnectedNotifier(requireActivity(), binding.banners);

        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        AuthenticationManager.get().removeListener(this);

        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        DB.get().removeListener(this);
        AvatarManager.removeListener(avatarListener);
        symbolTracker = null;
        mlMap = null;
        myLocationSymbol = null;
        infoPanel = null;
        binding = null;

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

        checkPermissions();

        // If the device has an FCM token, upload it
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful() && task.getException() != null) {
                    L.w("MainFragment.start failed to obtain FCM registration token", task.getException());
                    return;
                }

                OscarClient.queueAddFcmToken(ctx, accessToken, task.getResult());
            }
        });

        UpdateStatusTracker.addListener(updateStatusTrackerListener);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                oscarSocket = new OscarSocket(oscarSocketListener);
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
        if (mlMap != null) {
            Prefs.get(requireContext()).setCameraPosition(mlMap.getCameraPosition());
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
        if (mlMap == null) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        Bitmap icon = AvatarRenderer.getBitmap(ctx, friend.user.username, R.dimen.thirtySix);
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                if (symbolTracker == null) {
                    return;
                }
                symbolTracker.put(requireContext(), icon, friend, loc);
            }
        });
    }

    private void flyCameraToMyLocation() {
        if (mlMap == null || myLocationSymbol == null) {
            return;
        }

        LatLng ll = myLocationSymbol.getLatLng();
        if (ll == null) {
            return;
        }

        double zoom = Math.max(mlMap.getCameraPosition().zoom, Constants.DEFAULT_ZOOM_LEVEL);
        var cp = new CameraPosition.Builder()
                .zoom(zoom)
                .target(ll)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        isFlyingCameraToMyLocation = true;
        mlMap.animateCamera(cu, new MapLibreMap.CancelableCallback() {
            @Override
            public void onCancel() {
                isFlyingCameraToMyLocation = false;
            }

            @Override
            public void onFinish() {
                isFlyingCameraToMyLocation = false;
            }
        });
    }

    private void onFriendSelected(@NonNull FriendRecord fr) {
        if (symbolTracker == null) {
            return;
        }

        var fs = symbolTracker.get(fr.id);
        if (fs == null) {
            showInfoPanel(fr, null);
            return;
        }

        // Is the info panel already showing for this user? If so, just center the camera and follow
        if (infoPanel.getFriendId() == fr.id) {
            friendForCameraToTrack = fr.id;
            var update = CameraUpdateFactory.newLatLng(fs.getLatLng());
            mlMap.animateCamera(update);
            return;
        }

        // the camera should track this friend
        friendForCameraToTrack = fr.id;
        binding.myLocationFab.setSelected(false);
        var zoom = Math.max(mlMap.getCameraPosition().zoom, Constants.DEFAULT_ZOOM_LEVEL);
        CameraPosition cp = new CameraPosition.Builder()
                .target(fs.getLatLng())
                .zoom(zoom)
                .bearing(0)
                .tilt(0).build();
        var cu = CameraUpdateFactory.newCameraPosition(cp);
        mlMap.animateCamera(cu);

        var loc = fs.getLocation();
        showInfoPanel(fr, loc);
        fs.setErrorCircleVisible(true);
    }

    @UiThread
    private void onAddFriendClicked() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        // If the user hasn't granted background location permission, we need
        if (!Permissions.checkBackgroundLocationPermission(ctx)) {
            obtainBackgroundLocationPermission(true);
            return;
        }

        // We have to delay the presentation of the add friend screen because if we do it right
        // away it sometimes shows up as a black screen on some devices when the user is returning
        // granting background permission.
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                Context ctx = getContext();
                if (ctx == null) {
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
        });

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

    @UiThread
    private void backgroundLocationPermissionGranted() {
        verifyLocationSettingsResolution();

        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                ArrayList<FriendRecord> friends = DB.get().getFriends();
                if (friends.isEmpty()) {
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

    @UiThread
    private void checkActivityRecognitionPermission() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        if (Permissions.checkActivityRecognitionPermission(ctx)) {
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
                    activityRecognitionPermLauncher.launch(Permissions.getActivityRecognitionPermissions());
                }
            });
            dialog.setCancelable(false);
            dialog.show(getParentFragmentManager(), null);
            viewModel.setActivityRecognitionRationaleVisible(true);
        } else {
            activityRecognitionPermLauncher.launch(Permissions.getActivityRecognitionPermissions());
        }
    }

    @UiThread
    private void obtainBackgroundLocationPermission(boolean fromAddFriendClick) {
        Context ctx = getContext();
        if (ctx == null) {
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
                    if (fromAddFriendClick) {
                        bgLocationPermToAddFriendLauncher.launch(Permissions.getBackgroundLocationPermissions());
                    } else {
                        bgLocationPermLauncher.launch(Permissions.getBackgroundLocationPermissions());
                    }
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
            bgLocationPermLauncher.launch(Permissions.getBackgroundLocationPermissions());
        }
    }

    private void checkPermissions() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        if (Permissions.checkForegroundLocationPermission(ctx)) {
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
        String[] perms = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            ZoodDialog dialog = ZoodDialog.newInstance(getString(R.string.foreground_location_permission_reason_msg));
            dialog.setTitle(getString(R.string.permission_request));
            dialog.setButton1(getString(R.string.ok), new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    viewModel.setForegroundLocationRationaleVisible(false);
                    fgLocationPermLauncher.launch(perms);
                }
            });
            // In order to track when the dialog is dismissed, don't allow cancelling
            dialog.setCancelable(false);
            dialog.show(getParentFragmentManager(), null);
            viewModel.setForegroundLocationRationaleVisible(true);
        } else {
            fgLocationPermLauncher.launch(perms);
        }
    }

    @UiThread
    private void foregroundLocationPermissionGranted() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        // Start requesting location updates. The permission check is redundant, but we do it to quiet the linter
        if (Permissions.checkForegroundLocationPermission(ctx)) {
            locationProviderClient.requestLocationUpdates(locationRequest, mLocationCallbackHelper, Looper.getMainLooper());
        }

        // If we have at least 1 friend, then we need permission for background location
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                ArrayList<FriendRecord> friends = DB.get().getFriends();
                if (friends.isEmpty()) {
                    return;
                }

                if (Permissions.checkBackgroundLocationPermission(ctx)) {
                    return;
                }

                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        obtainBackgroundLocationPermission(false);
                    }
                });
            }
        });
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
                                rae.startResolutionForResult(activity, 20); // we don't care about the request code
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

    //endregion

    //region OnMapReadyCallback

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mlMap = map;
        map.setStyle("https://www.zood.xyz/static/map-style.json", style -> {
            GeoJsonOptions gjOpts = new GeoJsonOptions().withTolerance(0.05f);
            this.symbolManager = new SymbolManager(binding.map, mlMap, style, null, null, gjOpts);
            symbolManager.addClickListener(this);
            symbolManager.setIconAllowOverlap(true);
            symbolManager.setTextAllowOverlap(true);
            symbolTracker = new FriendSymbolTracker(symbolManager, map, style);
            myLocationSymbol = new MyLocationSymbol(symbolManager, map, style);

            /*
             * We add the listener here to make sure this listener is fired AFTER the
             * symbolManager's click listener. That way we can consume the clicks on symbol's first
             * before the event is propagated to the map click listener. If we don't control the
             * order we add the click listener, then it would be possible for the OnMapClickListener
             * to receive events first and THEN the symbol manager's click listener. That ends up
             * causing weird UI bugs. Hopefully a future version of the SDK will only send events to
             * the most specific items first and we won't need to rely on this hack anymore.
             * https://github.com/mapbox/mapbox-gl-native/issues/15718#issuecomment-983202888
             */
            map.addOnMapClickListener(new MapLibreMap.OnMapClickListener() {
                @Override
                public boolean onMapClick(@NonNull LatLng latLng) {
                    friendForCameraToTrack = -1;
                    binding.myLocationFab.setSelected(false);
                    infoPanel.hide();
                    binding.timedShareFab.setVisibility(View.VISIBLE);
                    symbolTracker.hideErrorCircle();

                    return false; // don't intercept the event
                }
            });
        });
        var pos = Prefs.get(requireContext()).getCameraPosition();
        if (pos != null) {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
        map.addOnFlingListener(new MapLibreMap.OnFlingListener() {
            @Override
            public void onFling() {
                if (isFlyingCameraToMyLocation) {
                    return;
                }
                friendForCameraToTrack = -1;
                binding.myLocationFab.setSelected(false);
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
        if (symbolTracker == null) {
            return;
        }

        symbolTracker.removeFriend(friendId);
    }

    @UiThread
    private void updateFriendSymbolLocation(@NonNull FriendLocation loc) {
        if (symbolTracker == null) {
            return;
        }
        // check if we already have a symbol for this friend
        FriendSymbol fs = symbolTracker.get(loc.friendId);
        if (fs == null) {
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
        } else {
            fs.updateLocation(loc);
        }
    }

    //endregion

    // region OnSymbolClickListener

    @Override
    public boolean onAnnotationClick(Symbol symbol) {
        L.i("onAnnotationClick");
        if (symbol.equals(myLocationSymbol.getSymbol())) {
            infoPanel.hide();
            binding.timedShareFab.setVisibility(View.VISIBLE);
            return true;
        }

        if (symbolTracker == null) {
            return true;
        }

        var fs = symbolTracker.get(symbol);
        if (fs == null) {
            // what did we click on?
            return true;
        }

        // make the camera track this friend
        friendForCameraToTrack = fs.getFriendId();

        // move the camera to the friend
        var update = CameraUpdateFactory.newLatLng(fs.getLatLng());
        mlMap.animateCamera(update);

        fs.setErrorCircleVisible(true);

        if (infoPanel.getFriendId() == fs.getFriendId()) {
            // we're already showing this friend in the info panel
            return true;
        }

        // retrieve the FriendRecord so we can show the info panel
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                var friend = DB.get().getFriendById(fs.getFriendId());
                if (friend == null) {
                    return;
                }
                App.runOnUiThread(new UiRunnable() {
                    @Override
                    public void run() {
                        showInfoPanel(friend, fs.getLocation());
                    }
                });
            }
        });


        return true;
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

        public void onInfoPanelSendLocation(@NonNull String username, @Nullable FriendLocation location) {
            if (location == null) {
                // the friend isn't sharing their location with us
                return;
            }

            Intent i = new Intent();
            i.setAction(Intent.ACTION_VIEW);
            i.setData(Uri.parse(String.format(Locale.ENGLISH, "geo:0,0?q=%f,%f(%s)", location.latitude, location.longitude, username)));
            if (i.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(i);
            }
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



    //region DB.Listener

    @Override
    public void onFriendLocationUpdated(final FriendLocation loc) {
        // If the info panel is already showing for this friend, update it
        final FriendRecord friend = infoPanel.getFriend();
        if (friend != null && friend.id == loc.friendId) {
            showInfoPanel(friend, loc);
        }

        // also update the position of the friend's symbol
        updateFriendSymbolLocation(loc);

        // if the camera is tracking this friend, update the camera position
        if (friendForCameraToTrack == loc.friendId && mlMap != null) {
            var update = CameraUpdateFactory.newLatLng(new LatLng(loc.latitude, loc.longitude));
            mlMap.animateCamera(update);
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
                    Bitmap icon = AvatarRenderer.getBitmap(requireContext(), username, R.dimen.thirtySix);
                    App.runOnUiThread(new UiRunnable() {
                        @Override
                        public void run() {
                            if (symbolTracker == null) {
                                return;
                            }

                            FriendSymbol s = symbolTracker.get(friend.id);
                            if (s != null) {
                                s.onAvatarUpdated(icon);
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
            if (!App.isInForeground) {
                return;
            }
            String token = Prefs.get(App.getApp().getApplicationContext()).getAccessToken();
            if (token == null) {
                return;
            }
            oscarSocket = new OscarSocket(oscarSocketListener);
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
        public void onLocationResult(@NonNull LocationResult result) {
            Location location = result.getLastLocation();
            if (location == null) {
                return;
            }

            Context ctx = getContext();
            if (ctx == null || myLocationSymbol == null) {
                return;
            }
            myLocationSymbol.updateLocation(ctx, location);

            // if the camera is tracking the user, update the camera position
            if (friendForCameraToTrack == 0) {
                if (mlMap != null && !isFlyingCameraToMyLocation) {
                    var update = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
                    mlMap.animateCamera(update);
                }
            }

            // share the location with all the user's friends
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
