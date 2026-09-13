package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.scope.ScopeCapture;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimpleFeatureRenderPhase.class, remap = false)
public abstract class ScopeSimplePhaseMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V", at = @At("HEAD"), cancellable = true)
    private void tacz$captureScope(SubmitNode submit, CallbackInfo ci) {
        if (ScopeCapture.capture(submit)) ci.cancel();
    }
}
