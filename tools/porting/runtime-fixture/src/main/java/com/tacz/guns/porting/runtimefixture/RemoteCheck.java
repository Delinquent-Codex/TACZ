package com.tacz.guns.porting.runtimefixture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientRecipeCache;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.LogicalSide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only instrumentation. All authoritative setup/observations use ordinary server RCON. */
final class RemoteCheck {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path directory = Path.of(System.getProperty("tacz.fixture.control"));
    private final String address = System.getProperty("tacz.fixture.remote");
    private final Map<String, Integer> shots = new LinkedHashMap<>();
    private final Map<String, Integer> reloads = new LinkedHashMap<>();
    private int projectiles;
    private int lastCommand;

    static void start() { new RemoteCheck().startListening(); }

    private void startListening() {
        GunShootEvent.BUS.addListener((GunShootEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLogicalSide() == LogicalSide.CLIENT)
                shots.merge(event.getShooter().getName().getString(), 1, Integer::sum);
        });
        GunReloadEvent.BUS.addListener((GunReloadEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLogicalSide() == LogicalSide.CLIENT)
                reloads.merge(event.getEntity().getName().getString(), 1, Integer::sum);
        });
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLevel().isClientSide() && event.getEntity() instanceof EntityKineticBullet) projectiles++;
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::tick);
    }

    private Map<String, Object> playerState(Player player) {
        var data = new LinkedHashMap<String, Object>();
        data.put("uuid", player.getUUID().toString());
        var stack = player.getMainHandItem();
        if (stack.getItem() instanceof IGun gun) {
            data.put("gun", gun.getGunId(stack).toString());
            data.put("magazine", gun.getCurrentAmmoCount(stack));
            data.put("chamber", gun.hasBulletInBarrel(stack));
        }
        data.put("reloading", IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading());
        return data;
    }

    private Map<String, Object> snapshot() {
        var data = new LinkedHashMap<String, Object>();
        data.put("connected", minecraft.player != null && minecraft.level != null);
        data.put("screen", String.valueOf(minecraft.gui.screen()));
        data.put("dedicated_connection", minecraft.getSingleplayerServer() == null);
        data.put("backend", RenderSystem.getDevice().getDeviceInfo().backendName());
        data.put("client_guns", TimelessAPI.getAllClientGunIndex().size());
        data.put("common_guns", TimelessAPI.getAllCommonGunIndex().size());
        data.put("common_ammo", TimelessAPI.getAllCommonAmmoIndex().size());
        data.put("common_attachments", TimelessAPI.getAllCommonAttachmentIndex().size());
        data.put("recipes", ClientRecipeCache.all().size());
        data.put("shots", new LinkedHashMap<>(shots));
        data.put("reloads", new LinkedHashMap<>(reloads));
        data.put("projectiles", projectiles);
        var players = new LinkedHashMap<String, Object>();
        if (minecraft.level != null)
            for (var player : minecraft.level.players()) players.put(player.getName().getString(), playerState(player));
        data.put("players", players);
        if (minecraft.player != null) {
            data.put("game_mode", minecraft.gameMode.getPlayerMode().name());
            data.put("reserve_slot_count", minecraft.player.getInventory().getItem(9).getCount());
            data.put("draw_cooldown", IGunOperator.fromLivingEntity(minecraft.player).getSynDrawCoolDown());
        }
        return data;
    }

    private static void write(Path path, Object value) throws Exception {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(value));
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
    }

    private void tick(TickEvent.ClientTickEvent.Post event) {
        if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding)
            onboarding.onClose();
        // Each path is published once. Replacing a file another process is
        // reading races Windows' sharing restrictions, even with atomic move.
        Path input = directory.resolve("command-" + (lastCommand + 1) + ".json");
        if (!Files.isRegularFile(input)) return;
        var response = new LinkedHashMap<String, Object>();
        try {
            JsonObject command = JSON.fromJson(Files.readString(input), JsonObject.class);
            int id = command.get("id").getAsInt();
            if (id <= lastCommand) return;
            lastCommand = id; // Actions execute once, including when later observation fails.
            response.put("id", id);
            String action = command.get("action").getAsString();
            response.put("action", action);
            switch (action) {
                case "snapshot" -> { }
                case "shoot" -> response.put("shoot_result", IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).shoot().toString());
                case "reload" -> IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).reload();
                case "disconnect" -> minecraft.disconnectFromWorld(Component.literal("TACZ fixture reconnect check"));
                case "connect" -> ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("TACZ isolated fixture", address, ServerData.Type.OTHER), true, null);
                case "quit" -> {
                    write(Path.of("runtime-fixture-result.json"), Map.of("status", "passed", "scope",
                            "remote command executor stopped; gameplay assertions belong to multiplayer controller", "snapshot", snapshot()));
                    minecraft.stop();
                }
                default -> throw new IllegalArgumentException("Unknown fixture action: " + action);
            }
            response.put("snapshot", snapshot());
            response.put("status", "ok");
        } catch (Throwable failure) {
            response.put("id", lastCommand);
            response.put("status", "failed");
            response.put("failure", failure.toString());
            failure.printStackTrace();
        }
        try { write(directory.resolve("response-" + lastCommand + ".json"), response); }
        catch (Exception failure) { throw new IllegalStateException("Cannot report remote fixture command", failure); }
    }
}
