package com.tacz.guns.client.renderer.entity;

/** Baseline tracer dimensions and visibility, evaluated while the entity is available. */
public record TracerGeometry(float width, double length, double offsetScale, boolean visible) {
    public static TracerGeometry calculate(double speed, double interpolatedEyeDistance,
                                           double eyeDistance, int age, float size) {
        double length = Math.min(0.85 * speed, interpolatedEyeDistance * 0.8);
        float width = 0.005F * size;
        width *= (float) Math.max(1, interpolatedEyeDistance / 3.5);
        return new TracerGeometry(width, length, Math.max(0, 50 - interpolatedEyeDistance) / 50,
                age >= 5 || eyeDistance > 2);
    }
}
