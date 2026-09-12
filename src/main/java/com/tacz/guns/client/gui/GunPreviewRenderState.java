package com.tacz.guns.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public record GunPreviewRenderState(ItemStackRenderState item, int x0, int y0, int x1, int y1, float scale,
                                    float rotation, Matrix3x2fc pose, ScreenRectangle scissorArea, ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    public GunPreviewRenderState(ItemStackRenderState item, int left, int top, float scale, long time,
                                 Matrix3x2fc pose, ScreenRectangle scissor) {
        this(item, left + 3, top + 16, left + 131, top + 115, scale, (time % 8000) * (360F / 8000),
                new Matrix3x2f(pose), scissor, PictureInPictureRenderState.getBounds(left + 3, top + 16, left + 131, top + 115, scissor));
    }

    /** Applied after the native PIP viewport center and scale, matching the old (left+68, top+58) origin. */
    public void applyModelPose(PoseStack poseStack) {
        poseStack.scale(1, -1, -1);
        poseStack.translate(1F / scale, 7.5F / scale, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(15));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
    }
}
