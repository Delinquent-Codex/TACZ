package com.tacz.guns.client.resource;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 26.2 does not synchronize the server RecipeManager to clients. */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class ClientRecipeCache {
    private static Map<Identifier, GunSmithTableRecipe> recipes = Map.of();

    private ClientRecipeCache() {}

    public static void replace(Map<Identifier, GunSmithTableRecipe> incoming) {
        recipes = Collections.unmodifiableMap(new LinkedHashMap<>(incoming));
    }

    public static GunSmithTableRecipe get(Identifier id) { return recipes.get(id); }

    public static List<GunSmithTableRecipe> all() { return List.copyOf(recipes.values()); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { recipes = Map.of(); }
}
