package xyz.zood.george.widget;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import io.pijun.george.R;
import io.pijun.george.network.Network;

public class BackgroundDataRestrictionNotifier implements LifecycleObserver {

    @NonNull private final BannerView banner;
    @NonNull private final AppCompatActivity activity;
    private static final int bannerItemId = 2134;

    public BackgroundDataRestrictionNotifier(@NonNull AppCompatActivity activity, @NonNull BannerView banner) {
        this.banner = banner;
        this.activity = activity;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        if (Network.isBackgroundDataRestricted(activity)) {
            banner.addItem(activity.getString(R.string.background_data_is_restricted), activity.getString(R.string.fix), bannerItemId, new BannerView.ItemClickListener() {
                @Override
                public void onBannerItemClick(int id) {
                    onBannerItemClicked();
                }
            });
        } else {
            banner.removeItem(bannerItemId);
        }
    }

    private void onBannerItemClicked() {
        ZoodDialog dialog = ZoodDialog.newInstance(activity.getString(R.string.background_data_restricted_msg));
        dialog.setTitle(activity.getString(R.string.data_restricted));
        dialog.setButton1(activity.getString(R.string.zood_settings), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent();
                i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                i.setData(uri);
                activity.startActivity(i);
            }
        });
        dialog.setButton2(activity.getString(R.string.ignore), null);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

}