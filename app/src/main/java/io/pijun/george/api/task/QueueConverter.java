package io.pijun.george.api.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.squareup.tape.FileObjectQueue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import io.pijun.george.api.OscarClient;

public class QueueConverter implements FileObjectQueue.Converter<OscarTask> {

    @Override
    public OscarTask from(byte[] bytes) throws IOException {
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
    public void toStream(OscarTask task, OutputStream os) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(os);
        switch (task.apiMethod) {
            case AddFcmTokenTask.NAME:
                AddFcmTokenTask aftt = (AddFcmTokenTask) task;
                OscarClient.sGson.toJson(aftt, writer);
                break;
            case DeleteFcmTokenTask.NAME:
                DeleteFcmTokenTask dftt = (DeleteFcmTokenTask) task;
                OscarClient.sGson.toJson(dftt, writer);
                break;
            case DeleteMessageTask.NAME:
                DeleteMessageTask dmt = (DeleteMessageTask) task;
                OscarClient.sGson.toJson(dmt, writer);
                break;
            case DropPackageTask.NAME:
                DropPackageTask dpt = (DropPackageTask) task;
                OscarClient.sGson.toJson(dpt, writer);
                break;
            case SendMessageTask.NAME:
                SendMessageTask smt = (SendMessageTask) task;
                OscarClient.sGson.toJson(smt, writer);
                break;
            default:
                throw new RuntimeException("Unknown task type: " + task.apiMethod);
        }
        writer.close();
    }

}
