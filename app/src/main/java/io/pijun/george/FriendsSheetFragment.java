package io.pijun.george;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.crash.FirebaseCrash;
import com.squareup.otto.Subscribe;

import java.security.SecureRandom;
import java.util.ArrayList;

import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRevoked;
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
                mFriendItemsAdapter.updateFriend(friend);
            }
        });
    }

    @Subscribe
    @UiThread
    public void onLocationSharingRevoked(LocationSharingRevoked revoked) {
        App.runInBackground(() -> {
            DB db = DB.get(getContext());
            // check if this is a known friend
            FriendRecord friend = db.getFriendByUserId(revoked.userId);
            if (friend == null) {
                // we don't know this person, so just leave
                return;
            }
            ArrayList<FriendRecord> friends = db.getFriends();
            mFriendItemsAdapter.setFriends(friends);
            App.runOnUiThread(() -> mFriendItemsAdapter.removeFriendLocation(friend.id));
        });
    }

    @Override
    public void onSharingStateChanged(@NonNull FriendRecord friend, boolean shouldShare) {
        App.runInBackground(() -> {
            if (shouldShare) {
                startSharingWith(friend);
            } else {
                stopSharingWith(friend);
            }
        });
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

    @WorkerThread
    private void startSharingWith(FriendRecord friend) {
        Prefs prefs = Prefs.get(getContext());
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(getContext(), null, "How are you not logged in right now? (missing access token)");
            return;
        }
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            Utils.showStringAlert(getContext(), null, "How are you not logged in right now? (missing key pair)");
            return;
        }
        byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(sendingBoxId);
        // send the sending box id to the friend
        UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
        EncryptedData msg = Sodium.publicKeyEncrypt(comm.toJSON(), friend.user.publicKey, keyPair.secretKey);
        if (msg != null) {
            OscarClient.queueSendMessage(getContext(), accessToken, friend.user.userId, msg, false);
        } else {
            FirebaseCrash.report(new Exception("The message was null!"));
            return;
        }

        // add this to our database
        DB db = DB.get(getContext());
        try {
            db.startSharingWith(friend.user, sendingBoxId);
        } catch (DB.DBException ex) {
            FirebaseCrash.report(ex);            // TODO: handle this. Toast or snackbar?
        }
    }

    @WorkerThread
    private void stopSharingWith(FriendRecord friend) {
        Prefs prefs = Prefs.get(getContext());
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(getContext(), null, "How are you not logged in right now? (missing access token)");
            return;
        }
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            Utils.showStringAlert(getContext(), null, "How are you not logged in right now? (missing key pair)");
            return;
        }

        // remove the sending box id from the database
        DB db = DB.get(getContext());
        try {
            db.stopSharingWith(friend.user);
        } catch (DB.DBException ex) {
            FirebaseCrash.report(ex);
            // TODO: handle this. Toast or snackbar?
        }

        UserComm comm = UserComm.newLocationSharingRevocation();
        EncryptedData msg = Sodium.publicKeyEncrypt(comm.toJSON(), friend.user.publicKey, keyPair.secretKey);
        if (msg != null) {
            OscarClient.queueSendMessage(getContext(), accessToken, friend.user.userId, msg, false);
        } else {
            FirebaseCrash.report(new Exception("The message was null!"));
        }
    }
}
