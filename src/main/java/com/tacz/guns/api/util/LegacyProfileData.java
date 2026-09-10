package com.tacz.guns.api.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.component.ResolvableProfile;

/** Converts the original target-block Owner compound through vanilla skull-owner fixes. */
public final class LegacyProfileData {
    private LegacyProfileData() {}

    public static ResolvableProfile withResolvedProfile(ResolvableProfile original, com.mojang.authlib.GameProfile resolved) {
        var profile = (CompoundTag) ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE,
                ResolvableProfile.createResolved(resolved)).getOrThrow();
        var patch = (CompoundTag) net.minecraft.world.entity.player.PlayerSkin.Patch.MAP_CODEC.codec()
                .encodeStart(NbtOps.INSTANCE, original.skinPatch()).getOrThrow();
        profile.merge(patch);
        return ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, profile).getOrThrow();
    }

    public static DataResult<ResolvableProfile> decode(Tag saved) {
        if (!(saved instanceof CompoundTag compound)
                || !compound.contains("Name") && !compound.contains("Id") && !compound.contains("Properties")) {
            return ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, saved);
        }
        try {
            var item = new CompoundTag();
            item.putString("id", "minecraft:player_head");
            item.putByte("Count", (byte) 1);
            var tag = new CompoundTag();
            tag.put("SkullOwner", saved.copy());
            item.put("tag", tag);
            var updated = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item),
                    3465, SharedConstants.getCurrentVersion().dataVersion().version());
            return updated.get("components").get("minecraft:profile").result()
                    .map(profile -> ResolvableProfile.CODEC.parse(profile))
                    .orElseGet(() -> DataResult.error(() -> "Legacy profile did not produce a profile component"));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Could not convert target owner: " + exception.getMessage());
        }
    }
}
