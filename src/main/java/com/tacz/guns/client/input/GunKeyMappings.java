package com.tacz.guns.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.settings.KeyModifier;

public final class GunKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tacz", "guns"));

    private GunKeyMappings() {}

    /** Preserve Forge 1.20.1's Shift, Control, Alt precedence for the configuration key. */
    public static KeyModifier activeModifier() {
        return KeyModifier.getValues(false).stream().filter(modifier -> modifier.isActive(null)).findFirst().orElse(KeyModifier.NONE);
    }
}
