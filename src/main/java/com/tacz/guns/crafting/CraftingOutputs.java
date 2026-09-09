package com.tacz.guns.crafting;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CraftingOutputs {
    private CraftingOutputs() {}

    /** Preserve recipe quantities such as 100 rounds across target-safe physical stacks. */
    public static List<ItemStack> split(ItemStack result) {
        var outputs = new ArrayList<ItemStack>();
        int limit = Math.max(1, Math.min(99, result.getMaxStackSize()));
        for (int remaining = result.getCount(); remaining > 0;) {
            int count = Math.min(limit, remaining);
            outputs.add(result.copyWithCount(count));
            remaining -= count;
        }
        return List.copyOf(outputs);
    }
}
