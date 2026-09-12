package com.tacz.guns.client.animation.third;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import java.util.Optional;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

public class InnerThirdPersonManager {
    public static void setRotationAnglesHead(LivingEntity entity, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head, float limbSwingAmount) {
        if (!(entity instanceof IGunOperator operator)) return;
        activeDisplay(entity).ifPresent(display -> {
            if (PlayerAnimatorCompat.hasPlayerAnimator3rd(entity, display)) {
                PlayerAnimatorCompat.playAnimation(entity, display, limbSwingAmount);
            } else {
                playVanillaAnimation(entity, rightArm, leftArm, body, head, operator, display);
            }
        });
    }

    public static <S extends HumanoidRenderState> HumanoidGunPose captureGunPose(LivingEntity entity, HumanoidModel<S> model, S state) {
        if (!(entity instanceof IGunOperator operator)) return HumanoidGunPose.EMPTY;
        return activeDisplay(entity).map(display -> {
            if (PlayerAnimatorCompat.hasPlayerAnimator3rd(entity, display)) {
                PlayerAnimatorCompat.playAnimation(entity, display, state.walkAnimationSpeed);
                return HumanoidGunPose.EMPTY;
            }
            return HumanoidGunPose.capture(model, state, () ->
                    playVanillaAnimation(entity, model.rightArm, model.leftArm, model.body, model.head, operator, display));
        }).orElse(HumanoidGunPose.EMPTY);
    }

    private static Optional<GunDisplayInstance> activeDisplay(LivingEntity entity) {
        if (Minecraft.getInstance().isPaused()) return Optional.empty();
        ItemStack mainHandItem = entity.getMainHandItem();
        if (IGun.getIGunOrNull(mainHandItem) == null || entity.getPose() == Pose.SLEEPING
                || entity.onClimbable() || entity.isSwimming() || entity.getPose() == Pose.FALL_FLYING) {
            PlayerAnimatorCompat.stopAllAnimation(entity);
            return Optional.empty();
        }
        return TimelessAPI.getGunDisplay(mainHandItem);
    }

    private static void playVanillaAnimation(LivingEntity entityIn, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head, IGunOperator operator, GunDisplayInstance display) {
        String animation = display.getThirdPersonAnimation();
        float aimingProgress = operator.getSynAimingProgress();
        if (aimingProgress <= 0) {
            ThirdPersonManager.getAnimation(animation).animateGunHold(entityIn, rightArm, leftArm, body, head);
        } else {
            ThirdPersonManager.getAnimation(animation).animateGunAim(entityIn, rightArm, leftArm, body, head, aimingProgress);
        }
    }
}
