package com.tacz.guns.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.item.nbt.ItemDataAccessor;

public enum GunTooltipPart {
    DESCRIPTION,
    AMMO_INFO,
    BASE_INFO,
    EXTRA_DAMAGE_INFO,
    UPGRADES_TIP,
    PACK_INFO;

    public static final String DATA_KEY = "tacz:tooltip_hide_flags";
    private final int mask = 1 << this.ordinal();

    public int getMask() {
        return this.mask;
    }

    public static int getHideFlags(ItemStack stack) {
        CompoundTag tag = ItemDataAccessor.get(stack);
        return tag.getIntOr(DATA_KEY, tag.getIntOr("HideFlags", stack.getItem().getDefaultTooltipHideFlags(stack)));
    }

    public static void setHideFlags(ItemStack stack, int mask) {
        ItemDataAccessor.update(stack, tag -> tag.putInt(DATA_KEY, mask));
    }

    /** Retain TACZ's use of HideFlags before vanilla converts that shared legacy field. */
    public static void preserveLegacy(CompoundTag tag) {
        if (tag.contains("GunId") && tag.contains("HideFlags") && !tag.contains(DATA_KEY)) {
            tag.put(DATA_KEY, tag.get("HideFlags").copy());
        }
    }
}
