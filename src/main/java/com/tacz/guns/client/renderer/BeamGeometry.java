package com.tacz.guns.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/** The baseline four beam faces, including its fourth-face UV order. */
public final class BeamGeometry {
    private BeamGeometry() {}

    public static void emit(float z, float width, VertexConsumer pConsumer, PoseStack.Pose pPose, int r, int g, int b, boolean fadeOut) {
        float halfWidth = width / 2;
        int endAlpha = fadeOut ? 0 : 255;
        int light = LightCoordsUtil.pack(15, 15);
    	pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 0).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, -1, 0, 0);
        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, -1, 0, 0);
        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, -1, 0, 0);
        pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, -1, 0, 0);

        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 0).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, 1, 0);
        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, 1, 0);
        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, 1, 0);
        pConsumer.addVertex(pPose.pose(), -halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, 1, 0);

        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, 0).setColor(r, g, b, 255).setUv(0, 0).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 1, 0, 0);
        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 1, 0, 0);
        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 1, 0, 0);
        pConsumer.addVertex(pPose.pose(), halfWidth, halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 1, 0, 0);

        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, -1, 0);
        pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, 0).setColor(r, g, b, 255).setUv(0, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, -1, 0);
        pConsumer.addVertex(pPose.pose(), -halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 1).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, -1, 0);
        pConsumer.addVertex(pPose.pose(), halfWidth, -halfWidth, z).setColor(r, g, b, endAlpha).setUv(1, 0).setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pPose, 0, -1, 0);
    }

}
