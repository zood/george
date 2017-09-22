package io.pijun.george;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.AnyThread;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;
import java.util.Locale;

import io.pijun.george.models.FriendRecord;
import io.pijun.george.view.AvatarView;

class AvatarsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private ArrayList<FriendRecord> mFriends = new ArrayList<>();
    @Nullable private AvatarsAdapterListener mListener;

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
            return R.layout.avatar_container_margin;
        }

        return R.layout.avatar_preview;
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
        if (holder instanceof AvatarViewHolder) {
            AvatarViewHolder h = (AvatarViewHolder) holder;
            Resources rsrcs = h.image.getResources();
            int imgSize = rsrcs.getDimensionPixelSize(R.dimen.forty);
            Context ctx = h.image.getContext();
            FriendRecord friend = mFriends.get(position);
            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    h.image.setImage(bitmap);
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                    Bitmap bitmap = Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888);
                    Identicon.draw(bitmap, friend.user.username);
                    h.image.setImage(bitmap);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {}
            };
            h.image.setTag(target);
            Picasso.with(ctx).
                    load(AvatarManager.getAvatar(ctx, friend.user.username)).
                    resize(imgSize, imgSize).
                    into(target);
            h.image.setBorderColorRes(friend.receivingBoxId != null ? R.color.colorPrimary : R.color.ui_tint_gray);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == R.layout.avatar_container_margin) {
            View view = inflater.inflate(R.layout.avatar_container_margin, parent, false);
            return new AvatarContainerMarginViewHolder(view);
        } else if (viewType == R.layout.avatar_preview) {
            View view = inflater.inflate(R.layout.avatar_preview, parent, false);
            final AvatarViewHolder h = new AvatarViewHolder(view);
            h.itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    FriendRecord friend = mFriends.get(h.getAdapterPosition());
                    mListener.onAvatarSelected(friend);
                }
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

    void setListener(@Nullable AvatarsAdapterListener l) {
        this.mListener = l;
    }

    private static class AvatarContainerMarginViewHolder extends RecyclerView.ViewHolder {
        AvatarContainerMarginViewHolder(View itemView) {
            super(itemView);
        }
    }

    private static class AvatarViewHolder extends RecyclerView.ViewHolder {
        final AvatarView image;

        AvatarViewHolder(View itemView) {
            super(itemView);

            image = itemView.findViewById(R.id.avatar_image);
        }
    }

    interface AvatarsAdapterListener {
        @UiThread
        void onAvatarSelected(FriendRecord fr);
    }
}
