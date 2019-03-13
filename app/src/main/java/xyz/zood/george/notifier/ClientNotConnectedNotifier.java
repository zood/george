package xyz.zood.george.notifier;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;

import io.pijun.george.Utils;
import xyz.zood.george.R;
import xyz.zood.george.widget.BannerView;

public class ClientNotConnectedNotifier implements BannerView.ItemClickListener {

    @NonNull
    private final BannerView banner;
    @NonNull private final AppCompatActivity activity;
    private static final int bannerItemId = 110348;

    public ClientNotConnectedNotifier(@NonNull AppCompatActivity activity, @NonNull BannerView banner) {
        this.banner = banner;
        this.activity = activity;
    }

    @UiThread
    public void hide() {
        banner.removeItem(bannerItemId);
    }

    @Override
    public void onBannerItemClick(int id) {
        Utils.showAlert(activity, 0, R.string.unable_to_connect_msg, activity.getSupportFragmentManager());
    }

    @UiThread
    public void show() {
        banner.addItem(activity.getString(R.string.unable_to_connect_to_zood_servers), activity.getString(R.string.info), bannerItemId, this);
    }
}
