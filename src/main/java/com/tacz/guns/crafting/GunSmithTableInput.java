package com.tacz.guns.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/** A detached inventory view used to prepare a crafting transaction. */
public record GunSmithTableInput(List<ItemStack> stacks) implements RecipeInput {
    public GunSmithTableInput {
        stacks = stacks.stream().map(ItemStack::copy).toList();
    }

    @Override
    public ItemStack getItem(int slot) { return stacks.get(slot); }

    @Override
    public int size() { return stacks.size(); }
}
