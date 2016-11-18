package io.pijun.george.api.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.squareup.tape.FileObjectQueue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import io.pijun.george.L;
import io.pijun.george.api.OscarClient;

public class QueueConverter implements FileObjectQueue.Converter<OscarTask> {

    @Override
    public OscarTask from(byte[] bytes) throws IOException {
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes));
        JsonElement root = new JsonParser().parse(reader);
        String apiMethod = root.getAsJsonObject().get("api_method").getAsString();
        switch (apiMethod) {
            case DeleteMessageTask.NAME:
                return OscarClient.sGson.fromJson(root, DeleteMessageTask.class);
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
            case DeleteMessageTask.NAME:
                DeleteMessageTask dmt = (DeleteMessageTask) task;
                OscarClient.sGson.toJson(dmt, writer);
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
