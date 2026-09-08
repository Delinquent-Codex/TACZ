package com.tacz.guns.network.message;

import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class ServerMessageSwapItem {
    public ServerMessageSwapItem() {
    }

    public static void encode(ServerMessageSwapItem message, FriendlyByteBuf buf) {
    }

    public static ServerMessageSwapItem decode(FriendlyByteBuf buf) {
        return new ServerMessageSwapItem();
    }

    public static void handle(ServerMessageSwapItem message, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            context.enqueueWork(() -> {
                SwapItemWithOffHand.BUS.post(new SwapItemWithOffHand());
            });
        }
        context.setPacketHandled(true);
    }
}
