package xyz.zood.george;

import android.content.Context;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.security.SecureRandom;
import java.util.ArrayList;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.pijun.george.Constants;
import io.pijun.george.database.DB;
import io.pijun.george.database.FriendLocation;
import io.pijun.george.database.FriendRecord;
import io.pijun.george.database.LimitedShare;
import io.pijun.george.database.MovementType;
import io.pijun.george.database.Snapshot;
import io.pijun.george.database.UserRecord;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DBTest {

    private static long u1Id = 0;
    private static byte[] u1UserId = new byte[Constants.USER_ID_LENGTH];
    private static String u1Username = "\uD83C\uDF7F alpha \uD83C\uDF7F";
    private static byte[] u1PublicKey = new byte[Constants.PUBLIC_KEY_LENGTH];
    private static long f1Id = 0;
    private static byte[] f1Sid = new byte[Constants.DROP_BOX_ID_LENGTH];
    private static byte[] f1Rid = new byte[Constants.DROP_BOX_ID_LENGTH];

    private static long u2Id = 0;
    private static byte[] u2UserId = new byte[Constants.USER_ID_LENGTH];
    private static String u2Username = "\uD83E\uDD57 beta \uD83E\uDD57";
    private static byte[] u2PublicKey = new byte[Constants.PUBLIC_KEY_LENGTH];
    private static long f2Id = 0;
    private static byte[] f2Sid = new byte[Constants.DROP_BOX_ID_LENGTH];

    private static long u3Id = 0;
    private static byte[] u3UserId = new byte[Constants.USER_ID_LENGTH];
    private static String u3Username = "\uD83C\uDF5A delta \uD83C\uDF5A";
    private static byte[] u3PublicKey = new byte[Constants.PUBLIC_KEY_LENGTH];
    private static long f3Id = 0;
    private static byte[] f3Rid = new byte[Constants.DROP_BOX_ID_LENGTH];

    private static Context ctx;

    @BeforeClass
    public static void setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().getContext();
        DB.init(ctx, true);

        SecureRandom rand = new SecureRandom();
        rand.nextBytes(u1UserId);
        rand.nextBytes(u1PublicKey);
        rand.nextBytes(f1Sid);
        rand.nextBytes(f1Rid);

        rand.nextBytes(u2UserId);
        rand.nextBytes(u2PublicKey);
        rand.nextBytes(f2Sid);

        rand.nextBytes(u3UserId);
        rand.nextBytes(u3PublicKey);
        rand.nextBytes(f3Rid);
    }

    @Test
    public void test010AddUser() {
        UserRecord u1, u2, u3;
        try {
            u1 = DB.get().addUser(u1UserId, u1Username, u1PublicKey);
            u2 = DB.get().addUser(u2UserId, u2Username, u2PublicKey);
            u3 = DB.get().addUser(u3UserId, u3Username, u3PublicKey);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        assertNotNull(u1);
        assertNotNull(u2);
        assertNotNull(u3);
        // make sure the values we get back, are the same as what we put in
        assertEquals(u1Username, u1.username);
        assertArrayEquals(u1UserId, u1.userId);
        assertArrayEquals(u1PublicKey, u1.publicKey);
        // and make sure we received a local id for this user
        if (u1.id < 1) {
            fail("Invalid local id: " + u1.id);
            return;
        }
        u1Id = u1.id;

        // Store the ids for u2 and u3
        if (u2.id < 1) {
            fail("Invalid local id: " + u2.id);
            return;
        }
        u2Id = u2.id;

        if (u3.id < 1) {
            fail("Invalid local id: " + u3.id);
        }
        u3Id = u3.id;

        assertNotEquals(u1Id, u2Id);
        assertNotEquals(u2Id, u3Id);
        assertNotEquals(u3Id, u1Id);
    }

    @Test
    public void test020GetUserByLocalId() {
        UserRecord u1 = DB.get().getUser(u1Id);
        assertNotNull(u1);

        assertEquals(u1Id, u1.id);
        assertArrayEquals(u1UserId, u1.userId);
        assertEquals(u1Username, u1.username);
        assertArrayEquals(u1PublicKey, u1.publicKey);
    }

    @Test
    public void test030GetUserByUsername() {
        UserRecord u1 = DB.get().getUser(u1Username);
        assertNotNull(u1);

        assertEquals(u1Id, u1.id);
        assertArrayEquals(u1UserId, u1.userId);
        assertEquals(u1Username, u1.username);
        assertArrayEquals(u1PublicKey, u1.publicKey);

        assertNotNull(DB.get().getUser(u2Username));
        assertNotNull(DB.get().getUser(u3Username));
    }

    @Test
    public void test040GetUserByUserId() {
        UserRecord u1 = DB.get().getUser(u1UserId);
        assertNotNull(u1);

        assertEquals(u1Id, u1.id);
        assertArrayEquals(u1UserId, u1.userId);
        assertEquals(u1Username, u1.username);
        assertArrayEquals(u1PublicKey, u1.publicKey);
    }

    @Test
    public void test050FullSharing() {
        UserRecord u1 = DB.get().getUser(u1Id);
        assertNotNull(u1);

        try {
            DB.get().sharingGrantedBy(u1, f1Rid);
            DB.get().startSharingWith(u1, f1Sid);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        FriendRecord f1 = DB.get().getFriendByUserId(u1Id);
        assertNotNull(f1);
        if (f1.id < 1) {
            fail("Invalid friend id: " + f1.id);
            return;
        }
        f1Id = f1.id;
        assertEquals(u1Id, f1.userId);
        assertArrayEquals(f1Rid, f1.receivingBoxId);
        assertArrayEquals(f1Sid, f1.sendingBoxId);
    }

    @Test
    public void test060SendingOnly() {
        UserRecord u2 = DB.get().getUser(u2Id);
        assertNotNull(u2);

        try {
            DB.get().startSharingWith(u2, f2Sid);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        FriendRecord f2 = DB.get().getFriendByUserId(u2Id);
        assertNotNull(f2);
        f2Id = f2.id;
        assertEquals(u2Id, f2.userId);
        assertArrayEquals(f2Sid, f2.sendingBoxId);
        assertNull(f2.receivingBoxId);
    }

    @Test
    public void test070ReceivingOnly() {
        UserRecord u3 = DB.get().getUser(u3Id);
        assertNotNull(u3);

        try {
            DB.get().sharingGrantedBy(u3, f3Rid);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        FriendRecord f3 = DB.get().getFriendByUserId(u3Id);
        assertNotNull(f3);
        f3Id = f3.id;
        assertEquals(u3Id, f3.userId);
        assertArrayEquals(f3Rid, f3.receivingBoxId);
        assertNull(f3.sendingBoxId);
    }

    @Test
    public void test080GetFriends() {
        ArrayList<FriendRecord> friends = DB.get().getFriends();
        assertNotNull(friends);
        assertEquals(3, friends.size());
    }

    @Test
    public void test090GetFriendById() {
        FriendRecord f1 = DB.get().getFriendById(f1Id);
        assertNotNull(f1);

        FriendRecord f2 = DB.get().getFriendById(f2Id);
        assertNotNull(f2);

        FriendRecord f3 = DB.get().getFriendById(f3Id);
        assertNotNull(f3);
    }

    @Test
    public void test100GetFriendByReceivingBoxId() {
        FriendRecord f1 = DB.get().getFriendByReceivingBoxId(f1Rid);
        assertNotNull(f1);
        assertEquals(f1Id, f1.id);

        FriendRecord f3 = DB.get().getFriendByReceivingBoxId(f3Rid);
        assertNotNull(f3);
        assertEquals(f3Id, f3.id);

        FriendRecord bad = DB.get().getFriendByReceivingBoxId(new byte[Constants.DROP_BOX_ID_LENGTH]);
        assertNull(bad);
    }

    @Test
    public void test110StopSending() {
        UserRecord u2 = DB.get().getUser(u2Id);
        assertNotNull(u2);

        try {
            DB.get().stopSharingWith(u2);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        FriendRecord f2 = DB.get().getFriendById(f2Id);
        assertNotNull(f2);
        assertNull(f2.sendingBoxId);
        assertNull(f2.receivingBoxId);
    }

    @Test
    public void test120StopReceiving() {
        UserRecord u3 = DB.get().getUser(u3Id);
        assertNotNull(u3);

        DB.get().sharingRevokedBy(u3);

        FriendRecord f3 = DB.get().getFriendById(f3Id);
        assertNotNull(f3);
        assertNull(f3.receivingBoxId);
        assertNull(f3.sendingBoxId);
    }

    @Test
    public void test130FriendLocation() {
        FriendLocation loc = DB.get().getFriendLocation(f1Id);
        assertNull(loc);

        try {
            DB.get().setFriendLocation(f1Id, 3, 3, 4, 1.0f, 8.0f, 90.0f, MovementType.Bicycle.val, 82, true);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        loc = DB.get().getFriendLocation(f1Id);
        assertNotNull(loc);

        assertEquals(3, loc.latitude, 0.0001);
        assertEquals(3, loc.longitude, 0.0001);
        assertEquals(4, loc.time);
        assertNotNull(loc.accuracy);
        assertEquals(1.0f, loc.accuracy, 0.0001);
        assertNotNull(loc.speed);
        assertEquals(8.0f, loc.speed, 0.0001);
        assertNotNull(loc.bearing);
        assertEquals(90.0f, loc.bearing, 0.0001);
    }

    @Test
    public void test150LimitedShare() {
        DB db = DB.get();
        LimitedShare ls = db.getLimitedShare();
        assertNull(ls);

        byte[] pubKey = new byte[Constants.PUBLIC_KEY_LENGTH];
        byte[] boxId = new byte[Constants.DROP_BOX_ID_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(pubKey);
        random.nextBytes(boxId);
        try {
            db.addLimitedShare(pubKey, boxId);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        ls = db.getLimitedShare();
        assertNotNull(ls);

        assertArrayEquals(pubKey, ls.publicKey);
        assertArrayEquals(boxId, ls.sendingBoxId);

        db.deleteLimitedShares();
        ls = db.getLimitedShare();
        assertNull(ls);
    }

    @Test
    public void test140DeleteAllData() {
        DB.get().deleteAllData();

        assertEquals(0, DB.get().getFriends().size());
        assertNull(DB.get().getLimitedShare());
        assertNull(DB.get().getUser(u1Id));
        assertNull(DB.get().getUser(u2Id));
        assertNull(DB.get().getUser(u3Id));
    }

    @Test
    public void test150Restore() {
        Snapshot snapshot = new Snapshot();
        String[] usernames = new String[]{"alpha", "beta", "delta"};
        SecureRandom random = new SecureRandom();
        for (String uname : usernames) {
            Snapshot.User u = new Snapshot.User();
            u.username = uname;
            u.userId = new byte[Constants.USER_ID_LENGTH];
            u.publicKey = new byte[Constants.PUBLIC_KEY_LENGTH];
            random.nextBytes(u.userId);
            random.nextBytes(u.publicKey);
            snapshot.users.add(u);
        }

        for (Snapshot.User u : snapshot.users) {
            Snapshot.Friend f = new Snapshot.Friend();
            f.userId = u.userId;
            f.receivingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
            f.sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
            snapshot.friends.add(f);
        }

        snapshot.schemaVersion = 1;
        snapshot.timestamp = System.currentTimeMillis();

        DB db = DB.get();
        try {
            db.restoreDatabase(ctx, snapshot);
        } catch (DB.DBException ex) {
            fail(ex.getLocalizedMessage());
            return;
        }

        // make sure all the data is in there
        for (Snapshot.User u : snapshot.users) {
            assertNotNull(db.getUser(u.userId));
        }
        for (Snapshot.Friend f : snapshot.friends) {
            assertNotNull(db.getFriendByReceivingBoxId(f.receivingBoxId));
        }
    }

}
