package com.tacz.guns.mixin.client;

import com.tacz.guns.client.animation.third.HumanoidGunPose;
import com.tacz.guns.client.animation.third.HumanoidGunStateAccess;
import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderer.class, remap = false)
public class EntityRendererMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void tacz$captureGunAnimation(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> ci) {
        if (entity instanceof LivingEntity living && ci.getReturnValue() instanceof HumanoidRenderState state
                && (Object) this instanceof LivingEntityRenderer<?, ?, ?> renderer && renderer.getModel() instanceof HumanoidModel<?> rawModel) {
            var access = (HumanoidGunStateAccess) state;
            access.tacz$setGunPose(HumanoidGunPose.EMPTY);
            if (state.ageInTicks == 0) return;
            HumanoidModel<HumanoidRenderState> model = (HumanoidModel<HumanoidRenderState>) rawModel;
            // All subclass extraction has completed. Public callbacks still receive the actual entity here,
            // while later model/layer rendering consumes only captured part values.
            access.tacz$setGunPose(InnerThirdPersonManager.captureGunPose(living, model, state));
        }
    }
}
