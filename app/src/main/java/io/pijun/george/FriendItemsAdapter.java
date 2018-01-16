package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.view.AvatarView;

class FriendItemsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private ArrayList<FriendRecord> mFriends = new ArrayList<>();
    private LongSparseArray<FriendLocation> mFriendLocations = new LongSparseArray<>();
    private FriendItemsListener mListener;

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

    @UiThread
    void onAvatarUpdated(@Nullable String username) {
        if (username == null) {
            return;
        }

        username = username.toLowerCase(Locale.US);
        for (int i=0; i<mFriends.size(); i++) {
            FriendRecord f = mFriends.get(i);
            if (f.user.username.toLowerCase(Locale.US).equals(username)) {
                notifyItemChanged(i);
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FriendItemViewHolder) {
            FriendItemViewHolder h = (FriendItemViewHolder) holder;

            // -- AVATAR --
            Resources rsrc = h.avatar.getResources();
            int forty = rsrc.getDimensionPixelSize(R.dimen.forty);
            Context ctx = h.avatar.getContext();
            final FriendRecord friend = mFriends.get(position);
            h.avatar.setBorderColorRes(friend.receivingBoxId != null ? R.color.colorPrimary : R.color.ui_tint_gray);
            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    h.avatar.setImage(bitmap);
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                    Bitmap bmp = Bitmap.createBitmap(forty, forty, Bitmap.Config.ARGB_8888);
                    Identicon.draw(bmp, friend.user.username);
                    h.avatar.setImage(bmp);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {}
            };
            h.avatar.setTag(target);
            Picasso.with(ctx).
                    load(AvatarManager.getAvatar(ctx, friend.user.username)).
                    resize(forty, forty).
                    into(target);

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
                h.location.setText("");
                h.updateTime.setText(R.string.not_sharing_location);
            } else {
                FriendLocation loc = mFriendLocations.get(friend.id);
                if (loc == null) {
                    h.location.setText("");
                    h.updateTime.setText(R.string.unknown);
                } else {
                    String area = AreaCache.getArea(loc.latitude, loc.longitude);
                    if (area == null) {
                        AreaCache.fetchArea(h.location.getContext(), loc.latitude, loc.longitude, new AreaCache.ReverseGeocodingListener() {
                            @Override
                            public void onReverseGeocodingCompleted(@Nullable String area) {
                                onFriendGeocodingUpdated(friend.id);
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
                    if (loc.time >= now - (60*DateUtils.SECOND_IN_MILLIS)) {
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
            // remove the listener while we're setting the checked state
            h.shareSwitch.setOnCheckedChangeListener(null);
            if (friend.sendingBoxId != null) {
                h.shareSwitch.setChecked(true);
                h.shareSwitchLabel.setText(R.string.sharing);
            } else {
                h.shareSwitch.setChecked(false);
                h.shareSwitchLabel.setText(R.string.not_sharing);
            }
            h.shareSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                onShareSwitchCheckedChange(holder.getAdapterPosition(), isChecked);
                if (isChecked) {
                    h.shareSwitchLabel.setText(R.string.sharing);
                } else {
                    h.shareSwitchLabel.setText(R.string.not_sharing);
                }
            });
            h.itemView.requestLayout();
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
        if (payloads == null || payloads.size() == 0) {
            onBindViewHolder(holder, position);
            return;
        }
        if (holder instanceof FriendItemViewHolder) {
            FriendItemViewHolder h = (FriendItemViewHolder) holder;
            final FriendRecord friend = mFriends.get(position);
            for (Object payload : payloads) {
                if (payload.equals("geocoding")) {
                    FriendLocation loc = mFriendLocations.get(friend.id);
                    if (loc == null) {
                        continue;
                    }
                    String area = AreaCache.getArea(loc.latitude, loc.longitude);
                    h.location.setText(area);
                } else if (payload.equals("location")) {
                    FriendLocation loc = mFriendLocations.get(friend.id);
                    if (loc == null) {
                        continue;
                    }
                    String area = AreaCache.getArea(loc.latitude, loc.longitude);
                    if (area == null) {
                        AreaCache.fetchArea(h.location.getContext(), loc.latitude, loc.longitude, new AreaCache.ReverseGeocodingListener() {
                            @Override
                            public void onReverseGeocodingCompleted(@Nullable String area) {
                                onFriendGeocodingUpdated(friend.id);
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
                    if (loc.time >= now - (60*DateUtils.SECOND_IN_MILLIS)) {
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
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.friend_item, parent, false);
        final FriendItemViewHolder holder = new FriendItemViewHolder(view);
        holder.itemView.setOnClickListener(v -> {
            if (mListener == null) {
                return;
            }
            FriendRecord friend = mFriends.get(holder.getAdapterPosition());
            mListener.onShowFriendInfoAction(friend);
        });

        return holder;
    }

    private void onFriendGeocodingUpdated(long friendId) {
        for (int i=0; i<mFriends.size(); i++) {
            FriendRecord friend = mFriends.get(i);
            if (friend.id == friendId) {
                notifyItemChanged(i, "geocoding");
                break;
            }
        }
    }

    private void onFriendLocationUpdated(long friendId) {
        for (int i=0; i<mFriends.size(); i++) {
            FriendRecord friend = mFriends.get(i);
            if (friend.id == friendId) {
                notifyItemChanged(i, "location");
                break;
            }
        }
    }

    private void onShareSwitchCheckedChange(int position, boolean isChecked) {
        if (mListener == null) {
            return;
        }
        FriendRecord friend = mFriends.get(position);
        mListener.onSharingStateChanged(friend, isChecked);
    }

    @UiThread
    void reloadFriend(long friendId) {
        for (int i=0; i<mFriends.size(); i++) {
            FriendRecord friend = mFriends.get(i);
            if (friend.id == friendId) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @UiThread
    void removeFriend(long friendId) {
        for (int i=0; i<mFriends.size(); i++) {
            FriendRecord friend = mFriends.get(i);
            if (friend.id == friendId) {
                mFriends.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @UiThread
    void removeFriendLocation(long friendId) {
        mFriendLocations.remove(friendId);
        reloadFriend(friendId);
    }

    @UiThread
    void setFriendLocation(@NonNull final Context ctx, @NonNull final FriendLocation loc) {
        mFriendLocations.put(loc.friendId, loc);
        if (AreaCache.getArea(loc.latitude, loc.longitude) == null) {
            AreaCache.fetchArea(ctx, loc.latitude, loc.longitude, area -> onFriendGeocodingUpdated(loc.friendId));
        }
        onFriendLocationUpdated(loc.friendId);
    }

    @UiThread
    void updateFriend(@NonNull FriendRecord friend) {
        // check if we're replacing or adding the friend
        int idx = mFriends.indexOf(friend);
        if (idx == -1) {
            mFriends.add(friend);
            notifyItemInserted(mFriends.size()-1);
        } else {
            mFriends.set(idx, friend);
            notifyItemChanged(idx);
        }
    }

    @SuppressLint("WrongThread")
    @AnyThread
    void setFriends(@NonNull final ArrayList<FriendRecord> friends) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _setFriends(friends);
        } else {
            App.runOnUiThread(() -> _setFriends(friends));
        }
    }

    @UiThread
    private void _setFriends(@NonNull ArrayList<FriendRecord> friends) {
        this.mFriends = friends;
        notifyDataSetChanged();
    }

    void setListener(FriendItemsListener l) {
        this.mListener = l;
    }

    interface FriendItemsListener {
        @UiThread
        void onSharingStateChanged(@NonNull FriendRecord friend, boolean shouldShare);
        void onShowFriendInfoAction(@NonNull FriendRecord friend);
    }

    private static class FriendItemViewHolder extends RecyclerView.ViewHolder {

        private final AvatarView avatar;
        private final TextView username;
        private final TextView location;
        private final TextView updateTime;
        private final TextView shareSwitchLabel;
        private final Switch shareSwitch;

        FriendItemViewHolder(View itemView) {
            super(itemView);

            avatar = itemView.findViewById(R.id.avatar);
            username = itemView.findViewById(R.id.username);
            location = itemView.findViewById(R.id.location);
            updateTime = itemView.findViewById(R.id.update_time);
            shareSwitch = itemView.findViewById(R.id.share_switch);
            shareSwitchLabel = itemView.findViewById(R.id.share_switch_label);
        }
    }

}
