package com.tacz.guns.network.message;

import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.Map;


public class ServerMessageSyncGunPack {
    private final Map<DataType, Map<Identifier, String>> cache;

    public ServerMessageSyncGunPack(Map<DataType, Map<Identifier, String>> cache) {
        this.cache = cache;
    }

    public static void encode(ServerMessageSyncGunPack message, FriendlyByteBuf buf) {
        buf.writeMap(message.getCache(), FriendlyByteBuf::writeEnum, (buf1, map) -> {
            buf1.writeMap(map, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeUtf);
        });
    }

    public static ServerMessageSyncGunPack decode(FriendlyByteBuf buf) {
        var map = buf.readMap(buf1 -> buf1.readEnum(DataType.class), buf2 -> {
            return buf2.readMap(FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readUtf);
        });
        return new ServerMessageSyncGunPack(map);
    }

    public static void handle(ServerMessageSyncGunPack message, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            boolean remoteConnection = context.getConnection() != null && !context.getConnection().isMemoryConnection();
            context.enqueueWork(() -> doSync(message, remoteConnection));
        }
        context.setPacketHandled(true);
    }


    public Map<DataType, Map<Identifier, String>> getCache() {
        return cache;
    }

    @OnlyIn(Dist.CLIENT)
    private static void doSync(ServerMessageSyncGunPack message, boolean remoteConnection) {
        if (remoteConnection) {
            CommonAssetsManager.clearInstance();
        }
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null) return;
        CommonNetworkCache.INSTANCE.fromNetwork(message.cache, connection.registryAccess());
        // 通知客户端重新构建ClientIndex
        ClientIndexManager.reload();
    }
}
