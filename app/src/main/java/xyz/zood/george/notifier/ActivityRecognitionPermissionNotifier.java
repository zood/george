package xyz.zood.george.notifier;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import xyz.zood.george.MainFragment;
import xyz.zood.george.Permissions;
import xyz.zood.george.R;
import xyz.zood.george.widget.BannerView;
import xyz.zood.george.widget.ZoodDialog;

public class ActivityRecognitionPermissionNotifier implements DefaultLifecycleObserver {

    @NonNull private final BannerView banner;
    @NonNull private final FragmentActivity activity;
    @NonNull private final MainFragment fragment;
    private static final int bannerItemId = 11174;

    public ActivityRecognitionPermissionNotifier(@NonNull FragmentActivity activity, @NonNull MainFragment fragment, @NonNull BannerView banner) {
        this.banner = banner;
        this.activity = activity;
        this.fragment = fragment;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (Permissions.checkActivityRecognitionPermission(activity)) {
            hide();
        }
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (Permissions.checkActivityRecognitionPermission(activity)) {
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
            fragment.activityRecognitionPermLauncher.launch(Permissions.getActivityRecognitionPermissions());
        } else {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            i.setData(uri);
            activity.startActivity(i);
        }
    }
}
