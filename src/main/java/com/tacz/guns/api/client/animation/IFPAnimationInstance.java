/*
 * Derived from SimpleBedrockModel 2.2.2 by the SimpleBedrockModel contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Ported for TACZ Forge 26.2 on 2026-09-09; see META-INF/licenses/SimpleBedrockModel-NOTICE.txt.
 */
package com.tacz.guns.api.client.animation;

import com.maydaymemory.mae.basic.Pose;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Used for centralized management of first-person item rendering-related context.
 */
public interface IFPAnimationInstance {
    ItemStack currentItem();

    /**
     * Get the blended pose from all active animations.
     * @return the current pose
     */
    Pose getPose();

    /**
     * should be called every frame to update the animation state or other logic.
     * @param partialTicks the partial ticks
     */
    void tick(float partialTicks);

    /**
     * Get the camera rotation quaternion for first-person rendering.
     * @return the camera rotation
     */
    @NotNull
    Quaternionf getCameraRotation();

    /**
     * Set the camera rotation quaternion for first-person rendering.<br/>
     * You should call this method at a proper time every frame to update the camera rotation,
     * such as preparing to render the first-person item.
     *
     * @param cameraRotation the camera rotation
     */
    void setCameraRotation(@NotNull Quaternionf cameraRotation);

    /**
     * Get the cached pose for this frame. Should store the result of {@link #getPose()} firstly at the start of each frame.
     * @return the cached pose
     */
    Pose getCachedPose();

    void updateItem(ItemStack stack);

    void triggerDraw();

    void triggerPutAway();

    default boolean shouldRenderHand() {
        return false;
    }
}