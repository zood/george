package xyz.zood.george;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import io.pijun.george.App;
import io.pijun.george.UiRunnable;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.view.AvatarView;
import io.pijun.george.view.RecyclerViewAdapterItem;

public class FriendsBarAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int ADDFRIEND_ID = -4;
    private static final int DIVIDER_ID = -3;
    private static final int MARGIN_ID = -2;

    private final ArrayList<RecyclerViewAdapterItem> items = new ArrayList<>();
    private ArrayList<FriendRecord> friends = new ArrayList<>();
    private final SparseArray<FriendRecord> friendPositions = new SparseArray<>();
    @NonNull
    private final Listener listener;

    FriendsBarAdapter(@NonNull Listener l) {
        this.listener = l;
        setHasStableIds(true);
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
        int idx = friends.indexOf(friend);
        if (idx == -1) {
            friends.add(friend);
        } else {
            friends.set(idx, friend);
        }
        rebuildItemsList();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    @UiThread
    void onAvatarUpdated(@Nullable String username) {
        if (username == null) {
            return;
        }

        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RecyclerViewAdapterItem item = items.get(position);
        if (holder instanceof AvatarViewHolder) {
            FriendRecord friend = friendPositions.get(position);
            if (friend == null) {
                throw new RuntimeException("Unable to find the friend with id " + item.id);
            }

            AvatarViewHolder h = (AvatarViewHolder) holder;
            Resources rsrcs = h.avatar.getResources();
            int imgSize = rsrcs.getDimensionPixelSize(R.dimen.forty);
            Context ctx = h.avatar.getContext();
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
        View view = inflater.inflate(viewType, parent, false);
        if (viewType == R.layout.friends_bar_friend) {
            final AvatarViewHolder h = new AvatarViewHolder(view);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FriendRecord friend = friendPositions.get(h.getAdapterPosition());
                    if (friend == null) {
                        throw new RuntimeException("Unable to find friend at position " + h.getAdapterPosition());
                    }
                    listener.onFriendSelected(friend);
                }
            });
            return h;
        } else if (viewType == R.layout.friends_bar_add_friend) {
            final AddFriendViewHolder h = new AddFriendViewHolder(view);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onAddFriendAction();
                }
            });
            return h;
        }

        return new NothingViewHolder(view);
    }

    @UiThread
    private void rebuildItemsList() {
        items.clear();

        int position = 0;
        items.add(new RecyclerViewAdapterItem(R.layout.friends_bar_margin, MARGIN_ID));
        friendPositions.clear();
        for (FriendRecord f : friends) {
            position++;
            items.add(new RecyclerViewAdapterItem(R.layout.friends_bar_friend, f.id));
            friendPositions.put(position, f);
        }
        items.add(new RecyclerViewAdapterItem(R.layout.friends_bar_divider, DIVIDER_ID));
        items.add(new RecyclerViewAdapterItem(R.layout.friends_bar_add_friend, ADDFRIEND_ID));
    }

    @UiThread
    void removeFriend(long friendId) {
        for (int i = 0; i< friends.size(); i++) {
            if (friends.get(i).id == friendId) {
                friends.remove(i);
                break;
            }
        }

        rebuildItemsList();
        notifyDataSetChanged();
    }

    @AnyThread
    public void setFriends(final ArrayList<FriendRecord> friends) {
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                _setFriends(friends);
            }
        });
    }

    @UiThread
    private void _setFriends(ArrayList<FriendRecord> friends) {
        this.friends = friends;
        rebuildItemsList();
        notifyDataSetChanged();
    }

    private static class AddFriendViewHolder extends RecyclerView.ViewHolder {

        ImageView image;

        AddFriendViewHolder(View itemView) {
            super(itemView);

            image = itemView.findViewById(R.id.image);
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, image.getWidth(), image.getHeight(), image.getWidth()/2.0f);
                }
            });
            image.setClipToOutline(true);
        }
    }

    private static class AvatarViewHolder extends RecyclerView.ViewHolder {
        final AvatarView avatar;

        AvatarViewHolder(View itemView) {
            super(itemView);

            avatar = itemView.findViewById(R.id.avatar_image);
        }
    }

    private static class NothingViewHolder extends RecyclerView.ViewHolder {
        NothingViewHolder(View itemView) {
            super(itemView);
        }
    }

    public interface Listener {
        @UiThread
        void onAddFriendAction();
        @UiThread
        void onFriendSelected(@NonNull FriendRecord fr);
    }
}
