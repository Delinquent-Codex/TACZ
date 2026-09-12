package com.tacz.guns.mixin.client;

import com.tacz.guns.client.animation.third.HumanoidGunPose;
import com.tacz.guns.client.animation.third.HumanoidGunStateAccess;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = HumanoidRenderState.class, remap = false)
public class HumanoidRenderStateMixin implements HumanoidGunStateAccess {
    @Unique private HumanoidGunPose tacz$gunPose = HumanoidGunPose.EMPTY;
    @Override public HumanoidGunPose tacz$getGunPose() { return tacz$gunPose; }
    @Override public void tacz$setGunPose(HumanoidGunPose pose) { tacz$gunPose = pose; }
}
