package com.tacz.guns.resource.serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.UnaryOperator;

/** Reads 1.20.1 pack syntax with the caller's target registry context; writes target syntax. */
public final class LegacyPackCodecs {
    public static final int SOURCE_DATA_VERSION = 3465;
    public static final Codec<Ingredient> INGREDIENT = compatible(Ingredient.CODEC, LegacyPackCodecs::upgradeIngredient);
    public static final Codec<ItemStack> ITEM_STACK = compatible(ItemStack.CODEC, LegacyPackCodecs::upgradeItemStack);

    private LegacyPackCodecs() {}

    private static <A> Codec<A> compatible(Codec<A> target, UnaryOperator<JsonElement> convert) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = convert.apply(ops.convertTo(JsonOps.INSTANCE, input));
                    return target.decode(ops, JsonOps.INSTANCE.convertTo(ops, json));
                } catch (RuntimeException exception) {
                    return DataResult.error(() -> "Invalid legacy pack value: " + exception.getMessage());
                }
            }

            @Override
            public <T> DataResult<T> encode(A value, DynamicOps<T> ops, T prefix) {
                return target.encode(value, ops, prefix);
            }
        };
    }

    public static JsonElement upgradeIngredient(JsonElement value) {
        if (value.isJsonArray()) {
            JsonArray converted = new JsonArray();
            value.getAsJsonArray().forEach(element -> converted.add(upgradeIngredient(element)));
            if (converted.isEmpty()) throw new IllegalArgumentException("Ingredient alternatives cannot be empty");
            JsonObject compound = new JsonObject();
            compound.addProperty("type", "forge:compound");
            compound.add("children", converted);
            return compound;
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            // Forge custom ingredients retain their serializer discriminator and full payload.
            if (object.has("type")) return object;
            if (object.size() == 1 && object.has("item")) return object.get("item");
            if (object.size() == 1 && object.has("tag")) return new JsonPrimitive("#" + object.get("tag").getAsString());
            throw new IllegalArgumentException("Ingredient needs one item or tag, or a Forge type");
        }
        return value;
    }

    public static CompoundTag readNbt(JsonElement value) {
        try {
            return TagParser.parseCompoundFully(value.isJsonPrimitive() ? value.getAsString() : value.toString());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalArgumentException("Malformed recipe NBT", exception);
        }
    }

    public static JsonElement upgradeItemStack(JsonElement value) {
        JsonObject object = value.getAsJsonObject();
        if (!object.has("item")) return object;
        for (String key : object.keySet()) {
            if (!java.util.Set.of("item", "count", "nbt").contains(key)) {
                throw new IllegalArgumentException("Unrecognized legacy item field: " + key);
            }
        }
        int count = object.has("count") ? object.get("count").getAsInt() : 1;
        if (count < 1 || count > 99) throw new IllegalArgumentException("Recipe result count must be 1..99");
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id", object.get("item").getAsString());
        legacy.putByte("Count", (byte) count);
        if (object.has("nbt")) legacy.put("tag", readNbt(object.get("nbt")));
        legacy.getCompound("tag").ifPresent(com.tacz.guns.item.GunTooltipPart::preserveLegacy);
        // Minecraft's own item fixes migrate display, damage, enchantments, entity data,
        // containers and unknown custom fields. The source pack JSON is never mutated.
        var updated = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, legacy),
                SOURCE_DATA_VERSION, SharedConstants.getCurrentVersion().dataVersion().version());
        return updated.convert(JsonOps.INSTANCE).getValue();
    }
}
