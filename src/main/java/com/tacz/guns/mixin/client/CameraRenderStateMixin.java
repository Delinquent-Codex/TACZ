package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.other.GunCameraStateAccess;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = CameraRenderState.class, remap = false)
public class CameraRenderStateMixin implements GunCameraStateAccess {
    @Unique private GunHurtBobTweak.Frame tacz$hurtBob = GunHurtBobTweak.Frame.NONE;
    @Override public GunHurtBobTweak.Frame tacz$getGunHurtBob() { return tacz$hurtBob; }
    @Override public void tacz$setGunHurtBob(GunHurtBobTweak.Frame frame) { tacz$hurtBob = frame; }
}
