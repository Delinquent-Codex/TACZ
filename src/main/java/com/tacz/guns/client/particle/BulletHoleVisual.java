package com.tacz.guns.client.particle;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts the baseline face-aligned decal into the target's stored quad representation. */
public final class BulletHoleVisual {
    private BulletHoleVisual() {}

    public static float fade(int age, int lifetime, double thresholdFraction) {
        if (lifetime <= 0 || age >= lifetime) return 0;
        // The allowed threshold 1 means no fade until expiry; baseline divided zero by zero.
        if (thresholdFraction >= 1) return 1;
        double threshold = thresholdFraction * lifetime;
        return 1 - (float) (Math.max(age - threshold, 0) / (lifetime - threshold));
    }

    public static void extract(QuadParticleRenderState output, Direction direction, float x, float y, float z,
                               float size, float u0, float u1, float v0, float v1,
                               float red, float green, float blue, float alpha, int age, int lifetime, double threshold) {
        Quaternionf face = direction.getRotation();
        Vector3f offset = new Vector3f(0, 0.01F * size, 0).rotate(face);
        // Target corners (x,y,0) map to source (-x,0,y), preserving winding and UV order.
        Quaternionf rotation = new Quaternionf(face).rotateX((float) (Math.PI / 2)).rotateY((float) Math.PI);
        int light = Math.max(15 - age / 2, 0);
        float brightness = light / 15.0F;
        int color = ARGB.color((int) (alpha * fade(age, lifetime, threshold) * 255),
                (int) (red * brightness * 255), (int) (green * brightness * 255), (int) (blue * brightness * 255));
        output.add(SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN, x + offset.x, y + offset.y, z + offset.z,
                rotation.x, rotation.y, rotation.z, rotation.w, size, u0, u1, v0, v1, color, LightCoordsUtil.pack(light, light));
    }
}
