package com.tacz.guns.client.resource.serialize;

import com.google.gson.*;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.client.resource.ClientAssetRegistries;
import com.tacz.guns.resource.serialize.LegacyPackCodecs;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;

public class ItemStackSerializer implements JsonDeserializer<ItemStack> {
    private final java.util.function.Supplier<DynamicOps<JsonElement>> ops;

    public ItemStackSerializer() {
        this.ops = () -> ClientAssetRegistries.current().createSerializationContext(JsonOps.INSTANCE);
    }

    public ItemStackSerializer(DynamicOps<JsonElement> ops) { this.ops = () -> ops; }

    @Override
    public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            return LegacyPackCodecs.ITEM_STACK.parse(ops.get(), jsonObject).getOrThrow(JsonParseException::new);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a ItemStack because it's not an object");
        }
    }
}
