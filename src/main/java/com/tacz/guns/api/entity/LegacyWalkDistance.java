package com.tacz.guns.api.entity;

import net.minecraft.world.phys.Vec3;

/** Restores 1.20.1's cumulative horizontal walkDist, including its float rounding order. */
public final class LegacyWalkDistance {
    private float current;
    private float previous;

    public void beginTick() { previous = current; }

    public void move(Vec3 clippedMovement) {
        current += (float) clippedMovement.horizontalDistance() * 0.6F;
    }

    public float delta() { return current - previous; }

    // TACZ's existing script API extrapolates from current, rather than interpolating from previous.
    public float extrapolate(float partialTicks) { return current + delta() * partialTicks; }
}
