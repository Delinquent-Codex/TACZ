package com.tacz.guns.mixin.common;

import com.tacz.guns.item.AmmoItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Restores the old Forge stack-size hook for TACZ ammo, including stacks loaded before pack reload. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    // ItemStack inherits this default from ItemInstance in 26.2. Adding an override avoids
    // changing the saved component on every lookup and keeps container/inventory limits live.
    public int getMaxStackSize() {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof AmmoItem ammo) return ammo.getMaxStackSize(stack);
        return stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }
}
