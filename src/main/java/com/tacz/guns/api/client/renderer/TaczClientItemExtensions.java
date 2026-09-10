package com.tacz.guns.api.client.renderer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

/** Forge's client item extension remains the per-item owner of the renderer instance. */
public interface TaczClientItemExtensions extends IClientItemExtensions {
    TaczItemRenderer getCustomRenderer();

    static @Nullable TaczItemRenderer getRenderer(ItemStack stack) {
        return getRenderer(stack.getItem());
    }

    static @Nullable TaczItemRenderer getRenderer(Item item) {
        return IClientItemExtensions.of(item) instanceof TaczClientItemExtensions extension
                ? extension.getCustomRenderer() : null;
    }
}
