package xyz.zood.george.notifier;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import io.pijun.george.network.Network;
import xyz.zood.george.R;
import xyz.zood.george.widget.BannerView;
import xyz.zood.george.widget.ZoodDialog;

public class BackgroundDataRestrictionNotifier implements DefaultLifecycleObserver {

    @NonNull private final BannerView banner;
    @NonNull private final FragmentActivity activity;
    private static final int bannerItemId = 2134;

    public BackgroundDataRestrictionNotifier(@NonNull FragmentActivity activity, @NonNull BannerView banner) {
        this.banner = banner;
        this.activity = activity;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
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
        dialog.setButton1(activity.getString(R.string.system_settings), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent();
                i.setAction(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                i.setData(uri);
                activity.startActivity(i);
            }
        });
        dialog.setButton2(activity.getString(R.string.ignore), null);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

}
