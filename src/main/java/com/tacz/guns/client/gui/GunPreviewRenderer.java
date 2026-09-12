package com.tacz.guns.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.util.RenderDistance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class GunPreviewRenderer extends PictureInPictureRenderer<GunPreviewRenderState> {
    @Override
    public Class<GunPreviewRenderState> getRenderStateClass() { return GunPreviewRenderState.class; }

    @Override
    protected void renderToTexture(GunPreviewRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        RenderDistance.markGuiRenderTimestamp();
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_FLAT);
        poseStack.pushPose();
        try {
            state.applyModelPose(poseStack);
            state.item().submit(poseStack, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    protected float getTranslateY(int height, int guiScale) { return height / 2F; }

    @Override
    protected String getTextureLabel() { return "tacz_gunsmith_preview"; }
}
