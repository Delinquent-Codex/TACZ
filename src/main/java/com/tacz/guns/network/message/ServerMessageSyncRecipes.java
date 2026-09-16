package com.tacz.guns.network.message;

import com.tacz.guns.client.resource.ClientRecipeCache;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.GunSmithTableSerializer;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sends resolved outputs after pack loading; the server retains crafting authority. */
public record ServerMessageSyncRecipes(Map<Identifier, GunSmithTableRecipe> recipes) {
    public static final int MAX_RECIPES = 16384;

    public ServerMessageSyncRecipes {
        if (recipes.size() > MAX_RECIPES) throw new IllegalArgumentException("Too many gunsmith recipes");
        recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
    }

    public static void encode(ServerMessageSyncRecipes message, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(message.recipes.size());
        message.recipes.forEach((id, recipe) -> {
            buffer.writeIdentifier(id);
            GunSmithTableSerializer.STREAM_CODEC.encode(buffer, recipe);
        });
    }

    public static ServerMessageSyncRecipes decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_RECIPES) throw new DecoderException("Invalid recipe count: " + count);
        var recipes = new LinkedHashMap<Identifier, GunSmithTableRecipe>();
        for (int index = 0; index < count; index++) {
            Identifier id = buffer.readIdentifier();
            var recipe = GunSmithTableSerializer.STREAM_CODEC.decode(buffer);
            recipe.bindId(id);
            if (recipes.putIfAbsent(id, recipe) != null) throw new DecoderException("Duplicate recipe ID: " + id);
        }
        return new ServerMessageSyncRecipes(recipes);
    }

    public static void handle(ServerMessageSyncRecipes message, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) context.enqueueWork(() -> ClientHandler.install(message));
        context.setPacketHandled(true);
    }

    // Loading codecs on the server must not verify client-only callback bytecode.
    private static final class ClientHandler {
        private static void install(ServerMessageSyncRecipes message) { ClientRecipeCache.replace(message.recipes); }
    }
}
