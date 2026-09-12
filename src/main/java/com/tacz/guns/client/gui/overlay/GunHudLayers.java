package com.tacz.guns.client.gui.overlay;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

import java.util.function.BooleanSupplier;

import static net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.*;

public final class GunHudLayers {
    private GunHudLayers() {}

    public static void register(ForgeLayeredDraw root, BooleanSupplier hidden, BooleanSupplier holdingGun,
                                ForgeLayer crosshair, ForgeLayer interact, ForgeLayer gun, ForgeLayer heat, ForgeLayer kills) {
        root.addConditionTo(PRE_SLEEP_STACK, CROSSHAIR, () -> !holdingGun.getAsBoolean());
        root.addAbove(PRE_SLEEP_STACK, id("gun_crosshair"), CROSSHAIR, crosshair);
        root.addAbove(PRE_SLEEP_STACK, id("tac_interact_key_overlay"), id("gun_crosshair"), interact);

        // Source Forge invoked custom overlays even with F1. The target pre-sleep
        // stack is hidden as a unit, so keep those callbacks in a separate fallback.
        ForgeLayer hiddenCrosshair = (graphics, delta) -> {
            if (hidden.getAsBoolean()) {
                crosshair.extract(graphics, delta);
                interact.extract(graphics, delta);
            }
        };
        root.addAbove(id("hidden_crosshair"), PRE_SLEEP_STACK, hiddenCrosshair);
        root.add(id("tac_gun_hud_overlay"), gun);
        root.add(id("tac_heat_bar"), heat);
        root.add(id("tac_kill_amount_overlay"), kills);
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("tacz", path); }
}
