package com.tacz.guns.resource.serialize;

import com.google.gson.*;
import com.tacz.guns.api.TaczConstants;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.crafting.result.RawGunTableResult;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.recipe.GunResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

import java.lang.reflect.Type;


public class GunSmithTableResultSerializer implements JsonDeserializer<GunSmithTableResult> {
    private static final Gson GUN_GSON = new GsonBuilder().registerTypeAdapter(Identifier.class, new IdentifierSerializer()).create();
    public static final Codec<GunSmithTableResult> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<GunSmithTableResult, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                var result = read(json, item -> LegacyPackCodecs.ITEM_STACK.parse(ops,
                        JsonOps.INSTANCE.convertTo(ops, item)).getOrThrow(JsonParseException::new));
                return DataResult.success(Pair.of(result, ops.empty()));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> "Invalid gunsmith result: " + exception.getMessage());
            }
        }

        @Override
        public <T> DataResult<T> encode(GunSmithTableResult value, DynamicOps<T> ops, T prefix) {
            JsonObject definition = value.getDefinition();
            if (definition != null) return Codec.PASSTHROUGH.encode(new Dynamic<>(JsonOps.INSTANCE, definition), ops, prefix);
            return ItemStack.CODEC.encodeStart(ops, value.getResult()).flatMap(item -> {
                JsonObject json = new JsonObject();
                json.addProperty("type", GunSmithTableResult.CUSTOM);
                json.addProperty("group", value.getGroup().toString());
                json.add("item", ops.convertTo(JsonOps.INSTANCE, item));
                return Codec.PASSTHROUGH.encode(new Dynamic<>(JsonOps.INSTANCE, json), ops, prefix);
            });
        }
    };

    @Override
    public GunSmithTableResult deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        return CODEC.parse(ops, json).getOrThrow(JsonParseException::new);
    }

    private static GunSmithTableResult read(JsonElement json, java.util.function.Function<JsonElement, ItemStack> itemDecoder) {
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            String typeName = GsonHelper.getAsString(jsonObject, "type");
            int count = 1;
            CompoundTag extraTag = null;
            Identifier tabOverride = null;
            if (jsonObject.has("count")) {
                count = GsonHelper.getAsInt(jsonObject, "count");
            }
            if (count < 1) throw new JsonSyntaxException("Result count must be positive");
            if (jsonObject.has("nbt")) {
                extraTag = LegacyPackCodecs.readNbt(jsonObject.get("nbt"));
            }
            if (jsonObject.has("group")) {
                String raw = GsonHelper.getAsString(jsonObject, "group");
                if (!raw.contains(":")) {
                    raw = TaczConstants.MOD_ID + ":" + raw;
                }
                tabOverride = Identifier.parse(raw);
            }

            GunSmithTableResult result;
            switch (typeName) {
                case GunSmithTableResult.GUN, GunSmithTableResult.AMMO, GunSmithTableResult.ATTACHMENT -> {
                    RawGunTableResult raw = new RawGunTableResult(typeName, getId(jsonObject), count);
                    if (extraTag != null) {
                        raw.setNbt(extraTag);
                    }
                    if (typeName.equals(GunSmithTableResult.GUN)) {
                        GunResult gunResult = GUN_GSON.fromJson(jsonObject, GunResult.class);
                        if (gunResult != null) {
                            raw.setExtraData(gunResult);
                        }
                    }

                    result = new GunSmithTableResult(raw, tabOverride);
                }
                case GunSmithTableResult.CUSTOM -> {
                    JsonObject resultObject = GsonHelper.getAsJsonObject(jsonObject, "item");
                    ItemStack itemStack = itemDecoder.apply(resultObject);
                    result = new GunSmithTableResult(itemStack, tabOverride);
                }
                default -> {
                    throw new JsonSyntaxException("Unknown gunsmith result type: " + typeName);
                }
            }
            result.setDefinition(jsonObject);
            return result;
        }
        throw new JsonSyntaxException("Gunsmith result must be an object");
    }

    private static Identifier getId(JsonObject jsonObject) {
        return Identifier.parse(GsonHelper.getAsString(jsonObject, "id"));
    }
}
