package com.tacz.guns.mixin.common;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayer.class, remap = false)
public class ServerPlayerMixin {
    @Unique
    private int tacz$lastSelectedSlot = -1;

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    public void initialGunOperateData(ServerPlayer pThat, boolean pKeepEverything, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        IGunOperator.fromLivingEntity(player).initialData();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tacz$reconcileSelectedGun(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (tacz$lastSelectedSlot == -1) {
            tacz$lastSelectedSlot = selectedSlot;
            return;
        }
        if (selectedSlot == tacz$lastSelectedSlot) {
            return;
        }
        tacz$lastSelectedSlot = selectedSlot;
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        var data = operator.getDataHolder();
        // A client draw packet normally binds the new slot first. Reconcile only
        // when it has not, retaining the normal draw reset and cooldown path.
        if (data.lastDrawnSlot != selectedSlot || data.lastDrawnStack != player.getMainHandItem()) {
            // Bind the selected slot, not a dynamic main-hand lookup; a later
            // slot change must still be distinguishable from this one.
            operator.draw(() -> player.getInventory().getItem(selectedSlot));
        }
    }
}
