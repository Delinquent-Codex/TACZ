package com.tacz.guns.client.renderer.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public class GunHurtBobTweak {
    private static long hurtByGunTimeStamp = -1L;
    private static float lastTweakMultiplier = 0.05f;

    public record Frame(boolean replacesVanilla, float yaw, float pitch) {
        public static final Frame NONE = new Frame(false, 0, 0);

        public boolean apply(PoseStack pose) {
            if (!replacesVanilla) return false;
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            return true;
        }
    }

    public static Frame capture(LocalPlayer player, float partialTicks) {
        return capture(player.hurtTime - partialTicks, player.hurtDuration, player.getHurtDir(),
                System.currentTimeMillis() - hurtByGunTimeStamp, lastTweakMultiplier);
    }

    public static Frame capture(float hurt, int duration, float direction, long elapsedMillis, float multiplier) {
        if (elapsedMillis > 500) return Frame.NONE;
        if (hurt < 0) return new Frame(true, 0, 0);
        hurt /= duration;
        hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
        return new Frame(true, direction * multiplier, -hurt * multiplier * 14.0F);
    }

    public static boolean onHurtBobTweak(LocalPlayer player, PoseStack matrixStack, float partialTicks) {
        return capture(player, partialTicks).apply(matrixStack);
    }

    public static void markTimestamp(float tweakMultiplier) {
        hurtByGunTimeStamp = System.currentTimeMillis();
        lastTweakMultiplier = tweakMultiplier;
    }
}
