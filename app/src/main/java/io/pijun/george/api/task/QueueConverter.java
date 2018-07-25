package io.pijun.george.api.task;

import android.support.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import io.pijun.george.Constants;
import io.pijun.george.queue.PersistentQueue;
import io.pijun.george.api.OscarClient;

public class QueueConverter implements PersistentQueue.Converter<OscarTask> {

    @Override
    public OscarTask deserialize(@NonNull byte[] bytes) {
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes));
        JsonElement root = new JsonParser().parse(reader);
        String apiMethod = root.getAsJsonObject().get("api_method").getAsString();
        switch (apiMethod) {
            case AddFcmTokenTask.NAME:
                return OscarClient.sGson.fromJson(root, AddFcmTokenTask.class);
            case DeleteFcmTokenTask.NAME:
                return OscarClient.sGson.fromJson(root, DeleteFcmTokenTask.class);
            case DeleteMessageTask.NAME:
                return OscarClient.sGson.fromJson(root, DeleteMessageTask.class);
            case DropMultiplePackagesTask.NAME:
                return OscarClient.sGson.fromJson(root, DropMultiplePackagesTask.class);
            case DropPackageTask.NAME:
                return OscarClient.sGson.fromJson(root, DropPackageTask.class);
            case SendMessageTask.NAME:
                return OscarClient.sGson.fromJson(root, SendMessageTask.class);
            default:
                throw new RuntimeException("Unknown api method: " + apiMethod);
        }
    }

    @NonNull
    @Override
    public byte[] serialize(@NonNull OscarTask task) {
        switch (task.apiMethod) {
            case AddFcmTokenTask.NAME:
                AddFcmTokenTask aftt = (AddFcmTokenTask) task;
                return OscarClient.sGson.toJson(aftt).getBytes(Constants.utf8);
            case DeleteFcmTokenTask.NAME:
                DeleteFcmTokenTask dftt = (DeleteFcmTokenTask) task;
                return OscarClient.sGson.toJson(dftt).getBytes(Constants.utf8);
            case DeleteMessageTask.NAME:
                DeleteMessageTask dmt = (DeleteMessageTask) task;
                return OscarClient.sGson.toJson(dmt).getBytes(Constants.utf8);
            case DropMultiplePackagesTask.NAME:
                DropMultiplePackagesTask dmpt = (DropMultiplePackagesTask) task;
                return OscarClient.sGson.toJson(dmpt).getBytes(Constants.utf8);
            case DropPackageTask.NAME:
                DropPackageTask dpt = (DropPackageTask) task;
                return OscarClient.sGson.toJson(dpt).getBytes(Constants.utf8);
            case SendMessageTask.NAME:
                SendMessageTask smt = (SendMessageTask) task;
                return OscarClient.sGson.toJson(smt).getBytes(Constants.utf8);
            default:
                throw new RuntimeException("Unknown task type: " + task.apiMethod);
        }
    }
}
