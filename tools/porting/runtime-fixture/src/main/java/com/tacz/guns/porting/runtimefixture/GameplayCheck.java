package com.tacz.guns.porting.runtimefixture;

import com.google.gson.GsonBuilder;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.LogicalSide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real client actions and server-thread observations; never calls server shoot/reload directly. */
final class GameplayCheck {
    private enum Stage { READY, SHOT, RELOAD, SETTLE }
    private record Snapshot(int magazine, boolean chamber, int reserve, boolean reloading, boolean survival) {}
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Map<String, Object> result;
    private final UUID playerId = minecraft.player.getUUID();
    private final AtomicInteger serverShots = new AtomicInteger();
    private final AtomicInteger serverReloads = new AtomicInteger();
    private final AtomicInteger serverProjectiles = new AtomicInteger();
    private final AtomicInteger clientProjectiles = new AtomicInteger();
    private CompletableFuture<Void> setup;
    private CompletableFuture<Snapshot> observation;
    private Snapshot last;
    private Stage stage = Stage.READY;
    private long stageStart = System.nanoTime();
    private int assertions;
    private int clientShots;
    private int clientReloads;
    private boolean observedServerReload;
    private boolean observedClientReload;
    private volatile boolean finished;

    static void start(Map<String, Object> result, int assertions) {
        new GameplayCheck(result, assertions).start();
    }

    private GameplayCheck(Map<String, Object> result, int assertions) {
        this.result = result;
        this.assertions = assertions;
    }

    private void start() {
        result.put("status", "in-progress");
        GunShootEvent.BUS.addListener((GunShootEvent event, boolean cancelled) -> {
            if (finished || cancelled || !event.getShooter().getUUID().equals(playerId)) return;
            if (event.getLogicalSide() == LogicalSide.SERVER) serverShots.incrementAndGet(); else clientShots++;
        });
        GunReloadEvent.BUS.addListener((GunReloadEvent event, boolean cancelled) -> {
            if (finished || cancelled || !event.getEntity().getUUID().equals(playerId)) return;
            if (event.getLogicalSide() == LogicalSide.SERVER) serverReloads.incrementAndGet(); else clientReloads++;
        });
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event, boolean cancelled) -> {
            if (finished || cancelled || !(event.getEntity() instanceof EntityKineticBullet)) return;
            if (event.getLevel().isClientSide()) clientProjectiles.incrementAndGet(); else serverProjectiles.incrementAndGet();
        });
        setup = minecraft.getSingleplayerServer().submit(() -> {
            var player = serverPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().setItem(9, AmmoItemBuilder.create().setId(Identifier.parse("tacz:762x39")).setCount(64).build());
            player.containerMenu.broadcastChanges();
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::tick);
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> minecraft.execute(() -> {
            if (!finished) finish(new AssertionError("Gameplay timeout at " + stage + ", server=" + last));
        }));
    }

    private ServerPlayer serverPlayer() {
        var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("Fixture server player disappeared");
        return player;
    }

    private Snapshot snapshot() {
        var player = serverPlayer();
        var stack = player.getMainHandItem();
        var gun = (IGun) stack.getItem();
        return new Snapshot(gun.getCurrentAmmoCount(stack), gun.hasBulletInBarrel(stack),
                player.getInventory().getItem(9).getCount(),
                IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading(),
                player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL);
    }

    private void tick(TickEvent.ClientTickEvent.Post event) {
        if (finished || !setup.isDone()) return;
        try {
            setup.join();
            if (observation == null) { observation = minecraft.getSingleplayerServer().submit(this::snapshot); return; }
            if (!observation.isDone()) return;
            last = observation.join();
            observation = null;
            var clientStack = minecraft.player.getMainHandItem();
            var clientGun = (IGun) clientStack.getItem();
            var operator = IClientPlayerGunOperator.fromLocalPlayer(minecraft.player);
            var common = IGunOperator.fromLivingEntity(minecraft.player);
            boolean clientReloading = common.getSynReloadState().getStateType().isReloading();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stageStart);
            switch (stage) {
                case READY -> {
                    if (!last.survival || minecraft.gameMode.getPlayerMode() != GameType.SURVIVAL
                            || minecraft.player.getInventory().getItem(9).getCount() != 64
                            || common.getSynDrawCoolDown() != 0 || elapsedMs < 1000) return;
                    check(last.magazine == 30 && last.chamber && last.reserve == 64, "initial authoritative ammunition");
                    check(clientGun.getCurrentAmmoCount(clientStack) == 30 && clientGun.hasBulletInBarrel(clientStack), "initial client ammunition");
                    check(common.needCheckAmmo() && common.consumesAmmoOrNot(), "survival ammo checks enabled");
                    var shot = operator.shoot();
                    result.put("shoot_result", shot.toString());
                    check(shot == ShootResult.SUCCESS, "client accepted one shoot action");
                    transition(Stage.SHOT);
                }
                case SHOT -> {
                    if (last.magazine != 29 || clientGun.getCurrentAmmoCount(clientStack) != 29 || elapsedMs < 400) return;
                    check(last.chamber && last.reserve == 64, "one round consumed on server, reserve unchanged");
                    check(clientGun.hasBulletInBarrel(clientStack), "client chamber synchronized after shot");
                    check(serverShots.get() == 1 && clientShots == 1, "exactly one client/server shoot event");
                    check(serverProjectiles.get() == 1, "one actual server projectile spawned");
                    check(clientProjectiles.get() == 1, "one actual client projectile synchronized");
                    result.put("after_shot", last.toString());
                    operator.reload();
                    transition(Stage.RELOAD);
                }
                case RELOAD -> {
                    observedServerReload |= last.reloading;
                    observedClientReload |= clientReloading;
                    if (last.magazine != 30 || clientGun.getCurrentAmmoCount(clientStack) != 30
                            || last.reloading || clientReloading || elapsedMs < 2200) return;
                    check(observedServerReload && observedClientReload, "server reload state synchronized to client");
                    check(last.chamber && last.reserve == 63, "partial reload consumes exactly one reserve round");
                    check(minecraft.player.getInventory().getItem(9).getCount() == 63 && clientGun.hasBulletInBarrel(clientStack), "reload inventory/chamber synchronized");
                    check(serverReloads.get() == 1 && clientReloads == 1, "exactly one client/server reload event");
                    result.put("after_reload", last.toString());
                    transition(Stage.SETTLE);
                }
                case SETTLE -> {
                    if (elapsedMs < 1000) return;
                    check(last.magazine == 30 && last.chamber && last.reserve == 63 && !last.reloading, "ammunition remains stable after reload");
                    check(serverShots.get() == 1 && serverProjectiles.get() == 1 && serverReloads.get() == 1, "no repeated authoritative actions");
                    finish(null);
                }
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private void transition(Stage next) {
        stage = next;
        stageStart = System.nanoTime();
        System.out.println("TACZ_GAMEPLAY_STAGE " + stage);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + "; server=" + last);
        assertions++;
    }

    private void finish(Throwable failure) {
        if (finished) return;
        finished = true;
        result.put("assertions", assertions);
        result.put("gameplay_scope", "one survival AK-47 shot and partial reload through client operator API and real integrated play packets; no remote multiplayer or visual/audio parity claim");
        result.put("gameplay_stage", stage.toString());
        result.put("server_shoot_events", serverShots.get());
        result.put("server_reload_events", serverReloads.get());
        result.put("server_projectiles", serverProjectiles.get());
        result.put("client_projectiles", clientProjectiles.get());
        result.put("status", failure == null ? "passed" : "failed");
        if (failure != null) { result.put("failure", failure.toString()); failure.printStackTrace(); }
        try {
            Files.writeString(Path.of("runtime-fixture-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { throw new IllegalStateException("Cannot record gameplay result", error); }
        System.out.println("TACZ_RUNTIME_RESULT " + result);
        minecraft.stop();
    }
}
