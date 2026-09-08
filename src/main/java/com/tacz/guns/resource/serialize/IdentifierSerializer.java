package com.tacz.guns.resource.serialize;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Type;

/** Retains TACZ's string resource IDs after removal of Minecraft's Gson adapter. */
public final class IdentifierSerializer implements JsonSerializer<Identifier>, JsonDeserializer<Identifier> {
    @Override
    public Identifier deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected a resource identifier string, got " + json);
        }
        Identifier id = Identifier.tryParse(json.getAsString());
        if (id == null) {
            throw new JsonParseException("Invalid resource identifier: " + json.getAsString());
        }
        return id;
    }

    @Override
    public JsonElement serialize(Identifier id, Type type, JsonSerializationContext context) {
        return new JsonPrimitive(id.toString());
    }
}
