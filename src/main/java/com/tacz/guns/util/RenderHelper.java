package com.tacz.guns.util;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.optifine.OptifineCompat;
import com.tacz.guns.client.renderer.RenderSubmission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.tacz.guns.client.renderer.TexturedBlit;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@OnlyIn(Dist.CLIENT)
public final class RenderHelper {
    /** Source bridge: replace old RenderSystem.setShaderTexture calls with withBlitTexture. */
    public static void blit(PoseStack poseStack, float x, float y, float uOffset, float vOffset, float width, float height, float textureWidth, float textureHeight) {
        blit(TexturedBlit.currentTexture(), poseStack, x, y, uOffset, vOffset, width, height, textureWidth, textureHeight);
    }

    public static void blit(Identifier texture, PoseStack poseStack, float x, float y, float uOffset, float vOffset, float width, float height, float textureWidth, float textureHeight) {
        TexturedBlit.submit(texture, poseStack, x, y, uOffset, vOffset, width, height, textureWidth, textureHeight);
    }

    public static void withBlitTexture(Identifier texture, Runnable render) {
        TexturedBlit.withTexture(texture, render);
    }

    public static void enableItemEntityStencilTest() {
        RenderSystem.assertOnRenderThread();
        if (OptifineCompat.isOptifineInstalled()) {
            // 以下代码用于应对 使用 optifine 的场景
            int depthTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int stencilTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (depthTextureId != GL30.GL_NONE && stencilTextureId == GL30.GL_NONE) {
                GL30.glBindTexture(GL30.GL_TEXTURE_2D, depthTextureId);
                int dataType = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_DEPTH_TYPE);
                if (dataType == GL30.GL_UNSIGNED_NORMALIZED) {
                    int width = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_WIDTH);
                    int height = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_HEIGHT);
                    GlStateManager._texImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, width, height, 0, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, null);
                    GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, 3553, depthTextureId, 0);
                }
            }
        } else {
            Minecraft.getInstance().gameRenderer.mainRenderTarget().enableStencil();
        }
        GL11.glEnable(GL11.GL_STENCIL_TEST);
    }

    public static void disableItemEntityStencilTest() {
        RenderSystem.assertOnRenderThread();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        AvatarRenderer renderer = (AvatarRenderer) renderManager.getRenderer(player);
        var collector = RenderSubmission.collector();
        // int oldId = RenderSystem.getShaderTexture(0);
        // RenderSystem.setShaderTexture(0, player.getSkinTextureLocation());

		if (ARCompat.shouldAccelerate()) {
			ARCompat.setRenderingLevel();
		}

        try {
            var skin = player.getSkin().body().texturePath();
            if (hand == HumanoidArm.RIGHT) {
                renderer.renderRightHand(matrixStack, collector, combinedLight, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
            } else {
                renderer.renderLeftHand(matrixStack, collector, combinedLight, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
            }
        } finally {
            if (ARCompat.shouldAccelerate()) ARCompat.resetRenderingLevel();
        }

        // RenderSystem.setShaderTexture(0, oldId);
    }
}
