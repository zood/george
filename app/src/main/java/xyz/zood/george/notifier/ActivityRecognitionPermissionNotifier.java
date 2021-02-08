package xyz.zood.george.notifier;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
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
public class ActivityRecognitionPermissionNotifier implements LifecycleObserver {

    @NonNull private final BannerView banner;
    @NonNull private final FragmentActivity activity;
    @NonNull private final Fragment fragment;
    private static final int bannerItemId = 11174;
    private final int activityRecognitionResultCode;

    public ActivityRecognitionPermissionNotifier(@NonNull FragmentActivity activity, @NonNull Fragment fragment, @NonNull BannerView banner, int resultCode) {
        this.banner = banner;
        this.activity = activity;
        this.fragment = fragment;
        this.activityRecognitionResultCode = resultCode;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        if (Permissions.checkGrantedActivityRecognitionPermission(activity)) {
            hide();
        }
    }

    private void hide() {
        banner.removeItem(bannerItemId);
    }

    private void onBannerItemClicked() {
        ZoodDialog dialog = ZoodDialog.newInstance(activity.getString(R.string.activity_recognition_permission_reason_msg));
        dialog.setTitle(activity.getString(R.string.permission_request));
        dialog.setButton1(activity.getString(R.string.fix), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPermissionRequest();
            }
        });
        dialog.setButton2(activity.getString(R.string.ignore), null);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    public void show() {
        String msg = activity.getString(R.string.activity_recognition_permission_needed);
        String action = activity.getString(R.string.fix);
        banner.addItem(msg, action, bannerItemId, new BannerView.ItemClickListener() {
            @Override
            public void onBannerItemClick(int id) {
                onBannerItemClicked();
            }
        });
    }

    private void showPermissionRequest() {
        // If Android says we should show a rationale, that means we can still bring up the
        // normal request dialog. Otherwise, we have to redirect the user to settings.
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)) {
            fragment.requestPermissions(Permissions.getBackgroundLocationPermissions(), activityRecognitionResultCode);
        } else {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            i.setData(uri);
            activity.startActivity(i);
        }
    }
}
