package io.pijun.george;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Locale;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.view.AvatarView;
import xyz.zood.george.AvatarManager;
import xyz.zood.george.R;

class AvatarsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private ArrayList<FriendRecord> mFriends = new ArrayList<>();
    @NonNull
    private final Listener listener;

    AvatarsAdapter(@NonNull Listener l) {
        this.listener = l;
    }

    @AnyThread
    void addFriend(final FriendRecord friend) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                _addFriend(friend);
            }
        });
    }

    @UiThread
    private void _addFriend(FriendRecord friend) {
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

    @Override
    public int getItemCount() {
        return mFriends.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mFriends.size()) {
            return R.layout.avatars_container_margin;
        }

        return R.layout.avatar_item;
    }

    @UiThread
    void onAvatarUpdated(@Nullable String username) {
        L.i("AvatarsAdapter.onAvatarUpdated");
        if (username == null) {
            return;
        }

        username = username.toLowerCase(Locale.US);
        for (int i=0; i<mFriends.size(); i++) {
            FriendRecord f = mFriends.get(i);
            if (f.user.username.toLowerCase(Locale.US).equals(username)) {
                L.i("\tfound the matching avatar to update: " + username);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AvatarViewHolder) {
            AvatarViewHolder h = (AvatarViewHolder) holder;
            Resources rsrcs = h.avatar.getResources();
            int imgSize = rsrcs.getDimensionPixelSize(R.dimen.fortyEight);
            Context ctx = h.avatar.getContext();
            FriendRecord friend = mFriends.get(position);
            h.avatar.setUsername(friend.user.username);
            Picasso.with(ctx).
                    load(AvatarManager.getAvatar(ctx, friend.user.username)).
                    resize(imgSize, imgSize).
                    into(h.avatar);
        }
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == R.layout.avatars_container_margin) {
            View view = inflater.inflate(R.layout.avatars_container_margin, parent, false);
            return new AvatarContainerMarginViewHolder(view);
        } else if (viewType == R.layout.avatar_item) {
            View view = inflater.inflate(R.layout.avatar_item, parent, false);
            final AvatarViewHolder h = new AvatarViewHolder(view);
            h.itemView.setOnClickListener(v -> {
                FriendRecord friend = mFriends.get(h.getAdapterPosition());
                listener.onAvatarSelected(friend);
            });
            return h;
        }

        throw new IllegalArgumentException("Unknown view type");
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

    @AnyThread
    void setFriends(final ArrayList<FriendRecord> friends) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                _setFriends(friends);
            }
        });
    }

    @UiThread
    private void _setFriends(ArrayList<FriendRecord> friends) {
        this.mFriends = friends;
        notifyDataSetChanged();
    }

    private static class AvatarContainerMarginViewHolder extends RecyclerView.ViewHolder {
        AvatarContainerMarginViewHolder(View itemView) {
            super(itemView);
        }
    }

    private static class AvatarViewHolder extends RecyclerView.ViewHolder {
        final AvatarView avatar;

        AvatarViewHolder(View itemView) {
            super(itemView);

            avatar = itemView.findViewById(R.id.avatar_image);
        }
    }

    interface Listener {
        @UiThread
        void onAvatarSelected(FriendRecord fr);
    }
}
