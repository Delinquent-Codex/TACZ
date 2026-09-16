package com.tacz.guns.porting.runtimefixture;

import com.google.gson.GsonBuilder;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.event.FirstPersonRenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Exercise server inventory sync, real first-person animation and the native hand hook. */
final class HeldGunCheck {
    private static final Identifier GUN = Identifier.parse("tacz:ak47");
    private final Map<String, Object> result;
    private final int priorAssertions;
    private CompletableFuture<Void> equip;
    private int customHandFrames;
    private int settledFrames;
    private boolean finished;

    static void start(Map<String, Object> result, int priorAssertions) {
        new HeldGunCheck(result, priorAssertions).start();
    }

    private HeldGunCheck(Map<String, Object> result, int priorAssertions) {
        this.result = result;
        this.priorAssertions = priorAssertions;
    }

    private void start() {
        Minecraft minecraft = Minecraft.getInstance();
        result.put("status", "in-progress");
        result.put("held_gun", GUN.toString());
        RenderHandEvent.BUS.addListener((RenderHandEvent event, boolean cancelled) -> {
            if (!finished && cancelled && event.getHand() == InteractionHand.MAIN_HAND && holdingExpectedGun()) customHandFrames++;
        });
        TickEvent.RenderTickEvent.Post.BUS.addListener(this::tick);
        var uuid = minecraft.player.getUUID();
        equip = minecraft.getSingleplayerServer().submit(() -> {
            var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            if (player == null) throw new IllegalStateException("Fixture player missing from integrated server");
            var gun = GunItemBuilder.create().setId(GUN).setAmmoCount(30).setAmmoInBarrel(true).setFireMode(FireMode.AUTO).build();
            if (gun.isEmpty()) throw new IllegalStateException("Registered AK-47 builder produced an empty stack");
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), gun);
            player.containerMenu.broadcastChanges();
        });
        CompletableFuture.delayedExecutor(25, TimeUnit.SECONDS).execute(() -> minecraft.execute(() -> {
            if (!finished) finish(new AssertionError("Timed out waiting for actual AK-47 hand rendering"));
        }));
    }

    private boolean holdingExpectedGun() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        var stack = player.getMainHandItem();
        return stack.getItem() instanceof IGun gun && GUN.equals(gun.getGunId(stack));
    }

    private void tick(TickEvent.RenderTickEvent.Post event) {
        if (finished || !equip.isDone()) return;
        try {
            equip.join();
            if (!holdingExpectedGun()) return;
            var animation = FirstPersonRenderHandler.getActiveAnimationInstance();
            if (animation == null || animation.currentItem().isEmpty() || customHandFrames < 60) return;
            if (++settledFrames < 60) return;
            result.put("assertions", priorAssertions + 3);
            finish(null);
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private void finish(Throwable failure) {
        if (finished) return;
        finished = true;
        result.put("custom_hand_frames", customHandFrames);
        result.put("held_gun_scope", "actual inventory sync, active first-person animation and cancelled native hand events; no pixel/visual parity or shooting claim");
        if (failure == null && Boolean.getBoolean("tacz.fixture.gameplay")) {
            GameplayCheck.start(result, priorAssertions + 3);
            return;
        }
        result.put("status", failure == null ? "passed" : "failed");
        if (failure != null) { result.put("failure", failure.toString()); failure.printStackTrace(); }
        try {
            Files.writeString(Path.of("runtime-fixture-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { throw new IllegalStateException("Cannot record held-gun result", error); }
        System.out.println("TACZ_RUNTIME_RESULT " + result);
        Minecraft.getInstance().stop();
    }
}
