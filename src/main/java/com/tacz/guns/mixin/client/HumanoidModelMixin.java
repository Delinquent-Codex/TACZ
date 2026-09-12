package com.tacz.guns.mixin.client;

import com.tacz.guns.client.animation.third.HumanoidGunStateAccess;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, remap = false)
public class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void tacz$applyGunPose(HumanoidRenderState state, CallbackInfo ci) {
        ((HumanoidGunStateAccess) state).tacz$getGunPose().apply((HumanoidModel<?>) (Object) this);
    }
}
