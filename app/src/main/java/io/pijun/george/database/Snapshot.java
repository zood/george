package io.pijun.george.database;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.pijun.george.CloudLogger;
import io.pijun.george.Constants;
import io.pijun.george.api.OscarClient;

public class Snapshot {

    public static class Friend {
        public byte[] userId;
        public byte[] sendingBoxId;
        public byte[] receivingBoxId;
    }

    public static class User {
        public byte[] userId;
        public byte[] publicKey;
        public String username;
    }

    @NonNull
    public final ArrayList<Friend> friends = new ArrayList<>();
    @NonNull
    public final ArrayList<User> users = new ArrayList<>();
    @Nullable
    public byte[] avatar;

    public int schemaVersion;
    public long timestamp;

    @NonNull
    public byte[] toJson() {
        String s = OscarClient.sGson.toJson(this);
        return s.getBytes(Constants.utf8);
    }

    @Nullable
    public static Snapshot fromJson(@NonNull byte[] bytes) {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(bytes));
        Snapshot snapshot;
        try {
            snapshot = OscarClient.sGson.fromJson(isr, Snapshot.class);
        } catch (Throwable t) {
            CloudLogger.log(t);
            return null;
        }
        return snapshot;
    }

}
