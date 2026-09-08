package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface AmmoBoxItemDataAccessor extends IAmmoBox {
    String AMMO_ID_TAG = "AmmoId";
    String AMMO_COUNT_TAG = "AmmoCount";
    String CREATIVE_TAG = "Creative";
    String ALL_TYPE_CREATIVE_TAG = "AllTypeCreative";
    String LEVEL_TAG = "Level";

    @Override
    default Identifier getAmmoId(ItemStack ammoBox) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        if (ItemDataAccessor.contains(tag, AMMO_ID_TAG, Tag.TAG_STRING)) {
            return Identifier.parse(tag.getStringOr(AMMO_ID_TAG, ""));
        }
        return DefaultAssets.EMPTY_AMMO_ID;
    }

    @Override
    default void setAmmoId(ItemStack ammoBox, Identifier ammoId) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        tag.putString(AMMO_ID_TAG, ammoId.toString());
        ItemDataAccessor.set(ammoBox, tag);
    }

    @Override
    default int getAmmoCount(ItemStack ammoBox) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        if (isAllTypeCreative(ammoBox) || isCreative(ammoBox)) {
            return Integer.MAX_VALUE;
        }
        if (ItemDataAccessor.contains(tag, AMMO_COUNT_TAG, Tag.TAG_INT)) {
            return tag.getIntOr(AMMO_COUNT_TAG, 0);
        }
        return 0;
    }

    @Override
    default void setAmmoCount(ItemStack ammoBox, int count) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        if (isCreative(ammoBox)) {
            tag.putInt(AMMO_COUNT_TAG, Integer.MAX_VALUE);
            ItemDataAccessor.set(ammoBox, tag);
            return;
        }
        tag.putInt(AMMO_COUNT_TAG, count);
        ItemDataAccessor.set(ammoBox, tag);
    }

    @Override
    default boolean isAmmoBoxOfGun(ItemStack gun, ItemStack ammoBox) {
        if (gun.getItem() instanceof IGun iGun && ammoBox.getItem() instanceof IAmmoBox iAmmoBox) {
            if (isAllTypeCreative(ammoBox)) {
                return true;
            }
            Identifier ammoId = iAmmoBox.getAmmoId(ammoBox);
            if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                return false;
            }
            Identifier gunId = iGun.getGunId(gun);
            return TimelessAPI.getCommonGunIndex(gunId).map(gunIndex -> gunIndex.getGunData().getAmmoId().equals(ammoId)).orElse(false);
        }
        return false;
    }

    @Override
    default ItemStack setAmmoLevel(ItemStack ammoBox, int level) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        tag.putInt(LEVEL_TAG, Math.max(level, 0));
        ItemDataAccessor.set(ammoBox, tag);
        return ammoBox;
    }

    @Override
    default int getAmmoLevel(ItemStack ammoBox) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        if (ItemDataAccessor.contains(tag, LEVEL_TAG, Tag.TAG_INT)) {
            return tag.getIntOr(LEVEL_TAG, 0);
        }
        return 0;
    }

    @Override
    default boolean isCreative(ItemStack ammoBox) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        if (tag != null && ItemDataAccessor.contains(tag, CREATIVE_TAG, Tag.TAG_BYTE)) {
            return tag.getBooleanOr(CREATIVE_TAG, false);
        }
        return false;
    }

    @Override
    default boolean isAllTypeCreative(ItemStack ammoBox) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        if (tag != null && ItemDataAccessor.contains(tag, ALL_TYPE_CREATIVE_TAG, Tag.TAG_BYTE)) {
            return tag.getBooleanOr(ALL_TYPE_CREATIVE_TAG, false);
        }
        return false;
    }

    @Override
    default ItemStack setCreative(ItemStack ammoBox, boolean isAllType) {
        CompoundTag tag = ItemDataAccessor.get(ammoBox);
        if (isAllType) {
            // 移除可能存在的创造模式标签
            if (ItemDataAccessor.contains(tag, CREATIVE_TAG, Tag.TAG_BYTE)) {
                tag.remove(CREATIVE_TAG);
                ItemDataAccessor.set(ammoBox, tag);
            }
            tag.putBoolean(ALL_TYPE_CREATIVE_TAG, true);
            ItemDataAccessor.set(ammoBox, tag);
            return ammoBox;
        }
        // 移除可能存在的全类型标签
        if (ItemDataAccessor.contains(tag, ALL_TYPE_CREATIVE_TAG, Tag.TAG_BYTE)) {
            tag.remove(ALL_TYPE_CREATIVE_TAG);
            ItemDataAccessor.set(ammoBox, tag);
        }
        tag.putBoolean(CREATIVE_TAG, true);
        ItemDataAccessor.set(ammoBox, tag);
        return ammoBox;
    }
}
