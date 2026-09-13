package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.scope.ScopeFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FeatureRenderDispatcher.class, remap = false)
public abstract class ScopeFeatureDispatcherMixin {
    @Shadow @Final private FeatureRendererMap featureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void tacz$registerScopeRenderer(CallbackInfo ci) {
        featureRenderers.put(ScopeFeatureRenderer.TYPE, new ScopeFeatureRenderer());
    }
}
