package com.tacz.guns.api.item.nbt;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/** Decodes explicitly versioned legacy nested item data without changing the source. */
public final class StoredItemData {
    public static final int SOURCE_DATA_VERSION = 3465;
    public static final Codec<Tag> RAW_TAG_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue(), tag -> new Dynamic<>(NbtOps.INSTANCE, tag));
    private StoredItemData() {}

    /** A placeable recovery item: vanilla BlockItem restores this component before setPlacedBy. */
    public static ItemStack recoveryBlockItem(ItemStack blockItem, BlockEntityType<?> type, CompoundTag saved) {
        ItemStack result = blockItem.copyWithCount(1);
        result.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(type, saved.copy()));
        return result;
    }

    public static DataResult<ItemStack> decode(CompoundTag saved, HolderLookup.Provider registries) {
        try {
            Tag data = saved.copy();
            if (saved.contains("Count")) {
                if (!ItemDataAccessor.contains(saved, "Count", Tag.TAG_BYTE)) {
                    return DataResult.error(() -> "Legacy item Count must be a byte");
                }
                if (saved.contains("count") || saved.contains("components")) {
                    return DataResult.error(() -> "Mixed legacy and component item formats");
                }
                for (String key : saved.keySet()) {
                    if (!Set.of("id", "Count", "tag").contains(key)) {
                        return DataResult.error(() -> "Unconverted legacy item field: " + key);
                    }
                }
                int count = saved.getByteOr("Count", (byte) 0);
                if (count == 0 && saved.getStringOr("id", "").equals("minecraft:air") && !saved.contains("tag")) {
                    return DataResult.success(ItemStack.EMPTY);
                }
                if (count < 1 || count > 99) return DataResult.error(() -> "Legacy saved item count outside 1..99: " + count);
                if (saved.contains("tag") && !ItemDataAccessor.contains(saved, "tag", Tag.TAG_COMPOUND)) {
                    return DataResult.error(() -> "Legacy item tag must be a compound");
                }
                CompoundTag prepared = saved.copy();
                prepared.getCompound("tag").ifPresent(com.tacz.guns.item.GunTooltipPart::preserveLegacy);
                data = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, prepared),
                        SOURCE_DATA_VERSION, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
            }
            return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), data).flatMap(stack -> {
                try {
                    if (!stack.isEmpty()) GunAttachmentData.migrateLegacy(stack);
                    return DataResult.success(stack);
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> exception.getMessage());
                }
            });
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Could not decode stored item: " + exception.getMessage());
        }
    }
}
