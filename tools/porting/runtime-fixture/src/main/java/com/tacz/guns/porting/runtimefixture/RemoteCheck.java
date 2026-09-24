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
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFireSelectEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientRecipeCache;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerShoot;
import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.LogicalSide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client-only instrumentation. All authoritative setup/observations use ordinary server RCON. */
final class RemoteCheck {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path directory = Path.of(System.getProperty("tacz.fixture.control"));
    private final String address = System.getProperty("tacz.fixture.remote");
    private final Map<String, Integer> shots = new LinkedHashMap<>();
    private final Map<String, Integer> reloads = new LinkedHashMap<>();
    private final Map<String, Integer> fires = new LinkedHashMap<>();
    private final Map<String, Integer> fireSelects = new LinkedHashMap<>();
    private final List<Map<String, Object>> gunHurts = new ArrayList<>();
    private final List<Map<String, Object>> gunKills = new ArrayList<>();
    private final List<Map<String, Object>> draws = new ArrayList<>();
    private OperatorSequence sequence;
    private final Screen inputBarrier = new Screen(Component.literal("TACZ remote operator fixture")) {
        @Override public boolean isPauseScreen() { return false; }
    };
    private int projectiles;
    private final Set<UUID> projectileIds = new HashSet<>();
    private final List<Map<String, Object>> projectileJoins = new ArrayList<>();
    private int lastCommand;
    private String previousReloadPhase;
    private final List<Map<String, Object>> reloadTransitions = new ArrayList<>();
    private Map<String, Object> previousGunState;
    private final List<Map<String, Object>> gunTransitions = new ArrayList<>();

    static void start() { new RemoteCheck().startListening(); }

    private void startListening() {
        GunDrawEvent.BUS.addListener((GunDrawEvent event) -> {
            if (event.getLogicalSide() == LogicalSide.CLIENT && event.getEntity() == minecraft.player) {
                var row = new LinkedHashMap<String, Object>();
                row.put("previous", String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getPreviousGunItem().getItem())));
                row.put("current", String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getCurrentGunItem().getItem())));
                row.put("selected_slot", minecraft.player.getInventory().getSelectedSlot());
                row.put("player_tick", minecraft.player.tickCount);
                draws.add(row);
            }
        });
        GunShootEvent.BUS.addListener((GunShootEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLogicalSide() == LogicalSide.CLIENT)
                shots.merge(event.getShooter().getName().getString(), 1, Integer::sum);
        });
        GunReloadEvent.BUS.addListener((GunReloadEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLogicalSide() == LogicalSide.CLIENT)
                reloads.merge(event.getEntity().getName().getString(), 1, Integer::sum);
        });
        GunFireEvent.BUS.addListener((GunFireEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLogicalSide() == LogicalSide.CLIENT)
                fires.merge(event.getShooter().getName().getString(), 1, Integer::sum);
        });
        GunFireSelectEvent.BUS.addListener((GunFireSelectEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLogicalSide() == LogicalSide.CLIENT)
                fireSelects.merge(event.getShooter().getName().getString(), 1, Integer::sum);
        });
        EntityHurtByGunEvent.Post.BUS.addListener((EntityHurtByGunEvent.Post event) -> {
            if (event.getLogicalSide() == LogicalSide.CLIENT)
                gunHurts.add(hit(event.getHurtEntity(), event.getAttacker(),
                        event.getGunId().toString(), event.getBaseAmount(), event.isHeadShot()));
        });
        EntityKillByGunEvent.BUS.addListener((EntityKillByGunEvent event) -> {
            if (event.getLogicalSide() == LogicalSide.CLIENT)
                gunKills.add(hit(event.getKilledEntity(), event.getAttacker(),
                        event.getGunId().toString(), event.getBaseDamage(), event.isHeadShot()));
        });
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event, boolean cancelled) -> {
            if (!cancelled && event.getLevel().isClientSide() && event.getEntity() instanceof EntityKineticBullet bullet) {
                projectiles++;
                var joined = new LinkedHashMap<String, Object>();
                joined.put("id", bullet.getId());
                joined.put("uuid", bullet.getUUID().toString());
                joined.put("repeated", !projectileIds.add(bullet.getUUID()));
                joined.put("player_tick", minecraft.player == null ? -1 : minecraft.player.tickCount);
                joined.put("position", List.of(bullet.getX(), bullet.getY(), bullet.getZ()));
                var motion = bullet.getDeltaMovement();
                joined.put("motion", List.of(motion.x, motion.y, motion.z));
                projectileJoins.add(joined);
            }
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::tick);
    }

    private Map<String, Object> hit(net.minecraft.world.entity.Entity target, LivingEntity attacker,
                                    String gun, float damage, boolean headshot) {
        var row = new LinkedHashMap<String, Object>();
        row.put("target", target == null ? null : target.getUUID().toString());
        row.put("attacker", attacker == null ? null : attacker.getUUID().toString());
        row.put("gun", gun);
        row.put("damage", damage);
        row.put("headshot", headshot);
        return row;
    }

    private Map<String, Object> playerState(Player player) {
        var data = new LinkedHashMap<String, Object>();
        data.put("uuid", player.getUUID().toString());
        data.put("entity_id", player.getId());
        data.put("position", List.of(player.getX(), player.getY(), player.getZ()));
        data.put("rotation", List.of(player.getYRot(), player.getXRot()));
        data.put("health", player.getHealth());
        data.put("armor", player.getArmorValue());
        data.put("armor_toughness", player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        data.put("diamond_chestplate", player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE));
        var stack = player.getMainHandItem();
        if (stack.getItem() instanceof IGun gun) {
            data.put("gun", gun.getGunId(stack).toString());
            data.put("magazine", gun.getCurrentAmmoCount(stack));
            data.put("chamber", gun.hasBulletInBarrel(stack));
            data.put("fire_mode", gun.getFireMode(stack).name());
            data.put("heat", gun.getHeatAmount(stack));
            data.put("overheated", gun.isOverheatLocked(stack));
        }
        data.put("reloading", IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading());
        data.put("reload_phase", IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().name());
        data.put("bolting", IGunOperator.fromLivingEntity(player).getSynIsBolting());
        return data;
    }

    private Map<String, Object> snapshot() {
        var data = new LinkedHashMap<String, Object>();
        data.put("connected", minecraft.player != null && minecraft.level != null);
        data.put("dimension", minecraft.level == null ? null : minecraft.level.dimension().identifier().toString());
        data.put("screen", String.valueOf(minecraft.gui.screen()));
        data.put("input_barrier_owned", minecraft.gui.screen() == inputBarrier);
        data.put("native_input_suppressed", !com.tacz.guns.util.InputExtraCheck.isInGame());
        data.put("dedicated_connection", minecraft.getSingleplayerServer() == null);
        data.put("backend", RenderSystem.getDevice().getDeviceInfo().backendName());
        data.put("client_guns", TimelessAPI.getAllClientGunIndex().size());
        data.put("common_guns", TimelessAPI.getAllCommonGunIndex().size());
        data.put("common_ammo", TimelessAPI.getAllCommonAmmoIndex().size());
        data.put("common_attachments", TimelessAPI.getAllCommonAttachmentIndex().size());
        data.put("recipes", ClientRecipeCache.all().size());
        data.put("shots", new LinkedHashMap<>(shots));
        data.put("reloads", new LinkedHashMap<>(reloads));
        data.put("fires", new LinkedHashMap<>(fires));
        data.put("fire_selects", new LinkedHashMap<>(fireSelects));
        data.put("gun_hurts", List.copyOf(gunHurts));
        data.put("gun_kills", List.copyOf(gunKills));
        data.put("draws", List.copyOf(draws));
        data.put("sequence", sequence == null ? Map.of("status", "idle", "ticks", 0) : sequence.summary());
        data.put("projectiles", projectiles);
        data.put("unique_projectiles", projectileIds.size());
        data.put("projectile_rejoins", projectiles - projectileIds.size());
        data.put("auto_reload", com.tacz.guns.config.client.KeyConfig.AUTO_RELOAD.get());
        data.put("reload_transitions", List.copyOf(reloadTransitions));
        data.put("gun_transitions", List.copyOf(gunTransitions));
        var players = new LinkedHashMap<String, Object>();
        if (minecraft.level != null)
            for (var player : minecraft.level.players()) players.put(player.getName().getString(), playerState(player));
        data.put("players", players);
        if (minecraft.player != null) {
            var ammo = new LinkedHashMap<String, Integer>();
            for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
                var stack = minecraft.player.getInventory().getItem(slot);
                if (stack.getItem() instanceof IAmmo item)
                    ammo.merge(item.getAmmoId(stack).toString(), stack.getCount(), Integer::sum);
            }
            data.put("inventory_ammo", ammo);
            data.put("charge", IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).getChargeProgress());
            data.put("shoot_cooldown", IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).getClientShootCoolDown());
            data.put("state_locked", IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).getDataHolder().clientStateLock);
            data.put("game_mode", minecraft.gameMode.getPlayerMode().name());
            data.put("reserve_slot_count", minecraft.player.getInventory().getItem(9).getCount());
            data.put("selected_slot", minecraft.player.getInventory().getSelectedSlot());
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
        if (sequence != null) sequence.tick(minecraft);
        // Observe every client tick, including short finishing phases between
        // controller requests. These are synchronized game states, not injected ones.
        if (minecraft.level != null) {
            for (var player : minecraft.level.players()) {
                if (!player.getName().getString().equals("TaczShooter")) continue;
                var value = playerState(player);
                var comparable = new LinkedHashMap<>(value);
                comparable.remove("heat"); // Continuous heat values are observed in snapshots/sequence traces.
                comparable.remove("position");
                comparable.remove("rotation");
                if (!comparable.equals(previousGunState)) {
                    previousGunState = comparable;
                    var observed = new LinkedHashMap<>(value);
                    observed.put("player_tick", player.tickCount);
                    gunTransitions.add(observed);
                }
                String phase = (String) value.get("reload_phase");
                if (!phase.equals(previousReloadPhase)) {
                    value.put("player_tick", player.tickCount);
                    reloadTransitions.add(value);
                    previousReloadPhase = phase;
                }
            }
        } else {
            previousReloadPhase = null;
            previousGunState = null;
        }
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
                case "invalid_shoot_timestamp" -> NetworkHandler.sendToServer(
                        new ClientMessagePlayerShoot(command.get("timestamp").getAsLong(), 0f));
                case "shoot_twice" -> {
                    var operator = IClientPlayerGunOperator.fromLocalPlayer(minecraft.player);
                    response.put("shoot_results", List.of(operator.shoot().toString(), operator.shoot().toString()));
                }
                case "reload" -> IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).reload();
                case "draw_packet" -> NetworkHandler.sendToServer(new ClientMessagePlayerDrawGun());
                case "fire_select" -> IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).fireSelect();
                case "input_guard" -> {
                    if (command.get("enabled").getAsBoolean()) {
                        if (minecraft.gui.screen() != null && minecraft.gui.screen() != inputBarrier)
                            throw new IllegalStateException("Cannot replace an existing screen");
                        minecraft.gui.setScreen(inputBarrier);
                    } else {
                        if (minecraft.gui.screen() != inputBarrier)
                            throw new IllegalStateException("Input barrier is no longer owned");
                        minecraft.gui.setScreen(null);
                    }
                }
                case "sequence_start" -> {
                    if (sequence != null && sequence.summary().get("status").equals("running"))
                        throw new IllegalStateException("Sequence already running");
                    sequence = new OperatorSequence(command, minecraft,
                            minecraft.gui.screen() == inputBarrier ? inputBarrier : null);
                }
                case "sequence_result" -> response.put("trace", sequence == null ? List.of() : sequence.trace());
                case "projectile_trace" -> response.put("projectile_trace", List.copyOf(projectileJoins));
                case "respawn" -> {
                    if (!(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen)
                            || minecraft.player == null || minecraft.player.isAlive())
                        throw new IllegalStateException("Native death screen is not ready for respawn");
                    minecraft.player.respawn();
                }
                case "select_slot" -> {
                    int slot = command.get("slot").getAsInt();
                    if (slot < 0 || slot > 8) throw new IllegalArgumentException("Not a hotbar slot: " + slot);
                    minecraft.player.getInventory().setSelectedSlot(slot);
                }
                case "inventory_swap" -> {
                    int from = command.get("from").getAsInt();
                    int to = command.get("to").getAsInt();
                    if (from < 0 || from > 8 || to < 0 || to > 8 || from == to)
                        throw new IllegalArgumentException("Invalid hotbar swap: " + from + " -> " + to);
                    if (minecraft.player.containerMenu != minecraft.player.inventoryMenu)
                        throw new IllegalStateException("Player inventory is not the active menu");
                    minecraft.gameMode.handleContainerInput(minecraft.player.inventoryMenu.containerId,
                            36 + from, to, ContainerInput.SWAP, minecraft.player);
                }
                case "drop_one" -> response.put("dropped", minecraft.player.drop(false));
                case "disconnect" -> minecraft.disconnectFromWorld(Component.literal("TACZ fixture reconnect check"));
                case "connect" -> ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("TACZ isolated fixture", address, ServerData.Type.OTHER), true, null);
                case "quit" -> {
                    response.put("projectile_trace", List.copyOf(projectileJoins));
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
