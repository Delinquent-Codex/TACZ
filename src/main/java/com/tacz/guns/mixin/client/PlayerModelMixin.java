package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The target renders first-person arms directly, without calling PlayerModel.setupAnim. */
@Mixin(value = AvatarRenderer.class, remap = false)
public class PlayerModelMixin {
    @Inject(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void tacz$resetFirstPersonArm(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin,
                                         ModelPart arm, boolean sleeve, CallbackInfo ci) {
        if (IGun.getIGunOrNull(KeepingItemRenderer.getRenderer().getCurrentItem()) != null) {
            arm.xRot = arm.yRot = arm.zRot = 0;
            // Sleeves are now children of their arm and inherit this transform automatically.
        }
    }
}
