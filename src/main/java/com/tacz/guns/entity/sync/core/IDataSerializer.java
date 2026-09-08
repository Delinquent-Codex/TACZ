package com.tacz.guns.entity.sync.core;

import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.HolderLookup;

/**
 * Author: MrCrayfish.
 * Open source at <a href="https://github.com/MrCrayfish/Framework">Github</a> under LGPL License.
 */
public interface IDataSerializer<T> {
    void write(RegistryFriendlyByteBuf buf, T value);

    T read(RegistryFriendlyByteBuf buf);

    Tag write(HolderLookup.Provider registries, T value);

    T read(HolderLookup.Provider registries, Tag nbt);
}
