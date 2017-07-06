package io.pijun.george;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;

import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;

public class FriendsSheetFragment extends Fragment implements FriendItemsAdapter.FriendItemsListener {

    private AvatarsAdapter mAvatarsAdapter = new AvatarsAdapter();
    private FriendItemsAdapter mFriendItemsAdapter = new FriendItemsAdapter();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFriendItemsAdapter.setListener(this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_friends_sheet, container, false);
        RecyclerView avatarsView = (RecyclerView) root.findViewById(R.id.avatars);
        LinearLayoutManager llm = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        avatarsView.setLayoutManager(llm);
        avatarsView.setAdapter(mAvatarsAdapter);

        RecyclerView friendsList = (RecyclerView) root.findViewById(R.id.friend_items);
        friendsList.setAdapter(mFriendItemsAdapter);

        return root;
    }

    @Subscribe
    public void onFriendLocationUpdated(final FriendLocation loc) {
        mFriendItemsAdapter.setFriendLocation(getContext(), loc);
    }

    @Subscribe
    public void onLocationSharingGranted(LocationSharingGranted grant) {
        App.runInBackground(() -> {
            FriendRecord friend = DB.get(getContext()).getFriendByUserId(grant.userId);
            if (friend != null) {
                mAvatarsAdapter.addFriend(friend);
            }
        });
    }

    @Override
    public void onSharingStateChanged(long friendId, boolean shouldShare) {
        if (shouldShare) {
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        App.registerOnBus(this);
        final DB db = DB.get(getContext());
        App.runInBackground(() -> {
            ArrayList<FriendRecord> friends = db.getFriends();
            mAvatarsAdapter.setFriends(friends);
            mFriendItemsAdapter.setFriends(friends);
            for (FriendRecord record : friends) {
                FriendLocation loc = db.getFriendLocation(record.id);
                if (loc != null) {
                    mFriendItemsAdapter.setFriendLocation(getContext(), loc);
                }
            }
        });

        if (getActivity() instanceof AvatarsAdapter.AvatarsAdapterListener) {
            mAvatarsAdapter.setListener((AvatarsAdapter.AvatarsAdapterListener) getActivity());
        }
    }

    @Override
    public void onStop() {
        App.unregisterFromBus(this);

        super.onStop();
    }
}
