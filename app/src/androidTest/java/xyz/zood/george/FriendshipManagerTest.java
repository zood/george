package xyz.zood.george;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import io.pijun.george.Constants;
import io.pijun.george.Sodium;
import io.pijun.george.api.task.OscarTask;
import io.pijun.george.api.task.QueueConverter;
import io.pijun.george.api.task.SendMessageTask;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.UserRecord;
import io.pijun.george.queue.PersistentQueue;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FriendshipManagerTest {

    @NonNull
    private static PersistentQueue<OscarTask> newQueue() {
        return new PersistentQueue<>(requireContext(), null, new QueueConverter());
    }

    @NonNull
    private static Context requireContext() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testAddFriendMissingUser() {
        String token = "accessToken";
        KeyPair kp = new KeyPair();
        Sodium.generateKeyPair(kp);
        DB.init(requireContext(), true);
        PersistentQueue<OscarTask> queue = newQueue();
        FriendshipManager mgr = new FriendshipManager(requireContext(), DB.get(), queue, token, kp);

        // add a fried
        final FriendshipManager.AddFriendResult[] actualResult = new FriendshipManager.AddFriendResult[1];
        final String[] actualExtraInfo = new String[1];
        mgr.addFriend("dannyocean", new FriendshipManager.OnAddFriendFinishedListener() {
            @Override
            public void onAddFriendFinished(@NonNull FriendshipManager.AddFriendResult result, @Nullable String extraInfo) {
                actualResult[0] = result;
                actualExtraInfo[0] = extraInfo;
            }
        });
        assertEquals(FriendshipManager.AddFriendResult.UserNotFound, actualResult[0]);
        assertNull(actualExtraInfo[0]);

        // no messages should have been sent
        assertNull(queue.peek());
    }

    @Test
    public void testAddFriendNewFriend() throws DB.DBException {
        String token = "accessToken";
        KeyPair kp = new KeyPair();
        Sodium.generateKeyPair(kp);
        DB.init(requireContext(), true);
        PersistentQueue<OscarTask> queue = newQueue();
        FriendshipManager mgr = new FriendshipManager(requireContext(), DB.get(), queue, token, kp);
        String username = "dannyocean";

        DB db = DB.get();
        byte[] friendUserId = new byte[Constants.USER_ID_LENGTH];
        new SecureRandom().nextBytes(friendUserId);
        KeyPair friendKp = new KeyPair();
        Sodium.generateKeyPair(friendKp);
        db.addUser(friendUserId, username, friendKp.publicKey);

        final FriendshipManager.AddFriendResult[] actualResult = new FriendshipManager.AddFriendResult[1];
        final String[] actualExtraInfo = new String[1];
        mgr.addFriend(username, new FriendshipManager.OnAddFriendFinishedListener() {
            @Override
            public void onAddFriendFinished(@NonNull FriendshipManager.AddFriendResult result, @Nullable String extraInfo) {
                actualResult[0] = result;
                actualExtraInfo[0] = extraInfo;
            }
        });
        assertEquals(FriendshipManager.AddFriendResult.Success, actualResult[0]);
        assertNull(actualExtraInfo[0]);

        // 1 message should have been sent
        OscarTask task = queue.peek();
        assertNotNull(task);
        assertEquals(SendMessageTask.NAME, task.apiMethod);
        assertEquals(token, task.accessToken);

        // the db should list this person as a friend
        ArrayList<FriendRecord> friends = db.getFriends();
        assertEquals(1, friends.size());
        FriendRecord f = friends.get(0);
        assertEquals(username, f.user.username);
    }

    @Test
    public void removeFriend() throws DB.DBException {
        String token = "accessToken";
        KeyPair kp = new KeyPair();
        Sodium.generateKeyPair(kp);
        DB.init(requireContext(), true);
        PersistentQueue<OscarTask> queue = newQueue();
        FriendshipManager mgr = new FriendshipManager(requireContext(), DB.get(), queue, token, kp);
        String username = "dannyocean";

        DB db = DB.get();
        byte[] friendUserId = new byte[Constants.USER_ID_LENGTH];
        new SecureRandom().nextBytes(friendUserId);
        KeyPair friendKp = new KeyPair();
        Sodium.generateKeyPair(friendKp);
        UserRecord dannyOcean = db.addUser(friendUserId, username, friendKp.publicKey);

        byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(boxId);
        db.sharingGrantedBy(dannyOcean, boxId);
        FriendRecord friend = db.getFriendByUserId(dannyOcean.id);
        assertNotNull(friend);

        AtomicBoolean result = new AtomicBoolean(false);
        mgr.removeFriend(friend, new FriendshipManager.OnRemoveFriendFinishedListener() {
            @Override
            public void onRemoveFriendFinished(boolean success) {
                result.set(success);
            }
        });
        assertTrue(result.get());

        // we should no longer find a friend record for them
        friend = db.getFriendByUserId(dannyOcean.id);
        assertNull(friend);
    }

    @Test
    public void startSharingWith() throws DB.DBException {
        String token = "accessToken";
        KeyPair kp = new KeyPair();
        Sodium.generateKeyPair(kp);
        DB.init(requireContext(), true);
        PersistentQueue<OscarTask> queue = newQueue();
        FriendshipManager mgr = new FriendshipManager(requireContext(), DB.get(), queue, token, kp);
        String username = "dannyocean";

        DB db = DB.get();
        byte[] friendUserId = new byte[Constants.USER_ID_LENGTH];
        new SecureRandom().nextBytes(friendUserId);
        KeyPair friendKp = new KeyPair();
        Sodium.generateKeyPair(friendKp);
        UserRecord dannyOcean = db.addUser(friendUserId, username, friendKp.publicKey);

        byte[] receivingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(receivingBoxId);
        db.sharingGrantedBy(dannyOcean, receivingBoxId);
        FriendRecord friend = db.getFriendByUserId(dannyOcean.id);
        assertNotNull(friend);

        AtomicBoolean sharingResult = new AtomicBoolean(false);
        mgr.startSharingWith(friend, new FriendshipManager.OnShareOpFinishedListener() {
            @Override
            public void onShareOpFinished(boolean success) {
                sharingResult.set(success);
            }
        });
        assertTrue(sharingResult.get());

        // make sure the sending box id was added to the database
        friend = db.getFriendById(friend.id);
        assertNotNull(friend);
        assertNotNull(friend.sendingBoxId);
    }

    @Test
    public void stopSharingWith() throws DB.DBException {
        String token = "accessToken";
        KeyPair kp = new KeyPair();
        Sodium.generateKeyPair(kp);
        DB.init(requireContext(), true);
        PersistentQueue<OscarTask> queue = newQueue();
        FriendshipManager mgr = new FriendshipManager(requireContext(), DB.get(), queue, token, kp);
        String username = "dannyocean";

        DB db = DB.get();
        byte[] friendUserId = new byte[Constants.USER_ID_LENGTH];
        new SecureRandom().nextBytes(friendUserId);
        KeyPair friendKp = new KeyPair();
        Sodium.generateKeyPair(friendKp);
        UserRecord dannyOcean = db.addUser(friendUserId, username, friendKp.publicKey);

        byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        new SecureRandom().nextBytes(sendingBoxId);
        db.startSharingWith(dannyOcean, sendingBoxId);

        FriendRecord friend = db.getFriendByUserId(dannyOcean.id);
        assertNotNull(friend);

        AtomicBoolean sharingResult = new AtomicBoolean(false);
        mgr.stopSharingWith(friend, new FriendshipManager.OnShareOpFinishedListener() {
            @Override
            public void onShareOpFinished(boolean success) {
                sharingResult.set(success);
            }
        });
        assertTrue(sharingResult.get());

        // make sure there is no sending box id on the friend record anymore
        friend = db.getFriendById(friend.id);
        assertNotNull(friend);
        assertNull(friend.sendingBoxId);
    }
}