package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.client.renderer.other.GunCameraStateAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererMixin {
    @Unique private boolean tacz$renderingHand;

    @WrapMethod(method = "renderItemInHand")
    private void tacz$handBobScope(CameraRenderState state, float partialTick, Matrix4fc modelView, Operation<Void> original) {
        boolean previous = tacz$renderingHand;
        tacz$renderingHand = true;
        try {
            original.call(state, partialTick, modelView);
        } finally {
            tacz$renderingHand = previous;
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tacz$hurtBob(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (((GunCameraStateAccess) state).tacz$getGunHurtBob().apply(pose)) {
            ci.cancel();
            return;
        }
        boolean cancel = tacz$renderingHand
                ? RenderItemInHandBobEvent.BobHurt.BUS.post(new RenderItemInHandBobEvent.BobHurt())
                : RenderLevelBobEvent.BobHurt.BUS.post(new RenderLevelBobEvent.BobHurt());
        if (cancel) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void tacz$viewBob(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        boolean cancel = tacz$renderingHand
                ? RenderItemInHandBobEvent.BobView.BUS.post(new RenderItemInHandBobEvent.BobView())
                : RenderLevelBobEvent.BobView.BUS.post(new RenderLevelBobEvent.BobView());
        if (cancel) ci.cancel();
    }
}
