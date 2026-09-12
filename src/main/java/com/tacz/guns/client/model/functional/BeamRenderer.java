package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.BeamGeometry;
import com.tacz.guns.client.renderer.RenderSubmission;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.LaserColorUtil;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

public class BeamRenderer {
    public static final Identifier LASER_BEAM_TEXTURE = TaczRenderTypes.LASER_BEAM_TEXTURE;
    private static final LaserConfig DEFAULT_LASER_CONFIG = new LaserConfig();

    public static void renderLaserBeam(ItemStack stack, PoseStack poseStack, ItemDisplayContext context, @Nonnull List<BedrockPart> path) {
        if (stack == null || !context.firstPerson() && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
        if (ARCompat.shouldAccelerate() && renderLaserBeamAccelerated(stack, poseStack, context, path)) return;
        submit(stack, poseStack, context, path, false);
    }

    public static boolean renderLaserBeamAccelerated(ItemStack stack, PoseStack poseStack, ItemDisplayContext context, @Nonnull List<BedrockPart> path) {
        if (!ARCompat.shouldAccelerate()) return false;
        submit(stack, poseStack, context, path, true);
        return true;
    }

    private static void submit(ItemStack stack, PoseStack poseStack, ItemDisplayContext context, List<BedrockPart> path, boolean accelerated) {
        LaserConfig config = getLaserConfig(stack);
        float z = context.firstPerson() ? -config.getLength() : -config.getLengthThird();
        float width = context.firstPerson() ? config.getWidth() : config.getWidthThird();
        int color = LaserColorUtil.getLaserColor(stack, config) | 0xff000000;
        boolean fade = RenderConfig.ENABLE_LASER_FADE_OUT.get();
        poseStack.pushPose();
        try {
            for (BedrockPart part : path) part.translateAndRotateAndScale(poseStack);
            if (accelerated) {
                // The real consumer exists during geometry preparation. The collector copies this pose.
                RenderSubmission.collector().submitCustomGeometry(poseStack, LaserBeamRenderState.getLaserBeamEntity(), (pose, output) -> {
                    if (ARCompat.isAccelerated(output)) {
                        PoseStack frozen = new PoseStack();
                        frozen.last().pose().set(pose.pose());
                        frozen.last().normal().set(pose.normal());
                        ARCompat.renderLaser(output, z, width, fade, frozen, color);
                    } else {
                        BeamGeometry.emit(z, width, output, pose, color >> 16 & 255, color >> 8 & 255, color & 255, fade);
                    }
                });
            } else {
                VertexCapture capture = new VertexCapture();
                BeamGeometry.emit(z, width, capture, poseStack.last(), color >> 16 & 255, color >> 8 & 255, color & 255, fade);
                RenderSubmission.submit(LaserBeamRenderState.getLaserBeam(), capture.drain());
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static LaserConfig getLaserConfig(ItemStack stack) {
        if (stack == null) {
            return DEFAULT_LASER_CONFIG;
        }

        if (stack.getItem() instanceof IAttachment iAttachment) {
            return TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(stack))
                    .map(ClientAttachmentIndex::getLaserConfig)
                    .orElse(DEFAULT_LASER_CONFIG);
        }

        if (stack.getItem() instanceof IGun) {
            return TimelessAPI.getGunDisplay(stack)
                    .map(GunDisplayInstance::getLaserConfig)
                    .orElse(DEFAULT_LASER_CONFIG);
        }

        return DEFAULT_LASER_CONFIG;
    }

    public static final class LaserBeamRenderState {
        private LaserBeamRenderState() {}
        public static RenderType getLaserBeam() { return TaczRenderTypes.laserBeam(); }
        public static RenderType getLaserBeamEntity() { return TaczRenderTypes.laserBeamEntity(); }
    }
}
