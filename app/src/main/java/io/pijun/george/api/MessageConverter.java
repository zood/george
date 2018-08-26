package io.pijun.george.api;


import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import androidx.annotation.NonNull;
import io.pijun.george.Constants;
import io.pijun.george.queue.PersistentQueue;

public class MessageConverter implements PersistentQueue.Converter<Message> {

    @Override
    public Message deserialize(@NonNull byte[] bytes) {
        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes));
        JsonElement root = new JsonParser().parse(reader);
        return OscarClient.sGson.fromJson(root, Message.class);
    }

    @NonNull
    @Override
    public byte[] serialize(@NonNull Message msg) {
        return OscarClient.sGson.toJson(msg).getBytes(Constants.utf8);
    }
}
