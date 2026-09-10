package com.tacz.guns.api.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

public final class LegacyComponentData {
    private LegacyComponentData() {}

    public static DataResult<Component> decode(String legacyJson, HolderLookup.Provider registries) {
        try {
            var fixed = DataFixers.getDataFixer().update(References.TEXT_COMPONENT,
                    new Dynamic<>(NbtOps.INSTANCE, StringTag.valueOf(legacyJson)), 3465,
                    SharedConstants.getCurrentVersion().dataVersion().version());
            return ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), fixed.getValue());
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Cannot convert legacy text component: " + exception.getMessage());
        }
    }
}
