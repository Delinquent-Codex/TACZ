package com.tacz.guns.client.renderer.item;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Retains all nine source ammo-box model states under the target range-dispatch codec. */
public record AmmoBoxModelProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<AmmoBoxModelProperty> CODEC = MapCodec.unit(new AmmoBoxModelProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        return AmmoBoxItem.getStatue(stack, level, null, seed);
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() { return CODEC; }
}
