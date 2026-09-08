package com.tacz.guns.network.message.handshake;

import com.tacz.guns.network.MappingConfigurationTask;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Acknowledges exactly one pending configuration snapshot on this connection. */
public record Acknowledge(long token) {
    public static void encode(Acknowledge message, FriendlyByteBuf buffer) { buffer.writeLong(message.token); }
    public static Acknowledge decode(FriendlyByteBuf buffer) { return new Acknowledge(buffer.readLong()); }
    public static void handle(Acknowledge message, CustomPayloadEvent.Context context) {
        if (context.isServerSide()) {
            context.enqueueWork(() -> MappingConfigurationTask.acknowledge(message.token, context));
        }
        context.setPacketHandled(true);
    }
}
