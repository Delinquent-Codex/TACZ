package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface GunItemDataAccessor extends IGun {
    String GUN_ID_TAG = "GunId";
    String GUN_FIRE_MODE_TAG = "GunFireMode";
    String GUN_HAS_BULLET_IN_BARREL = "HasBulletInBarrel";
    String GUN_CURRENT_AMMO_COUNT_TAG = "GunCurrentAmmoCount";
    String GUN_ATTACHMENT_BASE = "Attachment";
    String GUN_EXP_TAG = "GunLevelExp";
    String GUN_DUMMY_AMMO = "DummyAmmo";
    String GUN_MAX_DUMMY_AMMO = "MaxDummyAmmo";
    String GUN_ATTACHMENT_LOCK = "AttachmentLock";
    String GUN_DISPLAY_ID_TAG = "GunDisplayId";
    String LASER_COLOR_TAG = "LaserColor";
    String GUN_OVERHEAT_TAG = "HeatAmount";
    String GUN_OVERHEAT_LOCK_TAG = "OverHeated";

    @Override
    default boolean useDummyAmmo(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        return ItemDataAccessor.contains(nbt, GUN_DUMMY_AMMO, Tag.TAG_INT);
    }

    @Override
    default int getDummyAmmoAmount(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        return Math.max(0, nbt.getIntOr(GUN_DUMMY_AMMO, 0));
    }

    @Override
    default void setDummyAmmoAmount(ItemStack gun, int amount) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        nbt.putInt(GUN_DUMMY_AMMO, Math.max(amount, 0));
        ItemDataAccessor.set(gun, nbt);
    }

    @Override
    default void addDummyAmmoAmount(ItemStack gun, int amount) {
        if (!useDummyAmmo(gun)) {
            return;
        }
        int maxDummyAmmo = Integer.MAX_VALUE;
        if (hasMaxDummyAmmo(gun)) {
            maxDummyAmmo = getMaxDummyAmmoAmount(gun);
        }
        CompoundTag nbt = ItemDataAccessor.get(gun);
        amount = Math.min(getDummyAmmoAmount(gun) + amount, maxDummyAmmo);
        nbt.putInt(GUN_DUMMY_AMMO, Math.max(amount, 0));
        ItemDataAccessor.set(gun, nbt);
    }

    @Override
    default boolean hasMaxDummyAmmo(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        return ItemDataAccessor.contains(nbt, GUN_MAX_DUMMY_AMMO, Tag.TAG_INT);
    }

    @Override
    default int getMaxDummyAmmoAmount(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        return Math.max(0, nbt.getIntOr(GUN_MAX_DUMMY_AMMO, 0));
    }

    @Override
    default void setMaxDummyAmmoAmount(ItemStack gun, int amount) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        nbt.putInt(GUN_MAX_DUMMY_AMMO, Math.max(amount, 0));
        ItemDataAccessor.set(gun, nbt);
    }

    @Override
    default boolean hasAttachmentLock(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_ATTACHMENT_LOCK, Tag.TAG_BYTE)) {
            return nbt.getBooleanOr(GUN_ATTACHMENT_LOCK, false);
        }
        return false;
    }

    @Override
    default void setAttachmentLock(ItemStack gun, boolean lock) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        nbt.putBoolean(GUN_ATTACHMENT_LOCK, lock);
        ItemDataAccessor.set(gun, nbt);
    }

    @Override
    @Nonnull
    default Identifier getGunId(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_ID_TAG, Tag.TAG_STRING)) {
            Identifier gunId = Identifier.tryParse(nbt.getStringOr(GUN_ID_TAG, ""));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_GUN_ID);
        }
        return DefaultAssets.EMPTY_GUN_ID;
    }

    @Override
    default void setGunId(ItemStack gun, @Nullable Identifier gunId) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (gunId != null) {
            nbt.putString(GUN_ID_TAG, gunId.toString());
            ItemDataAccessor.set(gun, nbt);
        }
    }

    @Override
    @NotNull
    default Identifier getGunDisplayId(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_DISPLAY_ID_TAG, Tag.TAG_STRING)) {
            Identifier gunDisplayId = Identifier.tryParse(nbt.getStringOr(GUN_DISPLAY_ID_TAG, ""));
            return Objects.requireNonNullElse(gunDisplayId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID);
        }
        return DefaultAssets.DEFAULT_GUN_DISPLAY_ID;
    }

    @Override
    default void setGunDisplayId(ItemStack gun, Identifier displayId) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (displayId != null) {
            nbt.putString(GUN_DISPLAY_ID_TAG, displayId.toString());
            ItemDataAccessor.set(gun, nbt);
        }
    }

    @Override
    default int getLevel(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_EXP_TAG, Tag.TAG_INT)) {
            return getLevel(nbt.getIntOr(GUN_EXP_TAG, 0));
        }
        return 0;
    }

    @Override
    default int getExp(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_EXP_TAG, Tag.TAG_INT)) {
            return nbt.getIntOr(GUN_EXP_TAG, 0);
        }
        return 0;
    }

    @Override
    default int getExpToNextLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        if (level >= getMaxLevel()) {
            return 0;
        }
        int nextLevelExp = getExp(level + 1);
        return nextLevelExp - exp;
    }

    @Override
    default int getExpCurrentLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        if (level <= 0) {
            return exp;
        } else {
            return exp - getExp(level - 1);
        }
    }

    @Override
    default FireMode getFireMode(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_FIRE_MODE_TAG, Tag.TAG_STRING)) {
            return FireMode.valueOf(nbt.getStringOr(GUN_FIRE_MODE_TAG, ""));
        }
        return FireMode.UNKNOWN;
    }

    @Override
    default void setFireMode(ItemStack gun, @Nullable FireMode fireMode) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (fireMode != null) {
            nbt.putString(GUN_FIRE_MODE_TAG, fireMode.name());
            ItemDataAccessor.set(gun, nbt);
            return;
        }
        nbt.putString(GUN_FIRE_MODE_TAG, FireMode.UNKNOWN.name());
        ItemDataAccessor.set(gun, nbt);
    }

    @Override
    default int getCurrentAmmoCount(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_CURRENT_AMMO_COUNT_TAG, Tag.TAG_INT)) {
            return nbt.getIntOr(GUN_CURRENT_AMMO_COUNT_TAG, 0);
        }
        return 0;
    }

    @Override
    default void setCurrentAmmoCount(ItemStack gun, int ammoCount) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        nbt.putInt(GUN_CURRENT_AMMO_COUNT_TAG, Math.max(ammoCount, 0));
        ItemDataAccessor.set(gun, nbt);
    }

    @Override
    default void reduceCurrentAmmoCount(ItemStack gun) {
        // 只在不使用背包直读的情况下减少 AmmoCount
        if (!useInventoryAmmo(gun)) {
            setCurrentAmmoCount(gun, getCurrentAmmoCount(gun) - 1);
        }
    }

    @Override
    @Nullable
    default CompoundTag getAttachmentTag(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return null;
        }
        ItemStack attachment = GunAttachmentData.get(gun, type);
        return attachment.isEmpty() ? null : ItemDataAccessor.get(attachment);
    }

    @Override
    @NotNull
    default ItemStack getBuiltinAttachment(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return ItemStack.EMPTY;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index != null){
            var builtin = index.getGunData().getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                return AttachmentItemBuilder.create().setId(builtin.get(type)).build();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    default ItemStack getAttachment(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return ItemStack.EMPTY;
        }
        return GunAttachmentData.get(gun, type);
    }

    @Override
    @NotNull
    default Identifier getBuiltInAttachmentId(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index != null){
            var builtin = index.getGunData().getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                return builtin.get(type);
            }
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    @Nonnull
    default Identifier getAttachmentId(ItemStack gun, AttachmentType type) {
        CompoundTag attachmentTag = this.getAttachmentTag(gun, type);
        if (attachmentTag != null) {
            return AttachmentItemDataAccessor.getAttachmentIdFromTag(attachmentTag);
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    default void installAttachment(@Nonnull ItemStack gun, @Nonnull ItemStack attachment) {
        if (!allowAttachment(gun, attachment)) {
            return;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        if (iAttachment == null) {
            return;
        }
        GunAttachmentData.set(gun, iAttachment.getType(attachment), attachment);
    }

    @Override
    default void unloadAttachment(@Nonnull ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return;
        }
        GunAttachmentData.set(gun, type, ItemStack.EMPTY);
    }

    @Override
    default void updateAttachmentTag(ItemStack gun, AttachmentType type, java.util.function.Consumer<CompoundTag> edit) {
        if (allowAttachmentType(gun, type)) GunAttachmentData.updateTag(gun, type, edit);
    }

    @Override
    default float getAimingZoom(ItemStack gunItem) {
        float zoom = 1;
        Identifier scopeId = this.getAttachmentId(gunItem, AttachmentType.SCOPE);
        boolean builtin = false;
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = getBuiltInAttachmentId(gunItem, AttachmentType.SCOPE);
            builtin = true;
        }
        if (!DefaultAssets.isEmptyAttachmentId(scopeId)) {
            CompoundTag attachmentTag = this.getAttachmentTag(gunItem, AttachmentType.SCOPE);
            int zoomNumber = builtin ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentTag);
            float[] zooms = TimelessAPI.getClientAttachmentIndex(scopeId).map(ClientAttachmentIndex::getZoom).orElse(null);
            if (zooms != null) {
                zoom = zooms[zoomNumber % zooms.length];
            }
        } else {
            zoom = TimelessAPI.getGunDisplay(gunItem).map(GunDisplayInstance::getIronZoom).orElse(1f);
        }
        return zoom;
    }

    @Override
    default boolean hasBulletInBarrel(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (ItemDataAccessor.contains(nbt, GUN_HAS_BULLET_IN_BARREL, Tag.TAG_BYTE)) {
            return nbt.getBooleanOr(GUN_HAS_BULLET_IN_BARREL, false);
        }
        return false;
    }

    @Override
    default void setBulletInBarrel(ItemStack gun, boolean bulletInBarrel) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        nbt.putBoolean(GUN_HAS_BULLET_IN_BARREL, bulletInBarrel);
        ItemDataAccessor.set(gun, nbt);
    }

    @Override
    default boolean hasCustomLaserColor(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        return ItemDataAccessor.contains(nbt, LASER_COLOR_TAG, Tag.TAG_INT);
    }

    @Override
    default int getLaserColor(ItemStack gun) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        if (!hasCustomLaserColor(gun)) {
            return 0xFF0000;
        }
        return nbt.getIntOr(LASER_COLOR_TAG, 0);
    }

    @Override
    default void setLaserColor(ItemStack gun, int color) {
        CompoundTag nbt = ItemDataAccessor.get(gun);
        nbt.putInt(LASER_COLOR_TAG, color);
        ItemDataAccessor.set(gun, nbt);
    }

    /**
     * Heat Data
     */
    @Override
    default boolean hasHeatData(ItemStack gun) {
        return ItemDataAccessor.contains(ItemDataAccessor.get(gun), GUN_OVERHEAT_TAG, Tag.TAG_FLOAT);
    }

    @Override
    default boolean isOverheatLocked(ItemStack gun) {
        return ItemDataAccessor.get(gun).getBooleanOr(GUN_OVERHEAT_LOCK_TAG, false);
    }

    @Override
    default void setOverheatLocked(ItemStack gun, boolean locked) {
        ItemDataAccessor.update(gun, data -> data.putBoolean(GUN_OVERHEAT_LOCK_TAG, locked));
    }

    @Override
    default float getHeatAmount(ItemStack gun) {
        if(hasHeatData(gun)) return ItemDataAccessor.get(gun).getFloatOr(GUN_OVERHEAT_TAG, 0f);
        return 0f;
    }

    @Override
    default void setHeatAmount(ItemStack gun, float amount) {
        ItemDataAccessor.update(gun, data -> data.putFloat(GUN_OVERHEAT_TAG, amount >= 0 ? amount : 0f));
    }

    @Override
    default float lerpRPM(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
                .map(index -> index.getGunData().getHeatData())
                .map(heatData -> {
                    float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                    return Mth.lerp(heatPercentage, heatData.getMinRpmMod(), heatData.getMaxRpmMod());
                }).orElse(1f);
    }

    @Override
    default float lerpInaccuracy(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
                .map(index -> index.getGunData().getHeatData())
                .map(heatData -> {
                    float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                    return Mth.lerp(heatPercentage, heatData.getMinInaccuracy(), heatData.getMaxInaccuracy());
                }).orElse(1f);
    }
}
