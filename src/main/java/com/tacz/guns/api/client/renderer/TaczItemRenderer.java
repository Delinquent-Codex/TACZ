package com.tacz.guns.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.RenderSubmission;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Item rendering contract used by TACZ's target item model and first-person adapters. */
public interface TaczItemRenderer {
    void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                      SubmitNodeCollector collector, int light, int overlay);

    default void submitByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                              SubmitNodeCollector collector, int light, int overlay) {
        try (var submission = RenderSubmission.enter(collector)) {
            renderByItem(stack, context, poseStack, collector, light, overlay);
        }
    }
}
