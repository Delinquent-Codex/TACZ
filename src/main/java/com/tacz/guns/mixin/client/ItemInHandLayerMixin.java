package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.renderer.other.GunLayerStateAccess;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ItemInHandLayer.class, remap = false)
public class ItemInHandLayerMixin {
    @WrapMethod(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V")
    private void tacz$submitGuns(PoseStack pose, SubmitNodeCollector collector, int light, ArmedEntityRenderState state,
                                 float yRot, float xRot, Operation<Void> original) {
        boolean previousFlash = MuzzleFlashRender.isSelf;
        boolean previousShell = ShellRender.isSelf;
        try {
            original.call(pose, collector, light, state, yRot, xRot);
            MuzzleFlashRender.isSelf = false;
            ShellRender.isSelf = false;
            HumanoidOffhandRender.renderGun(((GunLayerStateAccess) state).tacz$getGunLayerState(), pose, collector, light, state.outlineColor);
        } finally {
            MuzzleFlashRender.isSelf = previousFlash;
            ShellRender.isSelf = previousShell;
        }
    }

    @WrapMethod(method = "submitArmWithItem")
    private void tacz$submitArm(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                PoseStack pose, SubmitNodeCollector collector, int light, Operation<Void> original) {
        var guns = ((GunLayerStateAccess) state).tacz$getGunLayerState();
        // Preserve the source's left-arm suppression while holding a main-hand gun.
        if (guns.mainHandGun() && arm == HumanoidArm.LEFT) return;
        boolean previousFlash = MuzzleFlashRender.isSelf;
        boolean previousShell = ShellRender.isSelf;
        try {
            MuzzleFlashRender.isSelf = guns.localPlayer();
            ShellRender.isSelf = guns.localPlayer();
            original.call(state, item, stack, arm, pose, collector, light);
        } finally {
            MuzzleFlashRender.isSelf = previousFlash;
            ShellRender.isSelf = previousShell;
        }
    }
}
