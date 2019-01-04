package xyz.zood.george.widget;

import android.app.Activity;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import io.pijun.george.App;
import io.pijun.george.L;
import io.pijun.george.R;
import io.pijun.george.UiRunnable;
import io.pijun.george.UpdateStatusTracker;
import io.pijun.george.api.locationiq.RevGeocoding;
import io.pijun.george.api.locationiq.ReverseGeocodingCache;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;

public class InfoPanel {

    private static final long REFRESH_INTERVAL = 15 * DateUtils.SECOND_IN_MILLIS;

    private final Activity activity;
    private final Listener listener;

    private FriendRecord currFriend;
    private FriendLocation currLoc;
    // Needs to be incremented in show() and hide() because both methods can cause a change of the
    // friend and it's location object
    private long showId = 1;

    // Views
    @NonNull private final TextView address;
    @NonNull private final TextView battery;
    @NonNull private final ImageView batteryIcon;
    @NonNull private final ImageView bearing;
    @NonNull private final ImageView motion;
    @NonNull private final ImageButton overflowButton;
    @NonNull private final ConstraintLayout panel;
    @NonNull private final Button refreshButton;
    @NonNull private final ProgressBar refreshProgressBar;
    @NonNull private final SwitchCompat shareSwitch;
    @NonNull private final TextView updateTime;
    @NonNull private final TextView username;

    @UiThread
    public InfoPanel(@NonNull ConstraintLayout root, @NonNull Activity activity, @NonNull Listener listener) {
        this.panel = root;
        this.listener = listener;
        this.activity = activity;
        panel.setClipToOutline(true);
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), activity.getResources().getDimension(R.dimen.eight));
            }
        });

        address = root.findViewById(R.id.address);
        if (address == null) {
            throw new IllegalArgumentException("'address' TextView is missing");
        }

        battery = root.findViewById(R.id.battery);
        if (battery == null) {
            throw new IllegalArgumentException("'battery' TextView is missing");
        }
        battery.setCompoundDrawablePadding((int) activity.getResources().getDimension(R.dimen.four));

        batteryIcon = root.findViewById(R.id.battery_icon);
        if (batteryIcon == null) {
            throw new IllegalArgumentException("'batteryIcon' is missing");
        }

        bearing = root.findViewById(R.id.bearing);
        if (bearing == null) {
            throw new IllegalArgumentException("'bearing' is missing");
        }

        motion = root.findViewById(R.id.motion);
        if (motion == null) {
            throw new IllegalArgumentException("'motion' is missing");
        }

        overflowButton = root.findViewById(R.id.info_overflow);
        if (overflowButton == null) {
            throw new IllegalArgumentException("'infoOverflow is missing");
        }
        overflowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInfoOverflowClicked();
            }
        });

        refreshButton = root.findViewById(R.id.refresh_button);
        if (refreshButton == null) {
            throw new IllegalArgumentException("'refreshButton is missing");
        }
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRefreshClicked();
            }
        });

        refreshProgressBar = root.findViewById(R.id.refresh_progress_bar);
        if (refreshProgressBar == null) {
            throw new IllegalArgumentException("'refreshProgressBar' is missing ");
        }

        shareSwitch = root.findViewById(R.id.share_switch);
        if (shareSwitch == null) {
            throw new IllegalArgumentException("'share_switch' SwitchCompat is missing");
        }

        updateTime = root.findViewById(R.id.update_time);
        if (updateTime == null) {
            throw new IllegalArgumentException("'updateTime' is missing");
        }

        username = root.findViewById(R.id.username);
        if (username == null) {
            throw new IllegalArgumentException("'username' TextView is missing");
        }
    }

    @UiThread
    private void calculateAndSetUpdateTime(long currTime) {
        final CharSequence relTime;
        if (currLoc.time >= currTime-60* DateUtils.SECOND_IN_MILLIS) {
            relTime = activity.getString(R.string.now);
        } else {
            relTime = DateUtils.getRelativeTimeSpanString(
                    currLoc.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
        }
        updateTime.setText(relTime);
        updateTime.setVisibility(View.VISIBLE);
    }

    @Nullable
    @UiThread
    public FriendRecord getFriend() {
        return currFriend;
    }

    @UiThread
    public long getFriendId() {
        if (currFriend != null) {
            return currFriend.id;
        }

        return -1;
    }

    @UiThread
    public void hide() {
        showId++;   // needs to be incremented here as well
        currFriend = null;
        currLoc = null;

        float xOffset = -panel.getRight();
        SpringForce xSpring = new SpringForce(xOffset)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM);
        new SpringAnimation(panel, DynamicAnimation.TRANSLATION_X)
                .setSpring(xSpring)
                .setStartVelocity(1000)
                .start();
    }

    @UiThread
    public boolean isHidden() {
        return panel.getTranslationX() != 0;
    }

    @UiThread
    private void onInfoOverflowClicked() {
        final FriendRecord friend = getFriend();
        if (friend == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(activity, overflowButton);
        activity.getMenuInflater().inflate(R.menu.info_panel_overflow, menu.getMenu());
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.remove_friend) {
                    listener.onInfoPanelRemoveFriend(friend);
                }
                return true;
            }
        });
        menu.show();
    }

    @UiThread
    private void onRefreshClicked() {
        final FriendRecord friend = getFriend();
        if (friend == null) {
            return;
        }
        listener.onInfoPanelLocationRequested(friend);
    }

    @UiThread
    public void show(@NonNull FriendRecord friend, @Nullable FriendLocation loc) {
        // If the panel is not already visible, then slide it into view
        if (isHidden()) {
            SpringForce xSpring = new SpringForce(0)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_MEDIUM);
            new SpringAnimation(panel, DynamicAnimation.TRANSLATION_X)
                    .setSpring(xSpring)
                    .setStartVelocity(1000)
                    .start();
        }

        currFriend = friend;
        currLoc = loc;
        showId++;

        // common stuff
        username.setText(friend.user.username);
        shareSwitch.setOnCheckedChangeListener(null);
        if (friend.sendingBoxId != null) {
            shareSwitch.setChecked(true);
            shareSwitch.setText(R.string.sharing);
        } else {
            shareSwitch.setChecked(false);
            shareSwitch.setText(R.string.not_sharing);
        }
        shareSwitch.setOnCheckedChangeListener(shareCheckedChangeListener);

        // If they're not sharing with us
        if (friend.receivingBoxId == null) {
            // show the empty state and get out of here
            address.setText(R.string.not_sharing_with_you);
            refreshProgressBar.setVisibility(View.INVISIBLE);
            refreshButton.setVisibility(View.INVISIBLE);
            bearing.setVisibility(View.GONE);
            motion.setVisibility(View.GONE);
            updateTime.setVisibility(View.INVISIBLE);
            battery.setVisibility(View.INVISIBLE);
            batteryIcon.setVisibility(View.INVISIBLE);
            return;
        }

        // If we don't have location info yet (from a new friend)
        if (loc == null) {
            address.setText(R.string.waiting_for_location_ellipsis);
            refreshButton.setVisibility(View.VISIBLE);
            updateTime.setVisibility(View.INVISIBLE);
            bearing.setVisibility(View.GONE);
            motion.setVisibility(View.GONE);
            battery.setVisibility(View.INVISIBLE);
            batteryIcon.setVisibility(View.INVISIBLE);
            return;
        }

        // == They are sharing with us ==
        // update time
        long now = System.currentTimeMillis();
        calculateAndSetUpdateTime(now);
        // if it's been less than 3 minutes since the last update, hide the refresh button
        if ((now - loc.time) < 3 * DateUtils.MINUTE_IN_MILLIS) {
            refreshButton.setVisibility(View.INVISIBLE);
        } else {
            refreshButton.setVisibility(View.VISIBLE);
        }

        // refresh status
        updateRefreshProgressBarState(loc.friendId);

        // friend activity info
        @DrawableRes int movement = 0;
        switch (loc.movement) {
            case Bicycle:
                movement = R.drawable.ic_sharp_bike_20dp;
                break;
            case OnFoot:
            case Running:
            case Walking:
                movement = R.drawable.ic_sharp_walk_20dp;
                break;
            case Vehicle:
                movement = R.drawable.ic_sharp_car_20dp;
                break;
            default:
                break;
        }
        if (movement != 0) {
            motion.setImageResource(movement);
            motion.setVisibility(View.VISIBLE);
        } else {
            motion.setImageBitmap(null);
            motion.setVisibility(View.GONE);
        }

        // friend bearing
        if (loc.bearing != null) {
            bearing.setVisibility(View.VISIBLE);
            bearing.setRotation(loc.bearing);
        } else {
            bearing.setVisibility(View.GONE);
        }

        // battery status
        if (loc.batteryLevel != null) {
            battery.setText(activity.getString(R.string.number_percent_msg, loc.batteryLevel));
            @DrawableRes int batteryImg;
            if (loc.batteryCharging != null && loc.batteryCharging) {
                if (loc.batteryLevel >= 95) {
                    batteryImg = R.drawable.ic_sharp_battery_charging_full_20dp;
                } else if (loc.batteryLevel >= 85) {
                    batteryImg = R.drawable.ic_sharp_battery_charging_90_20dp;
                } else if (loc.batteryLevel >= 70) {
                    batteryImg = R.drawable.ic_sharp_battery_charging_80_20dp;
                } else if (loc.batteryLevel >= 55) {
                    batteryImg = R.drawable.ic_sharp_battery_charging_60_20dp;
                } else if (loc.batteryLevel >= 40) {
                    batteryImg = R.drawable.ic_sharp_battery_charging_50_20dp;
                } else if (loc.batteryLevel >= 25) {
                    batteryImg = R.drawable.ic_sharp_battery_charging_30_20dp;
                } else {
                    batteryImg = R.drawable.ic_sharp_battery_charging_20_20dp;
                }
            } else {
                if (loc.batteryLevel >= 95) {
                    batteryImg = R.drawable.ic_sharp_battery_full_20dp;
                } else if (loc.batteryLevel >= 85) {
                    batteryImg = R.drawable.ic_sharp_battery_90_20dp;
                } else if (loc.batteryLevel >= 70) {
                    batteryImg = R.drawable.ic_sharp_battery_80_20dp;
                } else if (loc.batteryLevel >= 55) {
                    batteryImg = R.drawable.ic_sharp_battery_60_20dp;
                } else if (loc.batteryLevel >= 40) {
                    batteryImg = R.drawable.ic_sharp_battery_50_20dp;
                } else if (loc.batteryLevel >= 25) {
                    batteryImg = R.drawable.ic_sharp_battery_30_20dp;
                } else {
                    batteryImg = R.drawable.ic_sharp_battery_20_20dp;
                }
            }
            Drawable drawable = activity.getDrawable(batteryImg);
            batteryIcon.setImageDrawable(drawable);
            battery.setVisibility(View.VISIBLE);
            batteryIcon.setVisibility(View.VISIBLE);
        } else {
            battery.setText(null);
            batteryIcon.setImageDrawable(null);
            battery.setVisibility(View.INVISIBLE);
            batteryIcon.setVisibility(View.INVISIBLE);
        }

        // the address
        RevGeocoding rg = ReverseGeocodingCache.get(loc.latitude, loc.longitude);
        if (rg != null) {
            address.setText(rg.getAddress());
        } else {
            address.setText(R.string.loading_ellipsis);
            ReverseGeocodingCache.fetch(activity, loc.latitude, loc.longitude, new ReverseGeocodingCache.OnCachedListener() {
                @Override
                public void onReverseGeocodingCached(@Nullable RevGeocoding rg) {
                    if (rg == null) {
                        return;
                    }
                    if (currLoc == null) {
                        return;
                    }
                    if (currLoc.latitude == loc.latitude && currLoc.longitude == loc.longitude) {
                        address.setText(rg.getAddress());
                    }
                }
            });
        }

        App.runOnUiThread(new InfoPanelRefresher(showId), REFRESH_INTERVAL);
    }

    @UiThread
    public void updateFriendRecord(@NonNull FriendRecord update) {
        if (currFriend == null) {
            return;
        }

        if (currFriend.id == update.id) {
            currFriend = update;
        }
    }

    @UiThread
    public void updateRefreshProgressBarState(long friendId) {
        UpdateStatusTracker.State status = UpdateStatusTracker.getFriendState(friendId);

        int vis;
        @ColorRes int colorId = 0;
        switch (status) {
            case NotRequested:
                vis = View.GONE;
                break;
            case Requested:
                vis = View.VISIBLE;
                colorId = R.color.zood_canary;
                break;
            case RequestedAndUnresponsive:
                vis = View.VISIBLE;
                colorId = R.color.zood_grey;
                break;
            case RequestDenied:
                vis = View.VISIBLE;
                colorId = R.color.zood_red;
                break;
            case RequestAcknowledged:
                vis = View.VISIBLE;
                colorId = R.color.zood_blue;
                break;
            case RequestFulfilled:
                vis = View.GONE;
                break;
            default:
                vis = View.GONE;
                break;
        }
        if (colorId != 0) {
            DrawableCompat.setTint(refreshProgressBar.getIndeterminateDrawable(),
                    ContextCompat.getColor(activity, colorId));
        }
        refreshProgressBar.setVisibility(vis);
    }

    private final CompoundButton.OnCheckedChangeListener shareCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (currFriend == null) {
                L.i("Share switch changed, but no FriendRecord found");
                return;
            }

            listener.onInfoPanelShareToggled(currFriend, isChecked);
        }
    };

    public interface Listener {
        @UiThread
        void onInfoPanelLocationRequested(@NonNull FriendRecord friend);
        @UiThread
        void onInfoPanelRemoveFriend(@NonNull FriendRecord friend);
        @UiThread
        void onInfoPanelShareToggled(@NonNull FriendRecord friend, boolean shouldShare);
    }

    private class InfoPanelRefresher implements UiRunnable {
        private final long id;

        private InfoPanelRefresher(long id) {
            this.id = id;
        }

        @Override
        public void run() {
            if (showId != id) {
                return;
            }

            // update the relative time, and refresh button
            long now = System.currentTimeMillis();
            calculateAndSetUpdateTime(now);
            // if it's been less than 3 minutes since the last update, hide the refresh button
            if ((now - currLoc.time) < 3 * DateUtils.MINUTE_IN_MILLIS) {
                refreshButton.setVisibility(View.INVISIBLE);
            } else {
                refreshButton.setVisibility(View.VISIBLE);
            }

            App.runOnUiThread(this, REFRESH_INTERVAL);
        }
    }

}
