package com.tacz.guns.porting.runtimefixture;

import com.google.gson.JsonObject;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.InputExtraCheck;
import net.minecraft.client.Minecraft;

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
    private String status = "running";

    OperatorSequence(JsonObject command) {
        pressedTicks = command.get("pressed_ticks").getAsInt();
        releasedTicks = command.get("released_ticks").getAsInt();
        stopOnOverheat = command.has("stop_on_overheat") && command.get("stop_on_overheat").getAsBoolean();
        if (pressedTicks < 0 || releasedTicks < 0 || pressedTicks + releasedTicks < 1
                || pressedTicks > 1000 || releasedTicks > 1000)
            throw new IllegalArgumentException("Sequence requires 1..2000 bounded ticks");
    }

    void tick(Minecraft minecraft) {
        if (!status.equals("running")) return;
        if (minecraft.player == null || minecraft.gui.screen() != null || InputExtraCheck.isInGame()) {
            // A focused native ShootKey tick would also update charge. Fail rather
            // than silently drive the operator twice or claim keyboard coverage.
            status = "failed: disconnected, screen open or native input active";
            return;
        }
        var stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun gun)) {
            status = "failed: no gun";
            return;
        }
        if (stopOnOverheat && gun.isOverheatLocked(stack)) {
            status = "overheated";
            return;
        }
        var operator = IClientPlayerGunOperator.fromLocalPlayer(minecraft.player);
        var sample = new LinkedHashMap<String, Object>();
        boolean pressed = trace.size() < pressedTicks;
        sample.put("tick", minecraft.player.tickCount);
        sample.put("pressed", pressed);
        sample.put("before_charge", operator.getChargeProgress());
        boolean ready = operator.chargeShoot(pressed);
        sample.put("ready", ready);
        if (ready) sample.put("shoot_result", operator.shoot().toString());
        sample.put("after_charge", operator.getChargeProgress());
        sample.put("heat", gun.getHeatAmount(stack));
        sample.put("overheated", gun.isOverheatLocked(stack));
        trace.add(sample);
        if (trace.size() >= pressedTicks + releasedTicks) status = "finished";
    }

    Map<String, Object> summary() { return Map.of("status", status, "ticks", trace.size()); }
    List<Map<String, Object>> trace() { return List.copyOf(trace); }
}
