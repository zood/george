package io.pijun.george.api.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import io.pijun.george.api.OscarClient;

public class QueueConverter implements PersistentQueue.Converter<OscarTask> {

    @Override
    public OscarTask deserialize(byte[] bytes) {
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
            case DropPackageTask.NAME:
                return OscarClient.sGson.fromJson(root, DropPackageTask.class);
            case SendMessageTask.NAME:
                return OscarClient.sGson.fromJson(root, SendMessageTask.class);
            default:
                throw new RuntimeException("Unknown api method: " + apiMethod);
        }
    }

    @Override
    public byte[] serialize(OscarTask task) {
        switch (task.apiMethod) {
            case AddFcmTokenTask.NAME:
                AddFcmTokenTask aftt = (AddFcmTokenTask) task;
                return OscarClient.sGson.toJson(aftt).getBytes();
            case DeleteFcmTokenTask.NAME:
                DeleteFcmTokenTask dftt = (DeleteFcmTokenTask) task;
                return OscarClient.sGson.toJson(dftt).getBytes();
            case DeleteMessageTask.NAME:
                DeleteMessageTask dmt = (DeleteMessageTask) task;
                return OscarClient.sGson.toJson(dmt).getBytes();
            case DropPackageTask.NAME:
                DropPackageTask dpt = (DropPackageTask) task;
                return OscarClient.sGson.toJson(dpt).getBytes();
            case SendMessageTask.NAME:
                SendMessageTask smt = (SendMessageTask) task;
                return OscarClient.sGson.toJson(smt).getBytes();
            default:
                throw new RuntimeException("Unknown task type: " + task.apiMethod);
        }
    }
}
