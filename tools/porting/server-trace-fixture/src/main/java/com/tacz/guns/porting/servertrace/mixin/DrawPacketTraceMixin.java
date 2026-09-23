package com.tacz.guns.porting.servertrace.mixin;

import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import com.tacz.guns.porting.servertrace.ServerTrace;
import net.minecraftforge.event.network.CustomPayloadEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientMessagePlayerDrawGun.class, remap = false)
public abstract class DrawPacketTraceMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private static void received(ClientMessagePlayerDrawGun message, CustomPayloadEvent.Context context,
                                 CallbackInfo callback) {
        ServerTrace.drawPacket(context);
    }
}
