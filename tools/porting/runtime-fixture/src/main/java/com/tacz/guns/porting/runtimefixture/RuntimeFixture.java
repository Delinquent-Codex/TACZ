package com.tacz.guns.porting.runtimefixture;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Adds test orchestration alongside the unmodified, complete candidate JAR. */
@Mod("tacz_runtime_fixture")
public final class RuntimeFixture {
    private String worldId;
    private boolean ran;
    private int loadedFrames;
    private int assertions;
    private String previousState;
    private long lastProgress;

    public RuntimeFixture() {
        if (!System.getProperty("tacz.fixture.remote", "").isEmpty()) {
            RemoteCheck.start();
            return;
        }
        TickEvent.RenderTickEvent.Post.BUS.addListener(this::tick);
        // FML can shut down the event bus after a loading failure. Capture that
        // failure through the normal client executor instead of hanging the test.
        java.util.concurrent.CompletableFuture.delayedExecutor(60, java.util.concurrent.TimeUnit.SECONDS).execute(() ->
                Minecraft.getInstance().execute(() -> {
                    if (ran) return;
                    ran = true;
                    var result = new LinkedHashMap<String, Object>();
                    result.put("status", "failed");
                    result.put("assertions", assertions);
                    result.put("failure", "Full client did not reach the world checks within 60 seconds");
                    result.put("screen", String.valueOf(Minecraft.getInstance().gui.screen()));
                    result.put("overlay", String.valueOf(Minecraft.getInstance().gui.overlay()));
                    result.put("forge_warnings", net.minecraftforge.fml.ModLoader.getWarnings().stream()
                            .map(warning -> warning.formatToString()).toList());
                    try {
                        var errorField = net.minecraftforge.client.loading.ClientModLoader.class.getDeclaredField("error");
                        errorField.setAccessible(true);
                        var error = (Throwable) errorField.get(null);
                        if (error != null) { result.put("forge_failure", error.toString()); error.printStackTrace(); }
                        Files.writeString(Path.of("runtime-fixture-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    } catch (Exception error) { error.printStackTrace(); }
                    System.out.println("TACZ_RUNTIME_RESULT " + result);
                    Minecraft.getInstance().stop();
                }));
    }

    private void tick(TickEvent.RenderTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        String state = "screen=" + minecraft.gui.screen() + ", overlay=" + minecraft.gui.overlay();
        if (!state.equals(previousState)) { System.out.println("TACZ_RUNTIME_WAIT " + state); previousState = state; }
        if (minecraft.gui.overlay() instanceof net.minecraft.client.gui.screens.LoadingOverlay overlay
                && System.nanoTime() - lastProgress > 10_000_000_000L) {
            lastProgress = System.nanoTime();
            try {
                var field = net.minecraft.client.gui.screens.LoadingOverlay.class.getDeclaredField("reload");
                field.setAccessible(true);
                var reload = (net.minecraft.server.packs.resources.ReloadInstance) field.get(overlay);
                System.out.println("TACZ_RUNTIME_RELOAD progress=" + reload.getActualProgress() + " done=" + reload.isDone()
                        + " forgeLoading=" + net.minecraftforge.client.loading.ClientModLoader.isLoading());
                if (reload.isDone()) reload.checkExceptions();
            } catch (Throwable failure) { failure.printStackTrace(); }
        }
        if (ran || minecraft.gui.overlay() != null) return;
        if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        if (worldId == null) {
            if (!(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            worldId = "tacz-runtime-" + java.util.UUID.randomUUID();
            minecraft.createWorldOpenFlows().createFreshLevel(worldId,
                    new LevelSettings("TACZ runtime fixture", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT,
                            true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(262L, false, false), WorldPresets::createTestWorldDimensions, minecraft.gui.screen());
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null || ++loadedFrames < 60) return;
        ran = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("fixture", "complete packaged TACZ; see held_gun_scope and gameplay_scope for selected coverage");
        result.put("launch_environment", System.getProperty("tacz.fixture.environment", "Forge development client"));
        result.put("world", worldId);
        result.put("device", RenderSystem.getDevice().getDeviceInfo().toString());
        result.put("backend", RenderSystem.getDevice().getDeviceInfo().backendName());
        result.put("mods", ModList.getMods().stream().map(mod -> mod.getModId() + "@" + mod.getVersion()).toList());
        try {
            check(ModList.isLoaded("tacz"), "full mod is loaded");
            check(TimelessAPI.getAllClientGunIndex().size() == 54, "54 client gun indices");
            check(TimelessAPI.getAllClientAmmoIndex().size() == 24, "24 client ammo indices");
            check(TimelessAPI.getAllClientAttachmentIndex().size() == 99, "99 client attachment indices");
            check(TimelessAPI.getAllCommonGunIndex().size() == 54, "54 synchronized common gun indices");
            check(TimelessAPI.getAllCommonAmmoIndex().size() == 24, "24 synchronized common ammo indices");
            check(TimelessAPI.getAllCommonAttachmentIndex().size() == 99, "99 synchronized common attachment indices");
            check(TimelessAPI.getAllCommonBlockIndex().size() == 3, "three synchronized common block indices");
            check(TabConfig.DEFAULT_TABS.size() == 15, "15 default tabs");
            for (var tab : TabConfig.DEFAULT_TABS) check(!tab.icon().isEmpty(), "default icon " + tab.id());
            for (var block : TimelessAPI.getAllCommonBlockIndex()) {
                for (var tab : block.getValue().getData().getTabs()) check(!tab.icon().isEmpty(), "pack icon " + tab.id());
            }
            var painting = com.tacz.guns.client.resource.ClientRecipeCache.get(Identifier.parse("tacz:misc/blood_strike_1"));
            check(painting != null, "custom painting recipe survives world load and sync");
            check(painting.getOutput().is(net.minecraft.world.item.Items.PAINTING), "resolved custom painting output");
            check(com.tacz.guns.client.resource.ClientRecipeCache.all().size() == 173, "all 173 gunsmith recipes synchronized");
            result.put("status", "passed");
        } catch (Throwable failure) {
            result.put("status", "failed");
            result.put("failure", failure.toString());
            failure.printStackTrace();
        } finally {
            result.put("assertions", assertions);
            if ("passed".equals(result.get("status")) && (Boolean.getBoolean("tacz.fixture.holdGun") || Boolean.getBoolean("tacz.fixture.gameplay"))) {
                HeldGunCheck.start(result, assertions);
            } else {
                try {
                    Files.writeString(Path.of("runtime-fixture-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
                } catch (Exception failure) { throw new IllegalStateException("Cannot record full TACZ result", failure); }
                System.out.println("TACZ_RUNTIME_RESULT " + result);
                minecraft.stop();
            }
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
