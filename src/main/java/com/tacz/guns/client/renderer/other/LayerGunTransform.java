package com.tacz.guns.client.renderer.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.util.math.MathUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Independent of reloadable pack vectors and shared animation bones. */
public record LayerGunTransform(float x, float y, float z, float sx, float sy, float sz,
                                float qx, float qy, float qz, float qw) {
    public static LayerGunTransform capture(Vector3f pos, Vector3f rotate, Vector3f scale) {
        Quaternionf rotation = new Quaternionf();
        MathUtil.toQuaternion((float) Math.toRadians(rotate.x), (float) Math.toRadians(rotate.y),
                (float) Math.toRadians(rotate.z), rotation);
        return new LayerGunTransform(-pos.x / 16F, 1.5F - pos.y / 16F, pos.z / 16F,
                -scale.x, -scale.y, scale.z, rotation.x, rotation.y, rotation.z, rotation.w);
    }

    public void apply(PoseStack pose) {
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        pose.mulPose(new Quaternionf(qx, qy, qz, qw));
    }
}
