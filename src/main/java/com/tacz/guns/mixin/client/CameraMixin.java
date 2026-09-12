package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.other.GunCameraStateAccess;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, remap = false)
public class CameraMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void tacz$extractGunHurtBob(CameraRenderState state, float partialTicks, CallbackInfo ci) {
        var entity = ((Camera) (Object) this).entity();
        var frame = entity instanceof LocalPlayer player && !player.isDeadOrDying()
                ? GunHurtBobTweak.capture(player, partialTicks) : GunHurtBobTweak.Frame.NONE;
        ((GunCameraStateAccess) state).tacz$setGunHurtBob(frame);
    }
}
