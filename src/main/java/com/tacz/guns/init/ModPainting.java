package com.tacz.guns.init;

import com.tacz.guns.api.TaczConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

public class ModPainting {
    // 26.2 loads painting variants from data/<namespace>/painting_variant/*.json.
    public static final ResourceKey<PaintingVariant> BLOOD_STRIKE_1 = ResourceKey.create(
            Registries.PAINTING_VARIANT, Identifier.fromNamespaceAndPath(TaczConstants.MOD_ID, "blood_strike_1"));
}
