package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.UiThread;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import java.util.ArrayList;

import io.pijun.george.models.FriendRecord;

class AvatarsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private ArrayList<FriendRecord> mFriends = new ArrayList<>();
    private AvatarsAdapterListener mListener;

    @SuppressLint("WrongThread")
    @AnyThread
    void addFriend(final FriendRecord friend) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _addFriend(friend);
        } else {
            App.runOnUiThread(() -> _addFriend(friend));
        }
    }

    @UiThread
    private void _addFriend(FriendRecord friend) {
        // check if the friend is already in the list
        if (mFriends.contains(friend)) {
            return;
        }
        mFriends.add(friend);
        notifyItemInserted(mFriends.size()-1);
    }

    @Override
    public int getItemCount() {
        return mFriends.size() + 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 || position == mFriends.size()+1) {
            return R.layout.avatar_container_margin;
        }

        return R.layout.avatar_preview;
    }

    private void onAvatarClicked(int friendsPos) {
        if (mListener != null) {
            mListener.onAvatarSelected(mFriends.get(friendsPos));
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AvatarViewHolder) {
            AvatarViewHolder h = (AvatarViewHolder) holder;
            Resources rsrcs = h.button.getResources();
            int imgSize = rsrcs.getDimensionPixelSize(R.dimen.forty);
            Bitmap bitmap = Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888);
            FriendRecord friend = mFriends.get(position - 1);
            Identicon.draw(bitmap, friend.user.username);
            RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(rsrcs, bitmap);
            rounded.setCircular(true);
            h.button.setImageDrawable(rounded);
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
            h.button.setOnClickListener(v -> onAvatarClicked(h.getAdapterPosition()-1));
            return h;
        }

        throw new IllegalArgumentException("Unknown view type");
    }

    @SuppressLint("WrongThread")
    @AnyThread
    void setFriends(final ArrayList<FriendRecord> friends) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _setFriends(friends);
        } else {
            App.runOnUiThread(() -> _setFriends(friends));
        }
    }

    @UiThread
    private void _setFriends(ArrayList<FriendRecord> friends) {
        this.mFriends = friends;
        for (FriendRecord f : friends) {
            L.i("setting: " + f.user.username);
        }
        notifyDataSetChanged();
    }

    void setListener(AvatarsAdapterListener l) {
        mListener = l;
    }

    private static class AvatarContainerMarginViewHolder extends RecyclerView.ViewHolder {
        AvatarContainerMarginViewHolder(View itemView) {
            super(itemView);
        }
    }

    private static class AvatarViewHolder extends RecyclerView.ViewHolder {
        final ImageButton button;

        AvatarViewHolder(View itemView) {
            super(itemView);

            button = (ImageButton) itemView;
        }
    }

    interface AvatarsAdapterListener {
        @UiThread
        void onAvatarSelected(FriendRecord fr);
    }
}
