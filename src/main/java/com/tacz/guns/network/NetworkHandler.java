package com.tacz.guns.network;

import com.tacz.guns.GunMod;
import com.tacz.guns.network.message.*;
import com.tacz.guns.network.message.event.*;
import com.tacz.guns.network.message.handshake.Acknowledge;
import com.tacz.guns.network.message.handshake.ServerMessageSyncedEntityDataMapping;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.concurrent.atomic.AtomicInteger;

public class NetworkHandler {
    // Target-only protocol: exact version required on both ends.
    public static final int VERSION = 262002;

    public static final SimpleChannel HANDSHAKE_CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "handshake")).networkProtocolVersion(VERSION).simpleChannel();
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "network")).networkProtocolVersion(VERSION).simpleChannel();

    private static final AtomicInteger ID_COUNT = new AtomicInteger(1);

    public static void init() {
        CHANNEL.messageBuilder(ClientMessagePlayerShoot.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerShoot::encode).decoder(ClientMessagePlayerShoot::decode).consumerNetworkThread(ClientMessagePlayerShoot::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerReloadGun.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerReloadGun::encode).decoder(ClientMessagePlayerReloadGun::decode).consumerNetworkThread(ClientMessagePlayerReloadGun::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerCancelReload.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerCancelReload::encode).decoder(ClientMessagePlayerCancelReload::decode).consumerNetworkThread(ClientMessagePlayerCancelReload::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerFireSelect.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerFireSelect::encode).decoder(ClientMessagePlayerFireSelect::decode).consumerNetworkThread(ClientMessagePlayerFireSelect::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerAim.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerAim::encode).decoder(ClientMessagePlayerAim::decode).consumerNetworkThread(ClientMessagePlayerAim::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerCrawl.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerCrawl::encode).decoder(ClientMessagePlayerCrawl::decode).consumerNetworkThread(ClientMessagePlayerCrawl::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerDrawGun.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerDrawGun::encode).decoder(ClientMessagePlayerDrawGun::decode).consumerNetworkThread(ClientMessagePlayerDrawGun::handle).add();
        CHANNEL.messageBuilder(ServerMessageSound.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageSound::encode).decoder(ServerMessageSound::decode).consumerNetworkThread(ServerMessageSound::handle).add();
        CHANNEL.messageBuilder(ClientMessageCraft.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessageCraft::encode).decoder(ClientMessageCraft::decode).consumerNetworkThread(ClientMessageCraft::handle).add();
        CHANNEL.messageBuilder(ServerMessageCraft.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageCraft::encode).decoder(ServerMessageCraft::decode).consumerNetworkThread(ServerMessageCraft::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerZoom.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerZoom::encode).decoder(ClientMessagePlayerZoom::decode).consumerNetworkThread(ClientMessagePlayerZoom::handle).add();
        CHANNEL.messageBuilder(ClientMessageRefitGun.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessageRefitGun::encode).decoder(ClientMessageRefitGun::decode).consumerNetworkThread(ClientMessageRefitGun::handle).add();
        CHANNEL.messageBuilder(ServerMessageRefreshRefitScreen.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageRefreshRefitScreen::encode).decoder(ServerMessageRefreshRefitScreen::decode).consumerNetworkThread(ServerMessageRefreshRefitScreen::handle).add();
        CHANNEL.messageBuilder(ClientMessageUnloadAttachment.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessageUnloadAttachment::encode).decoder(ClientMessageUnloadAttachment::decode).consumerNetworkThread(ClientMessageUnloadAttachment::handle).add();
        CHANNEL.messageBuilder(ServerMessageSwapItem.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageSwapItem::encode).decoder(ServerMessageSwapItem::decode).consumerNetworkThread(ServerMessageSwapItem::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerBoltGun.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerBoltGun::encode).decoder(ClientMessagePlayerBoltGun::decode).consumerNetworkThread(ClientMessagePlayerBoltGun::handle).add();
        CHANNEL.messageBuilder(ServerMessageLevelUp.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageLevelUp::encode).decoder(ServerMessageLevelUp::decode).consumerNetworkThread(ServerMessageLevelUp::handle).add();
        CHANNEL.messageBuilder(ServerMessageGunHurt.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunHurt::encode).decoder(ServerMessageGunHurt::decode).consumerNetworkThread(ServerMessageGunHurt::handle).add();
        CHANNEL.messageBuilder(ServerMessageGunKill.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunKill::encode).decoder(ServerMessageGunKill::decode).consumerNetworkThread(ServerMessageGunKill::handle).add();
        CHANNEL.messageBuilder(ServerMessageUpdateEntityData.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageUpdateEntityData::encode).decoder(ServerMessageUpdateEntityData::decode).consumerNetworkThread(ServerMessageUpdateEntityData::handle).add();
        CHANNEL.messageBuilder(ServerMessageSyncGunPack.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageSyncGunPack::encode).decoder(ServerMessageSyncGunPack::decode).consumerNetworkThread(ServerMessageSyncGunPack::handle).add();
        CHANNEL.messageBuilder(ClientMessagePlayerMelee.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessagePlayerMelee::encode).decoder(ClientMessagePlayerMelee::decode).consumerNetworkThread(ClientMessagePlayerMelee::handle).add();

        CHANNEL.messageBuilder(ServerMessageGunDraw.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunDraw::encode).decoder(ServerMessageGunDraw::decode).consumerNetworkThread(ServerMessageGunDraw::handle).add();
        CHANNEL.messageBuilder(ServerMessageGunFire.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunFire::encode).decoder(ServerMessageGunFire::decode).consumerNetworkThread(ServerMessageGunFire::handle).add();
        CHANNEL.messageBuilder(ServerMessageGunFireSelect.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunFireSelect::encode).decoder(ServerMessageGunFireSelect::decode).consumerNetworkThread(ServerMessageGunFireSelect::handle).add();
        CHANNEL.messageBuilder(ServerMessageGunMelee.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunMelee::encode).decoder(ServerMessageGunMelee::decode).consumerNetworkThread(ServerMessageGunMelee::handle).add();
        CHANNEL.messageBuilder(ServerMessageGunReload.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunReload::encode).decoder(ServerMessageGunReload::decode).consumerNetworkThread(ServerMessageGunReload::handle).add();
        CHANNEL.messageBuilder(ServerMessageGunShoot.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageGunShoot::encode).decoder(ServerMessageGunShoot::decode).consumerNetworkThread(ServerMessageGunShoot::handle).add();
        CHANNEL.messageBuilder(ServerMessageSyncBaseTimestamp.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageSyncBaseTimestamp::encode).decoder(ServerMessageSyncBaseTimestamp::decode).consumerNetworkThread(ServerMessageSyncBaseTimestamp::handle).add();
        CHANNEL.messageBuilder(ClientMessageSyncBaseTimestamp.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessageSyncBaseTimestamp::encode).decoder(ClientMessageSyncBaseTimestamp::decode).consumerNetworkThread(ClientMessageSyncBaseTimestamp::handle).add();

        CHANNEL.messageBuilder(ClientMessageLaserColor.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientMessageLaserColor::encode).decoder(ClientMessageLaserColor::decode).consumerNetworkThread(ClientMessageLaserColor::handle).add();

        CHANNEL.messageBuilder(ServerMessageSyncRecipes.class, ID_COUNT.getAndIncrement(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerMessageSyncRecipes::encode).decoder(ServerMessageSyncRecipes::decode)
                .consumerNetworkThread(ServerMessageSyncRecipes::handle).add();

        CHANNEL.build();
        registerConfigurationMessages();
        GatherLoginConfigurationTasksEvent.BUS.addListener(Priority.LOWEST,
                event -> event.addTask(new MappingConfigurationTask()));
    }

    private static void registerConfigurationMessages() {
        HANDSHAKE_CHANNEL.messageBuilder(Acknowledge.class, 1, NetworkDirection.CONFIGURATION_TO_SERVER)
                .encoder(Acknowledge::encode).decoder(Acknowledge::decode).consumerNetworkThread(Acknowledge::handle).add();
        HANDSHAKE_CHANNEL.messageBuilder(ServerMessageSyncedEntityDataMapping.class, 2, NetworkDirection.CONFIGURATION_TO_CLIENT)
                .encoder(ServerMessageSyncedEntityDataMapping::encode).decoder(ServerMessageSyncedEntityDataMapping::decode)
                .consumerNetworkThread(ServerMessageSyncedEntityDataMapping::handle).add();
        HANDSHAKE_CHANNEL.build();
    }

    public static void sendToServer(Object message) {
        CHANNEL.send(message, PacketDistributor.SERVER.noArg());
    }

    public static void sendToClientPlayer(Object message, Player player) {
        CHANNEL.send(message, PacketDistributor.PLAYER.with((ServerPlayer) player));
    }

    /**
     * 发送给所有监听此实体的玩家
     */
    public static void sendToTrackingEntityAndSelf(Entity centerEntity, Object message) {
        CHANNEL.send(message, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(centerEntity));
    }

    public static void sendToAllPlayers(Object message) {
        CHANNEL.send(message, PacketDistributor.ALL.noArg());
    }

    public static void sendToTrackingEntity(Object message, final Entity centerEntity) {
        CHANNEL.send(message, PacketDistributor.TRACKING_ENTITY.with(centerEntity));
    }

    public static void sendToDimension(Object message, final Entity centerEntity) {
        ResourceKey<Level> dimension = centerEntity.level().dimension();
        CHANNEL.send(message, PacketDistributor.DIMENSION.with(dimension));
    }
}
