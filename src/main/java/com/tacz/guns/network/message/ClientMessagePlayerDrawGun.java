package com.tacz.guns.network.message;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class ClientMessagePlayerDrawGun {
    public ClientMessagePlayerDrawGun() {
    }

    public static void encode(ClientMessagePlayerDrawGun message, FriendlyByteBuf buf) {
    }

    public static ClientMessagePlayerDrawGun decode(FriendlyByteBuf buf) {
        return new ClientMessagePlayerDrawGun();
    }

    public static void handle(ClientMessagePlayerDrawGun message, CustomPayloadEvent.Context context) {
        if (context.isServerSide()) {
            context.enqueueWork(() -> {
                ServerPlayer entity = context.getSender();
                if (entity == null) {
                    return;
                }
                Inventory inventory = entity.getInventory();
                int selected = inventory.getSelectedSlot();
                IGunOperator operator = IGunOperator.fromLivingEntity(entity);
                var data = operator.getDataHolder();
                // A selected-slot change may already have been reconciled on the
                // server tick. Its later client packet must not reset an active
                // reload or the firing cooldown for the same stack.
                if (data.lastDrawnSlot == selected && data.lastDrawnStack == inventory.getItem(selected)) {
                    return;
                }
                operator.draw(() -> inventory.getItem(selected));
            });
        }
        context.setPacketHandled(true);
    }
}
