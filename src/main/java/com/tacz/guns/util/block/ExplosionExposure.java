package com.tacz.guns.util.block;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** TACZ's nearest visible sample calculation, distinct from vanilla's averaged exposure. */
public final class ExplosionExposure {
    private ExplosionExposure() {}

    public static double distanceStrength(double distance, float radius) {
        // Preserve the baseline's double-radius arithmetic for all supported radii.
        return radius > 0 && Float.isFinite(radius) ? distance * 2 / (radius * 2) : Double.POSITIVE_INFINITY;
    }

    public static double livingStrength(Vec3 origin, AABB bounds, float radius, Predicate<Vec3> visible) {
        if (!(radius > 0) || !Float.isFinite(radius)) {
            return Double.POSITIVE_INFINITY;
        }
        double distance = radius * 2;
        for (Vec3 sample : samples(bounds)) {
            if (visible.test(sample)) {
                distance = Math.min(distance, origin.distanceTo(sample));
            }
        }
        return distanceStrength(distance, radius);
    }

    public static Vec3[] samples(AABB b) {
        Vec3 c = b.getCenter();
        return new Vec3[]{
                new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.minX, b.minY, b.maxZ),
                new Vec3(b.minX, b.maxY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ),
                new Vec3(b.minX, b.maxY, b.maxZ), new Vec3(b.maxX, b.minY, b.maxZ),
                new Vec3(b.maxX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.maxZ),
                new Vec3(b.minX, c.y, c.z), new Vec3(b.maxX, c.y, c.z),
                new Vec3(c.x, b.minY, c.z), new Vec3(c.x, b.maxY, c.z),
                new Vec3(c.x, c.y, b.minZ), new Vec3(c.x, c.y, b.maxZ), c
        };
    }
}
