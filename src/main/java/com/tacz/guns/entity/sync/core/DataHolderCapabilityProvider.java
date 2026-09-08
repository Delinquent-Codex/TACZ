package com.tacz.guns.entity.sync.core;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class DataHolderCapabilityProvider implements ICapabilitySerializable<ListTag> {
    public static final Capability<DataHolder> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });
    private final DataHolder holder = new DataHolder();
    private final LazyOptional<DataHolder> optional = LazyOptional.of(() -> this.holder);
    private ListTag unresolvedEntries = new ListTag();

    public void invalidate() {
        this.optional.invalidate();
    }

    @Override
    public ListTag serializeNBT(HolderLookup.Provider registries) {
        ListTag list = unresolvedEntries.copy();
        this.holder.dataMap.forEach((key, entry) -> {
            if (key.save()) {
                CompoundTag keyTag = new CompoundTag();
                keyTag.putString("ClassKey", key.classKey().id().toString());
                keyTag.putString("DataKey", key.id().toString());
                keyTag.put("Value", entry.writeValue(registries));
                list.add(keyTag);
            }
        });
        return list;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, ListTag listTag) {
        Map<SyncedDataKey<?, ?>, DataEntry<?, ?>> prepared = new HashMap<>();
        ListTag unresolved = new ListTag();
        listTag.forEach(entryTag -> {
            if (!(entryTag instanceof CompoundTag keyTag)) {
                throw new IllegalArgumentException("TACZ entity-data entry must be a compound");
            }
            Identifier classKey = Identifier.tryParse(keyTag.getStringOr("ClassKey", ""));
            Identifier dataKey = Identifier.tryParse(keyTag.getStringOr("DataKey", ""));
            Tag value = keyTag.get("Value");
            if (classKey == null || dataKey == null || value == null) {
                throw new IllegalArgumentException("Malformed TACZ entity-data entry: " + keyTag);
            }
            SyncedClassKey<?> syncedClassKey = SyncedEntityData.instance().getClassKey(classKey);
            if (syncedClassKey == null) {
                unresolved.add(keyTag.copy());
                return;
            }
            SyncedDataKey<?, ?> syncedDataKey = SyncedEntityData.instance().getKey(syncedClassKey, dataKey);
            if (syncedDataKey == null || !syncedDataKey.save()) {
                unresolved.add(keyTag.copy());
                return;
            }
            DataEntry<?, ?> entry = new DataEntry<>(syncedDataKey);
            entry.readValue(registries, value);
            if (prepared.putIfAbsent(syncedDataKey, entry) != null) {
                throw new IllegalArgumentException("Duplicate TACZ entity-data entry: " + dataKey);
            }
        });
        this.holder.dataMap.clear();
        this.holder.dataMap.putAll(prepared);
        this.unresolvedEntries = unresolved;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return CAPABILITY.orEmpty(cap, this.optional);
    }
}
