package xyz.zood.george.notifier;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import xyz.zood.george.Permissions;
import xyz.zood.george.R;
import xyz.zood.george.widget.BannerView;
import xyz.zood.george.widget.ZoodDialog;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class ForegroundLocationPermissionNotifier implements LifecycleObserver {

    @NonNull private final BannerView banner;
    @NonNull private final FragmentActivity activity;
    @NonNull private final Fragment fragment;
    private static final int bannerItemId = 5811;
    private final int foregroundLocationResultCode;

    public ForegroundLocationPermissionNotifier(@NonNull FragmentActivity activity, @NonNull Fragment fragment, @NonNull BannerView banner, int fgLocResultCode) {
        this.banner = banner;
        this.activity = activity;
        this.fragment = fragment;
        this.foregroundLocationResultCode = fgLocResultCode;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        if (Permissions.checkGrantedForegroundLocationPermission(activity)) {
            hide();
        }
        // not having the permission doesn't definitively mean we need to show the banner item.
        // There is no 'not asked' state for permissions, so we wait for MapActivity to explicitly
        // call show() if the user rejects the permission.
    }

    private void hide() {
        banner.removeItem(bannerItemId);
    }

    private void onBannerItemClicked() {
        // Select the message based on the screen to which we'll be sending the user
        @StringRes int msgId;
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            msgId = R.string.grant_foreground_location_permission_msg;
        } else {
            msgId = R.string.grant_foreground_location_permission_via_settings_msg;
        }
        ZoodDialog dialog = ZoodDialog.newInstance(activity.getString(msgId));
        dialog.setTitle(activity.getString(R.string.location_permission));
        dialog.setButton1(activity.getString(R.string.fix), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPermissionRequest();
            }
        });
        dialog.setButton2(activity.getString(R.string.ignore), null);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private void showPermissionRequest() {
        // If Android says we should show a rationale, that means we can still bring up the
        // normal request dialog. Otherwise, we have to redirect the user to settings.
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            fragment.requestPermissions(Permissions.getForegroundLocationPermissions(), foregroundLocationResultCode);
        } else {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            i.setData(uri);
            activity.startActivity(i);
        }
    }

    public void show() {
        banner.addItem(activity.getString(R.string.location_permission_needed), activity.getString(R.string.fix), bannerItemId, new BannerView.ItemClickListener() {
            @Override
            public void onBannerItemClick(int id) {
                onBannerItemClicked();
            }
        });
    }
}
