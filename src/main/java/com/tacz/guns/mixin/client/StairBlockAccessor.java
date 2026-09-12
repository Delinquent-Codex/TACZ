package com.tacz.guns.mixin.client;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StairBlock.class, remap = false)
public interface StairBlockAccessor {
    /** Source API retained after Forge's old getModelBlock helper was removed. */
    @Accessor("base")
    Block invokeGetModelBlock();
}
