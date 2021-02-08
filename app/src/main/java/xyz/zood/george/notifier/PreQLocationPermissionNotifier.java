package xyz.zood.george.notifier;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
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

public class PreQLocationPermissionNotifier implements LifecycleObserver {

    @NonNull private final BannerView banner;
    @NonNull private final FragmentActivity activity;
    @NonNull private final Fragment fragment;
    private static final int bannerItemId = 9495;
    private final int preQLocationResultCode;

    public PreQLocationPermissionNotifier(@NonNull FragmentActivity activity, @NonNull Fragment fragment, @NonNull BannerView banner, int preQLocResultCode) {
        this.banner = banner;
        this.activity = activity;
        this.fragment = fragment;
        this.preQLocationResultCode = preQLocResultCode;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            hide();
            return;
        }

        if (Permissions.checkGrantedPreQLocationPermission(activity)) {
            hide();
        }
    }

    private void hide() {
        banner.removeItem(bannerItemId);
    }

    private void onBannerItemClicked() {
        @StringRes int msgId;
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            msgId = R.string.grant_pre_q_location_permission_via_settings_msg;
        } else {
            msgId = R.string.grant_pre_q_location_permission_msg;
        }
        ZoodDialog dialog = ZoodDialog.newInstance(activity.getString(msgId));
        dialog.setTitle(activity.getString(R.string.grant_location_permission));
        dialog.setButton1(activity.getString(R.string.fix), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPermissionRequest();
            }
        });
        dialog.setButton2(activity.getString(R.string.ignore), null);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    public void show() {
        banner.addItem(activity.getString(R.string.location_permission_needed), activity.getString(R.string.fix), bannerItemId, new BannerView.ItemClickListener() {
            @Override
            public void onBannerItemClick(int id) {
                onBannerItemClicked();
            }
        });
    }

    private void showPermissionRequest() {
        // If Android says we should show a rationale, that means we can still bring up the
        // normal request dialog. Otherwise, we have to redirect the user to settings.
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            fragment.requestPermissions(Permissions.getPreQLocationPermissions(), preQLocationResultCode);
        } else {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            i.setData(uri);
            activity.startActivity(i);
        }
    }
}
