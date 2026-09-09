package com.tacz.guns.api.util;

import com.tacz.guns.api.item.nbt.ItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Lua access to live custom data. Item writes commit through the component API. */
@SuppressWarnings("unused")
public final class LuaNbtAccessor {
    private final Supplier<CompoundTag> read;
    private final Consumer<Consumer<CompoundTag>> edit;

    public LuaNbtAccessor(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "nbt");
        this.read = () -> nbt;
        this.edit = operation -> operation.accept(nbt);
    }

    private LuaNbtAccessor(Supplier<CompoundTag> read, Consumer<Consumer<CompoundTag>> edit) {
        this.read = read;
        this.edit = edit;
    }

    public static LuaNbtAccessor from(ItemStack stack) {
        return new LuaNbtAccessor(() -> ItemDataAccessor.get(stack), operation -> ItemDataAccessor.update(stack, operation));
    }

    public static LuaNbtAccessor from(CompoundTag nbt) { return new LuaNbtAccessor(nbt); }

    public boolean contains(String key) { return read.get().contains(key); }

    public boolean contains(String key, int type) { return ItemDataAccessor.contains(read.get(), key, type); }

    public LuaNbtAccessor newCompoundTag() { return from(new CompoundTag()); }

    public int getInt(String key) { return read.get().getIntOr(key, 0); }

    public double getDouble(String key) { return read.get().getDoubleOr(key, 0); }

    public float getFloat(String key) { return read.get().getFloatOr(key, 0); }

    public long getLong(String key) { return read.get().getLongOr(key, 0); }

    public String getString(String key) { return read.get().getStringOr(key, ""); }

    public boolean getBoolean(String key) { return read.get().getBooleanOr(key, false); }

    /** Retains the original public overload for scripts which explicitly pass a compound. */
    public boolean getBoolean(CompoundTag nbt, String key) { return nbt.getBooleanOr(key, false); }

    public LuaNbtAccessor getCompound(String key) {
        if (!contains(key, Tag.TAG_COMPOUND)) return null;
        return new LuaNbtAccessor(() -> read.get().getCompoundOrEmpty(key), operation -> edit.accept(parent -> {
            // A removed compound is not recreated through a stale child accessor.
            parent.getCompound(key).ifPresent(operation);
        }));
    }

    public void putInt(String key, int value) { edit.accept(nbt -> nbt.putInt(key, value)); }

    public void putDouble(String key, double value) { edit.accept(nbt -> nbt.putDouble(key, value)); }

    public void putFloat(String key, float value) { edit.accept(nbt -> nbt.putFloat(key, value)); }

    public void putLong(String key, long value) { edit.accept(nbt -> nbt.putLong(key, value)); }

    public void putString(String key, String value) { edit.accept(nbt -> nbt.putString(key, value)); }

    public void putBoolean(String key, boolean value) { edit.accept(nbt -> nbt.putBoolean(key, value)); }

    /** Insert a snapshot; obtain an attached child with getCompound for subsequent edits. */
    public void putCompound(String key, LuaNbtAccessor value) {
        if (value != null) edit.accept(nbt -> nbt.put(key, value.nbt().copy()));
    }

    /** Item-backed access returns a snapshot. Use the put methods to commit item changes. */
    @ApiStatus.Internal
    public CompoundTag nbt() { return read.get(); }
}
