package com.tacz.guns.compat.kubejs.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.resource.serialize.LegacyPackCodecs;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.server.ServerLifecycleHooks;

/** TACZ's recipe JSON boundary; independent of a particular KubeJS binary/API version. */
public final class ScriptRecipeData {
    private ScriptRecipeData() {}

    public static HolderLookup.Provider currentRegistries() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) : server.registryAccess();
    }

    public static Ingredient readIngredient(JsonElement json, HolderLookup.Provider registries) {
        return LegacyPackCodecs.INGREDIENT.parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow(JsonParseException::new);
    }

    public static JsonElement writeIngredient(Ingredient ingredient, HolderLookup.Provider registries) {
        return Ingredient.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), ingredient)
                .getOrThrow(JsonParseException::new);
    }

    public static ItemStack readItem(JsonObject json, JsonElement replacementNbt, HolderLookup.Provider registries) {
        JsonObject input = json;
        if (replacementNbt != null && !replacementNbt.isJsonNull()) {
            if (!json.has("item")) throw new JsonParseException("Root nbt replacement requires legacy item syntax; use components for a native item");
            // The old deprecated recipe adapter called setTag, replacing the item's entire NBT.
            // Apply that replacement before data fixing, so damage/name/etc. become components.
            input = json.deepCopy();
            input.add("nbt", replacementNbt.deepCopy());
        }
        return LegacyPackCodecs.ITEM_STACK.parse(registries.createSerializationContext(JsonOps.INSTANCE), input)
                .getOrThrow(JsonParseException::new);
    }

    public static JsonObject writeItem(ItemStack stack, HolderLookup.Provider registries) {
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack)
                .getOrThrow(JsonParseException::new).getAsJsonObject();
    }
}
