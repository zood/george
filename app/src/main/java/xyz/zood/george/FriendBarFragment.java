package xyz.zood.george;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import io.pijun.george.App;
import io.pijun.george.WorkerRunnable;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import xyz.zood.george.viewmodels.MainViewModel;

public class FriendBarFragment extends Fragment implements FriendsBarAdapter.Listener, DB.Listener, AvatarManager.Listener {

    private FriendsBarAdapter adapter;
    private MainViewModel mainViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainViewModel = ViewModelProviders.of(requireActivity()).get(MainViewModel.class);
        adapter = new FriendsBarAdapter(this);
        App.runInBackground(new WorkerRunnable() {
            @Override
            public void run() {
                ArrayList<FriendRecord> friends = DB.get().getFriends();
                adapter.setFriends(friends);
            }
        });
        DB.get().addListener(this);
        AvatarManager.addListener(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        RecyclerView list = (RecyclerView) inflater.inflate(R.layout.fragment_friends_bar, container, false);
        LinearLayoutManager llm = new LinearLayoutManager(inflater.getContext(), LinearLayoutManager.HORIZONTAL, false);
        list.setLayoutManager(llm);
        list.setAdapter(adapter);

        return list;
    }

    @Override
    public void onDestroy() {
        DB.get().removeListener(this);
        AvatarManager.removeListener(this);

        super.onDestroy();
    }

    //region FriendsBarAdapter.Listener

    @Override
    public void onAddFriendAction() {
        mainViewModel.notifyAddFriendClicked();
    }

    @Override
    public void onFriendSelected(@NonNull FriendRecord friend) {
        mainViewModel.selectFriend(friend);
    }

    //endregion

    //region DB.Listener

    @Override
    public void onFriendRemoved(long friendId) {
        adapter.removeFriend(friendId);
    }

    @Override
    public void onLocationSharingGranted(long userId) {
        FriendRecord friend = DB.get().getFriendByUserId(userId);
        if (friend == null) {
            return;
        }
        adapter.addFriend(friend);
    }

    @Override
    public void onStartedSharingWithUser(long userId) {
        FriendRecord friend = DB.get().getFriendByUserId(userId);
        if (friend == null) {
            return;
        }
        // We need to make sure the friend is in the avatar adapter
        // The adapter handles the case if the friend is already in there.
        adapter.addFriend(friend);
    }

    @Override
    public void onStoppedSharingWithUser(long userId) {
        FriendRecord friend = DB.get().getFriendByUserId(userId);
        if (friend == null) {
            return;
        }
        // Update the FriendRecord in our avatar list
        adapter.addFriend(friend);
    }

    //endregion

    //region AvatarManager.Listener

    @Override
    public void onAvatarUpdated(@Nullable String username) {
        if (username == null) {
            return;
        }

        adapter.onAvatarUpdated(username);
    }

    //endregion
}
