package io.pijun.george.api.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.squareup.tape.FileObjectQueue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import io.pijun.george.api.Message;
import io.pijun.george.api.OscarClient;

public class MessageConverter implements FileObjectQueue.Converter<Message> {

    @Override
    public Message from(byte[] bytes) throws IOException {
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes));
        JsonElement root = new JsonParser().parse(reader);
        return OscarClient.sGson.fromJson(root, Message.class);
    }

    @Override
    public void toStream(Message msg, OutputStream os) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(os);
        OscarClient.sGson.toJson(msg, writer);
        writer.close();
    }

}
