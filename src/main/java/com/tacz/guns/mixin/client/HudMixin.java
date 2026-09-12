package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tacz.guns.client.event.PreventsHotbarEvent;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Hud.class, remap = false)
public class HudMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void tacz$suppressBackground(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        if (PreventsHotbarEvent.shouldSuppressOverlays()) {
            // The source crosshair listener receives cancelled overlay events,
            // retaining hit markers while the rest of the gun-screen HUD is hidden.
            RenderCrosshairEvent.extract(graphics, delta);
        } else {
            original.call(graphics, delta);
        }
    }
}
