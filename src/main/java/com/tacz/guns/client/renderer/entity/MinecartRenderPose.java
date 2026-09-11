package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Native rail/hurt transforms, shared with checks for the target's extracted minecart state. */
public final class MinecartRenderPose {
    private MinecartRenderPose() {}

    public static void apply(MinecartRenderState state, PoseStack pose) {
        long seed = state.offsetSeed;
        pose.translate((((float) (seed >> 16 & 7L) + 0.5F) / 8 - 0.5F) * 0.004F,
                (((float) (seed >> 20 & 7L) + 0.5F) / 8 - 0.5F) * 0.004F,
                (((float) (seed >> 24 & 7L) + 0.5F) / 8 - 0.5F) * 0.004F);
        if (state.isNewRender) {
            pose.mulPose(Axis.YP.rotationDegrees(state.yRot));
            pose.mulPose(Axis.ZP.rotationDegrees(-state.xRot));
            pose.translate(0, 0.375F, 0);
        } else {
            float pitch = state.xRot;
            float yaw = state.yRot;
            if (state.posOnRail != null && state.frontPos != null && state.backPos != null) {
                pose.translate(state.posOnRail.x - state.x, (state.frontPos.y + state.backPos.y) / 2 - state.y,
                        state.posOnRail.z - state.z);
                Vec3 direction = state.backPos.subtract(state.frontPos);
                if (direction.length() != 0) {
                    direction = direction.normalize();
                    yaw = (float) (Math.atan2(direction.z, direction.x) * 180 / Math.PI);
                    pitch = (float) (Math.atan(direction.y) * 73);
                }
            }
            pose.translate(0, 0.375F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(-pitch));
        }
        if (state.hurtTime > 0) {
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10 * state.hurtDir));
        }
    }

    public static void contents(MinecartRenderState state, PoseStack pose) {
        pose.scale(0.75F, 0.75F, 0.75F);
        pose.translate(-0.5F, (state.displayOffset - 8) / 16.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(90));
    }
}
