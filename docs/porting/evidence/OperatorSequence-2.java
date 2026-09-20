package com.tacz.guns.porting.runtimefixture;

import com.google.gson.JsonObject;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.InputExtraCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded client-tick API driver. No charge, heat, ammo or server state writes. */
final class OperatorSequence {
    private final int pressedTicks;
    private final int releasedTicks;
    private final boolean stopOnOverheat;
    private final List<Map<String, Object>> trace = new ArrayList<>();
    private final Screen inputBarrier = new Screen(Component.literal("TACZ operator fixture")) {
        @Override public boolean isPauseScreen() { return false; }
    };
    private String status = "running";

    OperatorSequence(JsonObject command, Minecraft minecraft) {
        pressedTicks = command.get("pressed_ticks").getAsInt();
        releasedTicks = command.get("released_ticks").getAsInt();
        stopOnOverheat = command.has("stop_on_overheat") && command.get("stop_on_overheat").getAsBoolean();
        if (pressedTicks < 0 || releasedTicks < 0 || pressedTicks + releasedTicks < 1
                || pressedTicks > 1000 || releasedTicks > 1000)
            throw new IllegalArgumentException("Sequence requires 1..2000 bounded ticks");
        if (minecraft.gui.screen() != null)
            throw new IllegalStateException("Cannot replace an existing screen");
        // Use the game's normal screen/input gate to prevent ShootKey from also
        // advancing charge. The world and actual client/server operators tick.
        minecraft.gui.setScreen(inputBarrier);
    }

    void tick(Minecraft minecraft) {
        if (!status.equals("running")) return;
        if (minecraft.player == null || minecraft.gui.screen() != inputBarrier || InputExtraCheck.isInGame()) {
            // A focused native ShootKey tick would also update charge. Fail rather
            // than silently drive the operator twice or claim keyboard coverage.
            finish(minecraft, "failed: disconnected, input barrier replaced or native input active");
            return;
        }
        var stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun gun)) {
            finish(minecraft, "failed: no gun");
            return;
        }
        if (stopOnOverheat && gun.isOverheatLocked(stack)) {
            finish(minecraft, "overheated");
            return;
        }
        var operator = IClientPlayerGunOperator.fromLocalPlayer(minecraft.player);
        var sample = new LinkedHashMap<String, Object>();
        boolean pressed = trace.size() < pressedTicks;
        sample.put("tick", minecraft.player.tickCount);
        sample.put("pressed", pressed);
        sample.put("native_input_suppressed", !InputExtraCheck.isInGame());
        sample.put("before_charge", operator.getChargeProgress());
        boolean ready = operator.chargeShoot(pressed);
        sample.put("ready", ready);
        if (ready) sample.put("shoot_result", operator.shoot().toString());
        sample.put("after_charge", operator.getChargeProgress());
        sample.put("heat", gun.getHeatAmount(stack));
        sample.put("overheated", gun.isOverheatLocked(stack));
        trace.add(sample);
        if (trace.size() >= pressedTicks + releasedTicks) finish(minecraft, "finished");
    }

    private void finish(Minecraft minecraft, String result) {
        status = result;
        if (minecraft.gui.screen() == inputBarrier) minecraft.gui.setScreen(null);
    }

    Map<String, Object> summary() { return Map.of("status", status, "ticks", trace.size()); }
    List<Map<String, Object>> trace() { return List.copyOf(trace); }
}
