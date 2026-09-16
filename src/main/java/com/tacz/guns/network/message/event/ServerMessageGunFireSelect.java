package com.tacz.guns.network.message.event;

import com.tacz.guns.api.event.common.GunFireSelectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class ServerMessageGunFireSelect {
    private final int shooterId;
    private final ItemStack gunItemStack;

    public ServerMessageGunFireSelect(int shooterId, ItemStack gunItemStack) {
        this.shooterId = shooterId;
        this.gunItemStack = gunItemStack;
    }

    public static void encode(ServerMessageGunFireSelect message, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(message.shooterId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, message.gunItemStack);
    }

    public static ServerMessageGunFireSelect decode(RegistryFriendlyByteBuf buf) {
        int shooterId = buf.readVarInt();
        ItemStack gunItemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new ServerMessageGunFireSelect(shooterId, gunItemStack);
    }

    public static void handle(ServerMessageGunFireSelect message, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            context.enqueueWork(() -> ClientHandler.doClientEvent(message));
        }
        context.setPacketHandled(true);
    }

    // Loading codecs on the server must not verify client-only callback bytecode.
    private static final class ClientHandler {
        private static void doClientEvent(ServerMessageGunFireSelect message) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            if (level.getEntity(message.shooterId) instanceof LivingEntity shooter) {
                GunFireSelectEvent gunFireSelectEvent = new GunFireSelectEvent(shooter, message.gunItemStack, LogicalSide.CLIENT);
                GunFireSelectEvent.BUS.post(gunFireSelectEvent);
            }
        }
    }
}
