package com.tacz.guns.network;

import com.tacz.guns.network.message.handshake.ServerMessageSyncedEntityDataMapping;
import io.netty.util.AttributeKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.config.ConfigurationTaskContext;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Keeps configuration open until the client has installed the complete mapping. */
public final class MappingConfigurationTask implements ConfigurationTask {
    public static final Type TYPE = new Type("tacz:synced_entity_data");
    private static final AttributeKey<MappingConfigurationTask> PENDING = AttributeKey.valueOf("tacz:pending_mapping");
    private final long token = ThreadLocalRandom.current().nextLong();
    private ConfigurationTaskContext taskContext;

    @Override
    public void start(ConfigurationTaskContext context) {
        taskContext = context;
        if (!context.getConnection().channel().attr(PENDING).compareAndSet(null, this)) {
            throw new IllegalStateException("A TACZ mapping task is already pending");
        }
        NetworkHandler.HANDSHAKE_CHANNEL.send(ServerMessageSyncedEntityDataMapping.snapshot(token), context.getConnection());
    }

    public static void acknowledge(long token, CustomPayloadEvent.Context context) {
        var attribute = context.getConnection().channel().attr(PENDING);
        MappingConfigurationTask pending = attribute.get();
        if (pending == null || pending.token != token || !attribute.compareAndSet(pending, null)) {
            context.getConnection().disconnect(Component.literal("[TACZ] Unexpected entity-data mapping acknowledgement"));
            return;
        }
        pending.taskContext.finish(TYPE);
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        throw new IllegalStateException("TACZ mapping synchronization requires Forge's configuration task context");
    }

    @Override
    public Type type() { return TYPE; }
}
