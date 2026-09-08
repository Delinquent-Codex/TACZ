package com.tacz.guns.network.message.handshake;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.SyncedDataMapping;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.network.CustomPayloadEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ServerMessageSyncedEntityDataMapping(long token, SyncedDataMapping mapping) {
    public static ServerMessageSyncedEntityDataMapping snapshot(long token) {
        SyncedEntityData data = SyncedEntityData.instance();
        var entries = data.getKeys().stream()
                .map(key -> new SyncedDataMapping.Entry(key.classKey().id(), key.id(), data.getInternalId(key)))
                .sorted(Comparator.comparingInt(SyncedDataMapping.Entry::id)).toList();
        return new ServerMessageSyncedEntityDataMapping(token, new SyncedDataMapping(entries));
    }

    public static void encode(ServerMessageSyncedEntityDataMapping message, FriendlyByteBuf buffer) {
        buffer.writeLong(message.token);
        message.mapping.encode(buffer);
    }

    public static ServerMessageSyncedEntityDataMapping decode(FriendlyByteBuf buffer) {
        return new ServerMessageSyncedEntityDataMapping(buffer.readLong(), SyncedDataMapping.decode(buffer));
    }

    public static void handle(ServerMessageSyncedEntityDataMapping message, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            context.enqueueWork(() -> {
                if (!SyncedEntityData.instance().updateMappings(message)) {
                    context.getConnection().disconnect(Component.literal("[TACZ] Incompatible synced entity-data keys; check server and client mods"));
                    return;
                }
                GunMod.LOGGER.debug("Installed {} synced entity-data keys", message.mapping.entries().size());
                NetworkHandler.HANDSHAKE_CHANNEL.reply(new Acknowledge(message.token), context);
            });
        }
        context.setPacketHandled(true);
    }

    /** Snapshot of the previous API's grouped view; modifying it cannot affect the packet. */
    public Map<Identifier, List<Pair<Identifier, Integer>>> getKeyMap() {
        Map<Identifier, List<Pair<Identifier, Integer>>> result = new HashMap<>();
        for (var entry : mapping.entries()) {
            result.computeIfAbsent(entry.classId(), ignored -> new ArrayList<>()).add(Pair.of(entry.keyId(), entry.id()));
        }
        return result;
    }
}
