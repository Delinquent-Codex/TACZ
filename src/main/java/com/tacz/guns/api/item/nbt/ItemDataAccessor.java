package com.tacz.guns.api.item.nbt;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * TACZ-owned item fields remain in minecraft:custom_data under their existing
 * names. Reads return detached snapshots. Mutations must be committed with set
 * or update so vanilla's component equality, copy and synchronization see them.
 */
public final class ItemDataAccessor {
    public static final int LEGACY_ANY_NUMERIC = 99;
    private ItemDataAccessor() {
    }

    public static CompoundTag get(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> edit) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, edit);
    }

    /** Retains the legacy typed contains check, including TAG_ANY_NUMERIC. */
    public static boolean contains(CompoundTag tag, String key, int type) {
        Tag value = tag.get(key);
        return value != null && (value.getId() == type || type == LEGACY_ANY_NUMERIC && value instanceof NumericTag);
    }
}
