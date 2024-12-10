package xyz.zood.george.notifier;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import xyz.zood.george.Permissions;
import xyz.zood.george.R;
import xyz.zood.george.widget.BannerView;
import xyz.zood.george.widget.ZoodDialog;

public class BackgroundLocationPermissionNotifier implements DefaultLifecycleObserver {

    @NonNull private final BannerView banner;
    @NonNull private final FragmentActivity activity;
    private static final int bannerItemId = 871;

    public BackgroundLocationPermissionNotifier(@NonNull FragmentActivity activity, @NonNull BannerView banner) {
        this.banner = banner;
        this.activity = activity;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (Permissions.checkBackgroundLocationPermission(activity)) {
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
        @StringRes int msgId = R.string.grant_background_location_permission_via_settings_msg;
        ZoodDialog dialog = ZoodDialog.newInstance(activity.getString(msgId));
        dialog.setTitle(activity.getString(R.string.background_permission));
        dialog.setButton1(activity.getString(R.string.fix), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppSystemSettings();
            }
        });
        dialog.setButton2(activity.getString(R.string.ignore), null);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private void showAppSystemSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        i.setData(uri);
        activity.startActivity(i);
    }

    public void show() {
        banner.addItem(activity.getString(R.string.background_location_permission_needed), activity.getString(R.string.fix), bannerItemId, new BannerView.ItemClickListener() {
            @Override
            public void onBannerItemClick(int id) {
                onBannerItemClicked();
            }
        });
    }

}
