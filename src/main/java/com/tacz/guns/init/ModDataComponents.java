package com.tacz.guns.init;

import com.mojang.serialization.DataResult;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Attachment stacks use vanilla's immutable, registry-aware item codec. */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "tacz");
    public static final DataComponentType<ItemContainerContents> ATTACHMENT_DATA = DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC.validate(contents -> validSize(contents)
                            ? DataResult.success(contents) : DataResult.error(() -> "TACZ attachments have at most six slots")))
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC.map(ModDataComponents::validateSize, ModDataComponents::validateSize))
                    .build();
    public static final RegistryObject<DataComponentType<ItemContainerContents>> ATTACHMENTS = COMPONENTS.register("attachments", () -> ATTACHMENT_DATA);

    private ModDataComponents() {
    }

    private static boolean validSize(ItemContainerContents contents) {
        return contents.allItemsCopyStream().limit(7).count() <= 6;
    }

    private static ItemContainerContents validateSize(ItemContainerContents contents) {
        if (!validSize(contents)) throw new IllegalArgumentException("TACZ attachments have at most six slots");
        return contents;
    }
}
