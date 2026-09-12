package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.other.GunLayerStateAccess;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ArmedEntityRenderState.class, remap = false)
public class ArmedEntityRenderStateMixin implements GunLayerStateAccess {
    @Unique private HumanoidOffhandRender.State tacz$gunLayerState = new HumanoidOffhandRender.State(false, false, List.of());

    @Override public HumanoidOffhandRender.State tacz$getGunLayerState() { return tacz$gunLayerState; }
    @Override public void tacz$setGunLayerState(HumanoidOffhandRender.State state) { tacz$gunLayerState = state; }

    @Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void tacz$extractGuns(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver resolver, float partialTicks, CallbackInfo ci) {
        ((GunLayerStateAccess) state).tacz$setGunLayerState(HumanoidOffhandRender.extract(entity, resolver));
    }
}
