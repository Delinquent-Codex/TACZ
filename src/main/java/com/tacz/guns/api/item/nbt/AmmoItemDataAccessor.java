package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface AmmoItemDataAccessor extends IAmmo {
    String AMMO_ID_TAG = "AmmoId";

    @Override
    @Nonnull
    default Identifier getAmmoId(ItemStack ammo) {
        CompoundTag nbt = ItemDataAccessor.get(ammo);
        if (ItemDataAccessor.contains(nbt, AMMO_ID_TAG, Tag.TAG_STRING)) {
            Identifier gunId = Identifier.tryParse(nbt.getStringOr(AMMO_ID_TAG, ""));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_AMMO_ID);
        }
        return DefaultAssets.EMPTY_AMMO_ID;
    }

    @Override
    default void setAmmoId(ItemStack ammo, @Nullable Identifier ammoId) {
        CompoundTag nbt = ItemDataAccessor.get(ammo);
        if (ammoId != null) {
            nbt.putString(AMMO_ID_TAG, ammoId.toString());
            ItemDataAccessor.set(ammo, nbt);
            return;
        }
        nbt.putString(AMMO_ID_TAG, DefaultAssets.DEFAULT_AMMO_ID.toString());
        ItemDataAccessor.set(ammo, nbt);
    }

    @Override
    default boolean isAmmoOfGun(ItemStack gun, ItemStack ammo) {
        if (gun.getItem() instanceof IGun iGun && ammo.getItem() instanceof IAmmo iAmmo) {
            Identifier gunId = iGun.getGunId(gun);
            Identifier ammoId = iAmmo.getAmmoId(ammo);
            return TimelessAPI.getCommonGunIndex(gunId).map(gunIndex -> gunIndex.getGunData().getAmmoId().equals(ammoId)).orElse(false);
        }
        return false;
    }
}
