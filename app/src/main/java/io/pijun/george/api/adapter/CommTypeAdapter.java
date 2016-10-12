package io.pijun.george.api.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

import io.pijun.george.api.CommType;

public class CommTypeAdapter implements JsonSerializer<CommType>, JsonDeserializer<CommType> {
    @Override
    public CommType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return CommType.get(json.getAsString());
    }

    @Override
    public JsonElement serialize(CommType src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.val);
    }
}
