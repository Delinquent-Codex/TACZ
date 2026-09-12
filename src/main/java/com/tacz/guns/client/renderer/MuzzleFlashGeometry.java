package com.tacz.guns.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.SlotModel;

/** Captures both flash layers before the shared animation model can be changed. */
public final class MuzzleFlashGeometry {
    public static final long TIME_RANGE = 50;
    private static final SlotModel MODEL = new SlotModel(true);
    private MuzzleFlashGeometry() {}

    public record Frame(VertexCapture.Snapshot background, VertexCapture.Snapshot glow) {}

    public static Frame capture(PoseStack.Pose anchor, float configuredScale, long time, float rotation, int light, int overlay) {
        float scale = .5F * configuredScale;
        float scaleTime = TIME_RANGE / 2.0F;
        scale = time < scaleTime ? scale * (time / scaleTime) : scale;
        return new Frame(layer(anchor, scale, rotation, -1, light, overlay),
                layer(anchor, scale / 2, rotation, -.9, light, overlay));
    }

    private static VertexCapture.Snapshot layer(PoseStack.Pose anchor, float scale, float rotation, double y, int light, int overlay) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(anchor.pose());
        pose.last().normal().set(anchor.normal());
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.ZP.rotationDegrees(rotation));
        pose.translate(0, y, 0);
        VertexCapture capture = new VertexCapture();
        MODEL.renderToBuffer(pose, capture, light, overlay, 1, 1, 1, 1);
        return capture.drain();
    }
}
