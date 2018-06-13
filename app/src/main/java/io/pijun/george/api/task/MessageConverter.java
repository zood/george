package io.pijun.george.api.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import io.pijun.george.Constants;
import io.pijun.george.queue.PersistentQueue;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarClient;

public class MessageConverter implements PersistentQueue.Converter<Message> {

    @Override
    public Message deserialize(byte[] bytes) {
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes));
        JsonElement root = new JsonParser().parse(reader);
        return OscarClient.sGson.fromJson(root, Message.class);
    }

    @Override
    public byte[] serialize(Message msg) {
        return OscarClient.sGson.toJson(msg).getBytes(Constants.utf8);
    }
}
