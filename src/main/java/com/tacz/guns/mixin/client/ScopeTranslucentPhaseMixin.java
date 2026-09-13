package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.scope.ScopeCapture;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TranslucentFeatureRenderPhase.class, remap = false)
public abstract class ScopeTranslucentPhaseMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V", at = @At("HEAD"), cancellable = true)
    private void tacz$captureScope(TranslucentSubmit submit, CallbackInfo ci) {
        if (ScopeCapture.capture(submit)) ci.cancel();
    }
}
