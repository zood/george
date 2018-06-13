package io.pijun.george;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;

import io.pijun.george.api.OscarClient;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.databinding.FragmentFriendsSheetBinding;
import io.pijun.george.event.AvatarUpdated;
import io.pijun.george.event.FriendRemoved;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRevoked;
import io.pijun.george.view.FriendsSheetBehavior;
import io.pijun.george.view.FriendsSheetLayout;
import io.pijun.george.view.MainLayout;

public class FriendsSheetFragment extends Fragment implements FriendItemsAdapter.FriendItemsListener {

    private AvatarsAdapter mAvatarsAdapter = new AvatarsAdapter();
    private FriendItemsAdapter mFriendItemsAdapter = new FriendItemsAdapter();
    private FragmentFriendsSheetBinding mBinding;
    private FriendsSheetBehavior mBehavior;
    private int mTenDips;
    private boolean mInitialLayoutDone = false;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof MapActivity) {
            ((MapActivity) context).setFriendsSheetFragment(this);
        }
    }

    @Subscribe @Keep @UiThread
    public void onAvatarUpdated(AvatarUpdated evt) {
        mAvatarsAdapter.onAvatarUpdated(evt.username);
        mFriendItemsAdapter.onAvatarUpdated(evt.username);
    }

    /**
     * Should be called by the containing activity
     * @return <code>true</code> if the FriendsSheetFragment consumed the button press. <code>false</code>
     * otherwise.
     */
    public boolean onBackPressed() {
        if (mBehavior == null) {
            return false;
        }

        if (mBehavior.isSheetExpanded()) {
            mBehavior.setSheetState(false);
            return true;
        }

        return false;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFriendItemsAdapter.setListener(this);
        mTenDips = Utils.dpsToPix(requireContext(), 10);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_friends_sheet, container, false);
        LinearLayoutManager llm = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        mBinding.avatars.setLayoutManager(llm);
        mBinding.avatars.setAdapter(mAvatarsAdapter);

        mBinding.friendItems.setAdapter(mFriendItemsAdapter);

        mBinding.toggle.setOnClickListener(v -> toggleFriendsSheet());

        final FriendsSheetLayout root = (FriendsSheetLayout) mBinding.getRoot();
        root.setElevation(Utils.dpsToPix(requireContext(), 36));
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!mInitialLayoutDone) {
                    mInitialLayoutDone = true;
                    int seventyTwo = Utils.dpsToPix(requireContext(), 72);
                    int transY = root.getHeight() - seventyTwo;
                    root.hiddenStateTranslationY = transY;
                    root.setTranslationY(transY);
                    root.setTag(transY);
                    root.setVisibility(View.VISIBLE);
                }
            }
        });

        root.setVisibility(View.INVISIBLE);

        return root;
    }

    @Override
    public void onDestroyView() {
        mBinding = null;
        mBehavior = null;

        super.onDestroyView();
    }

    @Subscribe
    @Keep
    public void onFriendLocationUpdated(final FriendLocation loc) {
        mFriendItemsAdapter.setFriendLocation(requireContext(), loc);
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationSharingGranted(LocationSharingGranted grant) {
        App.runInBackground(() -> {
            FriendRecord friend = DB.get().getFriendByUserId(grant.userId);
            if (friend != null) {
                mAvatarsAdapter.addFriend(friend);
                App.runOnUiThread(() -> mFriendItemsAdapter.updateFriend(friend));
            }
        });
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationSharingRevoked(LocationSharingRevoked revoked) {
        App.runInBackground(() -> {
            DB db = DB.get();
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

    @UiThread
    private void promptToRemoveFriend(@NonNull final FriendRecord friend) {
        AlertDialog.Builder bldr = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        String msg = getString(R.string.remove_friend_prompt_msg, friend.user.username);
        bldr.setMessage(msg);
        bldr.setCancelable(true);
        bldr.setPositiveButton(R.string.remove_friend, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                removeFriend(friend);
            }
        });
        bldr.setNeutralButton(R.string.never_mind, null);
        bldr.show();
    }

    @Subscribe
    @Keep
    public void onFriendRemoved(FriendRemoved evt) {
        mFriendItemsAdapter.removeFriend(evt.friendId);
        mAvatarsAdapter.removeFriend(evt.friendId);
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
    public void onShowFriendInfoAction(@NonNull FriendRecord friend) {
        AlertDialog.Builder bldr = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        bldr.setTitle(friend.user.username);
        bldr.setMessage("Key:\n" + Hex.toHexString(friend.user.publicKey));
        bldr.setPositiveButton(R.string.ok, null);
        bldr.setNegativeButton(R.string.remove_friend, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                promptToRemoveFriend(friend);
            }
        });
        bldr.setCancelable(true);
        bldr.show();
    }

    @Override
    public void onStart() {
        super.onStart();

        App.registerOnBus(this);
        final DB db = DB.get();
        App.runInBackground(() -> {
            ArrayList<FriendRecord> friends = db.getFriends();
            mAvatarsAdapter.setFriends(friends);
            mFriendItemsAdapter.setFriends(friends);
            for (FriendRecord record : friends) {
                FriendLocation loc = db.getFriendLocation(record.id);
                if (loc != null) {
                    App.runOnUiThread(() -> mFriendItemsAdapter.setFriendLocation(requireContext(), loc));
                }
            }
        });

        if (getActivity() instanceof AvatarsAdapter.AvatarsAdapterListener) {
            mAvatarsAdapter.setListener((AvatarsAdapter.AvatarsAdapterListener) getActivity());
        }

        mBehavior = MainLayout.registerFriendsSheet(this, mBinding.friendsSheet);
    }

    @Override
    public void onStop() {
        App.unregisterFromBus(this);
        mAvatarsAdapter.setListener(null);
        mBehavior = null;

        super.onStop();
    }

    @WorkerThread
    private void removeFriend(@NonNull FriendRecord friend) {
        Prefs prefs = Prefs.get(requireContext());
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(requireContext(), null, "How are you not logged in right now? (missing access token)");
            return;
        }
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            Utils.showStringAlert(requireContext(), null, "How are you not logged in right now? (missing key pair)");
            return;
        }

        try {
            DB.get().removeFriend(friend, requireContext());
        } catch (DB.DBException ex) {
            L.w("Error removing friend", ex);
            Crashlytics.logException(ex);
            Utils.showStringAlert(getContext(), null, "There was a problem removing this friend. Try again, and if it still fails, contact support.");
            return;
        }

        App.postOnBus(new FriendRemoved(friend.id, friend.user.id, friend.user.username));
    }

    @WorkerThread
    private void startSharingWith(@NonNull FriendRecord friend) {
        byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(sendingBoxId);
        // send the sending box id to the friend
        UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
        String errMsg = OscarClient.queueSendMessage(requireContext(), friend.user, comm, false, false);
        if (errMsg != null) {
            Crashlytics.logException(new RuntimeException(errMsg));
            return;
        }

        // add this to our database
        DB db = DB.get();
        try {
            db.startSharingWith(friend.user, sendingBoxId, requireContext());
            AvatarManager.sendAvatarToUser(requireContext(), friend.user);
        } catch (DB.DBException ex) {
            Crashlytics.logException(ex);
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), R.string.toast_enable_sharing_error_msg, Toast.LENGTH_SHORT).show();
                    mFriendItemsAdapter.reloadFriend(friend.id);
                }
            });
        } catch (IOException ex) {
            Crashlytics.logException(ex);
        }

        FriendRecord updatedFriend = db.getFriendById(friend.id);
        if (updatedFriend == null) {
            L.w("Somebody deleted a friend while we were enabling sharing with them.");
            return;
        }
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                mFriendItemsAdapter.updateFriend(updatedFriend);
            }
        });
    }

    @WorkerThread
    private void stopSharingWith(@NonNull FriendRecord friend) {
        // remove the sending box id from the database
        DB db = DB.get();
        try {
            db.stopSharingWith(friend.user, requireContext());
        } catch (DB.DBException ex) {
            Crashlytics.logException(ex);
            App.runOnUiThread(new UiRunnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), R.string.toast_disable_sharing_error_msg, Toast.LENGTH_SHORT).show();
                    mFriendItemsAdapter.reloadFriend(friend.id);
                }
            });
        }

        UserComm comm = UserComm.newLocationSharingRevocation();
        String errMsg = OscarClient.queueSendMessage(requireContext(), friend.user, comm, false, false);
        if (errMsg != null) {
            Crashlytics.logException(new RuntimeException(errMsg));
        }

        // grab the updated friend record, and apply it on our adapter
        FriendRecord updatedFriend = db.getFriendById(friend.id);
        if (updatedFriend == null) {
            // somebody deleted the friend while we were disabling sharing?
            L.w("Somebody deleted a friend while we were disabling sharing with them");
            return;
        }
        App.runOnUiThread(new UiRunnable() {
            @Override
            public void run() {
                mFriendItemsAdapter.updateFriend(updatedFriend);
            }
        });
    }

    private void toggleFriendsSheet() {
        if (mBehavior == null) {
            L.w("How did toggleFriendsSheet get called when the behavior was null?");
            return;
        }

        mBehavior.setSheetState(!mBehavior.isSheetExpanded());
    }

    public void onSlide(float slideOffset) {
        float height = mBinding.avatars.getHeight();
        mBinding.avatars.setTranslationY(height*slideOffset*-3.0f);

        float btnRot;
        float btnTrY;
        if (slideOffset >= 0.75) {
            float progress = (slideOffset - 0.75f)*4.0f;
            btnRot = 180.0f * progress;
            btnTrY = progress * mTenDips;
        } else {
            btnRot = 0;
            btnTrY = 0;
        }
        mBinding.toggle.setRotation(btnRot);
        mBinding.toggle.setTranslationY(btnTrY);

        float maxTrY = mBinding.toggle.getTop(); // top right of the toggle button
        maxTrY += mBinding.toggle.getHeight()/2.0f;  // to calculate the center of the toggle
        maxTrY += mTenDips; // because at the end of the animation the toggle goes down 10dp
        maxTrY -= mBinding.title.getTop() + mBinding.title.getHeight()/2.0f;    // to calculate the actual difference with the center of the title
        mBinding.title.setTranslationY(maxTrY * slideOffset);
    }
}
