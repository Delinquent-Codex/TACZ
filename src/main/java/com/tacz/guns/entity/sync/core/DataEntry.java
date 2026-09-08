package com.tacz.guns.entity.sync.core;

import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.Validate;

public class DataEntry<E extends Entity, T> {
    private final SyncedDataKey<E, T> key;
    private T value;
    private boolean dirty;

    public DataEntry(SyncedDataKey<E, T> key) {
        this.key = key;
        this.value = key.defaultValueSupplier().get();
    }

    public static DataEntry<?, ?> read(RegistryFriendlyByteBuf buffer) {
        SyncedDataKey<?, ?> key = SyncedEntityData.instance().getKey(buffer.readVarInt());
        Validate.notNull(key, "Synced key does not exist for id");
        DataEntry<?, ?> entry = new DataEntry<>(key);
        entry.readValue(buffer);
        return entry;
    }

    public SyncedDataKey<E, T> getKey() {
        return this.key;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value, boolean dirty) {
        this.value = value;
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void clean() {
        this.dirty = false;
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        int id = SyncedEntityData.instance().getInternalId(this.key);
        buffer.writeVarInt(id);
        this.key.serializer().write(buffer, this.value);
    }

    public void readValue(RegistryFriendlyByteBuf buffer) {
        this.value = this.getKey().serializer().read(buffer);
    }

    public Tag writeValue(HolderLookup.Provider registries) {
        return this.key.serializer().write(registries, this.value);
    }

    public void readValue(HolderLookup.Provider registries, Tag nbt) {
        this.value = this.key.serializer().read(registries, nbt);
    }
}
