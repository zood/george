package io.pijun.george.models;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.firebase.crash.FirebaseCrash;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import io.pijun.george.Hex;
import io.pijun.george.api.OscarClient;

public class Snapshot {

    public static class Friend {
        public byte[] userId;
        public byte[] sendingBoxId;
        public byte[] receivingBoxId;
    }

    public static class Request {
        public byte[] userId;
        public long sentDate;
        public String response;

        @Override
        public String toString() {
            return "Request{" +
                    "userId=" + Hex.toHexString(userId) +
                    ", sentDate=" + sentDate +
                    ", response='" + response + '\'' +
                    '}';
        }
    }

    public static class User {
        public byte[] userId;
        public byte[] publicKey;
        public String username;
    }

    @NonNull
    public ArrayList<Friend> friends = new ArrayList<>();
    @NonNull
    public ArrayList<Request> incomingRequests = new ArrayList<>();
    @NonNull
    public ArrayList<Request> outgoingRequests = new ArrayList<>();
    @NonNull
    public ArrayList<User> users = new ArrayList<>();

    public int schemaVersion;
    public long timestamp;

    @NonNull
    public byte[] toJson() {
        String s = OscarClient.sGson.toJson(this);
        return s.getBytes();
    }

    @Nullable
    public static Snapshot fromJson(@NonNull byte[] bytes) {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(bytes));
        Snapshot snapshot;
        try {
            snapshot = OscarClient.sGson.fromJson(isr, Snapshot.class);
        } catch (Throwable t) {
            FirebaseCrash.report(t);
            return null;
        }
        return snapshot;
    }

}
