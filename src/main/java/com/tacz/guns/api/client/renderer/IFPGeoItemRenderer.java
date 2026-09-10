/*
 * Derived from SimpleBedrockModel 2.2.2 by the SimpleBedrockModel contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Ported for TACZ Forge 26.2 on 2026-09-09; see META-INF/licenses/SimpleBedrockModel-NOTICE.txt.
 */
package com.tacz.guns.api.client.renderer;

import com.tacz.guns.api.client.animation.IFPAnimationInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IFPGeoItemRenderer {

    default boolean isSameItem(ItemStack oldStack, ItemStack newStack) {
        return ItemStack.isSameItem(oldStack, newStack);
    }

    @Nullable
    default IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity) {
        return null;
    }

    default long getPutAwayDuration(ItemStack stack) {
        return 0;
    }

    default boolean blockOffhandRender() {
        return false;
    }

    void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, SubmitNodeCollector bufferSource,
                                  int light, float partialTick);
}
