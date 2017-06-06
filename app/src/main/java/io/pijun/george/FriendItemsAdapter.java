package io.pijun.george;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;

class FriendItemsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private ArrayList<FriendRecord> mFriends = new ArrayList<>();
    private LongSparseArray<FriendLocation> mFriendLocations = new LongSparseArray<>();

    FriendItemsAdapter() {
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return mFriends.get(position).id;
    }

    @Override
    public int getItemCount() {
        return mFriends.size();
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FriendItemViewHolder) {
            FriendItemViewHolder h = (FriendItemViewHolder) holder;

            // -- AVATAR --
            Resources rsrc = h.avatar.getResources();
            int forty = rsrc.getDimensionPixelSize(R.dimen.forty);
            Bitmap bmp = Bitmap.createBitmap(forty, forty, Bitmap.Config.ARGB_8888);
            final FriendRecord friend = mFriends.get(position);
            Identicon.draw(bmp, friend.user.username);
            RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(rsrc, bmp);
            rounded.setCircular(true);
            h.avatar.setImageDrawable(rounded);
            h.avatar.setActivated(friend.receivingBoxId != null);

            // -- USERNAME --
            h.username.setText(friend.user.username);
            if (friend.receivingBoxId == null) {
                h.username.setEnabled(false);
                h.username.setActivated(false);
                h.username.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
            } else {
                h.username.setEnabled(true);
                h.username.setActivated(friend.sendingBoxId != null);
                h.username.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            }

            // -- LOCATION AND UPDATE TIME --
            if (friend.receivingBoxId == null) {
                h.location.setText(null);
                h.updateTime.setText(R.string.not_sharing_location);
            } else {
                FriendLocation loc = mFriendLocations.get(friend.id);
                if (loc == null) {
                    h.location.setText(null);
                    h.updateTime.setText(R.string.unknown);
                } else {
                    String area = AreaCache.getArea(loc.latitude, loc.longitude);
                    if (area == null) {
                        AreaCache.fetchArea(h.location.getContext(), loc.latitude, loc.longitude, new AreaCache.ReverseGeocodingListener() {
                            @Override
                            public void onReverseGeocodingCompleted(@NonNull String area) {
                                reloadFriend(friend.id);
                            }
                        });
                    }
                    if (area != null) {
                        h.location.setText(area);
                    } else {
                        h.location.setText(h.location.getContext().getString(R.string.loading_ellipsis));
                    }
                    long now = System.currentTimeMillis();
                    final CharSequence relTime;
                    if (loc.time >= now-60* DateUtils.SECOND_IN_MILLIS) {
                        relTime = " • " + h.location.getContext().getString(R.string.now);
                    } else {
                        relTime = " • " + DateUtils.getRelativeTimeSpanString(
                                loc.time,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE);
                    }
                    h.updateTime.setText(relTime);
                }
            }

            // -- SHARE SWITCH + LABEL --
            h.shareSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    L.i("on checked change: " + isChecked);
                }
            });
            if (friend.sendingBoxId != null) {
                h.shareSwitch.setChecked(true);
                h.shareSwitchLabel.setText(R.string.sharing);
            } else {
                h.shareSwitch.setChecked(false);
                h.shareSwitchLabel.setText(R.string.not_sharing);
            }
            h.shareSwitch.setChecked(friend.sendingBoxId != null);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.friend_item, parent, false);
        FriendItemViewHolder holder = new FriendItemViewHolder(view);
        ViewOutlineProvider vop = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        };
        holder.avatar.setOutlineProvider(vop);
        holder.avatar.setClipToOutline(true);
        return holder;
    }

    @UiThread
    private void reloadFriend(long friendId) {
        for (int i=0; i<mFriends.size(); i++) {
            FriendRecord friend = mFriends.get(i);
            if (friend.id == friendId) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @AnyThread
    void setFriendLocation(@NonNull final Context ctx, @NonNull final FriendLocation loc) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            App.runInBackground(new WorkerRunnable() {
                @Override
                public void run() {
                    _setFriendLocation(ctx, loc);
                }
            });
        } else {
            //noinspection WrongThread
            _setFriendLocation(ctx, loc);
        }
    }

    @WorkerThread
    private void _setFriendLocation(Context ctx, final FriendLocation loc) {
        mFriendLocations.put(loc.friendId, loc);
        if (AreaCache.getArea(loc.latitude, loc.longitude) == null) {
            AreaCache.fetchArea(ctx, loc.latitude, loc.longitude, new AreaCache.ReverseGeocodingListener() {
                @Override
                public void onReverseGeocodingCompleted(@NonNull String area) {
                    L.i("_setFriendLocation onreverse complete");
                    reloadFriend(loc.friendId);
                }
            });
        }
    }

    @UiThread
    void setFriends(@NonNull ArrayList<FriendRecord> friends) {
        this.mFriends = friends;
        notifyDataSetChanged();
    }

    interface FriendItemsListener {
        void onSharingStateChanged(long friendId, boolean shouldShare);
    }

    private static class FriendItemViewHolder extends RecyclerView.ViewHolder {

        private final ImageView avatar;
        private final TextView username;
        private final TextView location;
        private final TextView updateTime;
        private final TextView shareSwitchLabel;
        private final Switch shareSwitch;

        FriendItemViewHolder(View itemView) {
            super(itemView);

            avatar = (ImageView) itemView.findViewById(R.id.avatar);
            username = (TextView) itemView.findViewById(R.id.username);
            location = (TextView) itemView.findViewById(R.id.location);
            updateTime = (TextView) itemView.findViewById(R.id.update_time);
            shareSwitch = (Switch) itemView.findViewById(R.id.share_switch);
            shareSwitchLabel = (TextView) itemView.findViewById(R.id.share_switch_label);
        }
    }

}
