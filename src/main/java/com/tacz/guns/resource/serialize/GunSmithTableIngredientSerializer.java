package com.tacz.guns.resource.serialize;

import com.google.gson.*;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Type;

public class GunSmithTableIngredientSerializer implements JsonDeserializer<GunSmithTableIngredient> {
    @Override
    public GunSmithTableIngredient deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var ops = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)
                .createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        return GunSmithTableIngredient.CODEC.parse(ops, json).getOrThrow(JsonParseException::new);
    }
}
