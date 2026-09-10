package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.event.FirstPersonRenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin implements KeepingItemRenderer {
    @Shadow
    private float mainHandHeight;
    @Shadow
    private float oMainHandHeight;
    @Shadow
    private ItemStack mainHandItem;
    @Unique
    private ItemStack tacz$KeepItem;
    @Unique
    private long tacz$KeepTimeMs;
    @Unique
    private long tacz$KeepTimestamp;

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    public void beforeHandRender(float pPartialTicks, PoseStack pMatrixStack, SubmitNodeCollector collector, LocalPlayer pPlayerEntity, int pCombinedLight, CallbackInfo ci) {
        BeforeRenderHandEvent.BUS.post(new BeforeRenderHandEvent(pMatrixStack));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void cancelEquippedProgress(CallbackInfo ci) {
        // Preserve the model library's put-away lock after moving its lifecycle into TACZ.
        if (FirstPersonRenderHandler.shouldLockVanilla()) {
            mainHandHeight = 1.0F;
            oMainHandHeight = 1.0F;
            ci.cancel();
        }
    }

    @Unique
    @Override
    public void keep(ItemStack itemStack, long timeMs) {
        long time = System.currentTimeMillis() - tacz$KeepTimestamp;
        if (time < tacz$KeepTimeMs) {
            return;
        }
        this.tacz$KeepTimeMs = timeMs;
        this.tacz$KeepTimestamp = System.currentTimeMillis();
        this.tacz$KeepItem = itemStack;
        this.mainHandItem = itemStack;
    }

    @Override
    public ItemStack getCurrentItem() {
        if (Minecraft.getInstance().player == null) {
            return mainHandItem;
        }
        if (tacz$KeepItem != null) {
            long time = System.currentTimeMillis() - tacz$KeepTimestamp;
            if (time < tacz$KeepTimeMs) {
                return tacz$KeepItem;
            } else {
                tacz$KeepItem = null;
            }
        }
        return mainHandItem;
    }
}
