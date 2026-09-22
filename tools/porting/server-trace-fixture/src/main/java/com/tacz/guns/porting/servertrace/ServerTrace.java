package com.tacz.guns.porting.servertrace;

import com.google.gson.Gson;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diagnostic observations only. No result/state/packet replacement or cancellation. */
@Mod("tacz_server_trace")
public final class ServerTrace {
    private static final List<Map<String, Object>> RECORDS = new ArrayList<>();

    public ServerTrace() {
        add(Map.of("kind", "trace_loaded"));
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event, boolean cancelled) -> {
            if (!cancelled && !event.getLevel().isClientSide() && event.getEntity() instanceof EntityKineticBullet bullet)
                projectile("projectile_join", bullet);
        });
        EntityLeaveLevelEvent.BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityKineticBullet bullet)
                projectile("projectile_leave", bullet);
        });
        ServerStoppingEvent.BUS.addListener((ServerStoppingEvent event) -> write());
    }

    public static Map<String, Object> begin(LivingEntity shooter, ShooterDataHolder data, long timestamp, float charge) {
        var row = new LinkedHashMap<String, Object>();
        row.put("kind", "shoot_result");
        row.put("shooter", shooter.getUUID().toString());
        row.put("player_tick", shooter.tickCount);
        row.put("wall_before", System.currentTimeMillis());
        row.put("request_timestamp", timestamp);
        row.put("base_timestamp", data.baseTimestamp);
        row.put("previous_shoot_timestamp", data.shootTimestamp);
        row.put("charge", charge);
        var stack = shooter.getMainHandItem();
        if (stack.getItem() instanceof IGun gun) {
            row.put("gun", gun.getGunId(stack).toString());
            row.put("heat_before", gun.getHeatAmount(stack));
            row.put("locked_before", gun.isOverheatLocked(stack));
        }
        return row;
    }

    public static void end(Map<String, Object> row, Object result) {
        row.put("result", result.toString());
        add(row);
    }

    private static void projectile(String kind, EntityKineticBullet bullet) {
        var row = new LinkedHashMap<String, Object>();
        row.put("kind", kind);
        row.put("uuid", bullet.getUUID().toString());
        row.put("id", bullet.getId());
        row.put("age", bullet.tickCount);
        row.put("gun", String.valueOf(bullet.getGunId()));
        row.put("position", List.of(bullet.getX(), bullet.getY(), bullet.getZ()));
        var motion = bullet.getDeltaMovement();
        row.put("motion", List.of(motion.x, motion.y, motion.z));
        row.put("removal_reason", String.valueOf(bullet.getRemovalReason()));
        add(row);
    }

    private static synchronized void add(Map<String, Object> row) {
        if (RECORDS.size() >= 10000) throw new IllegalStateException("Server trace exceeded bounded scenario capacity");
        var copy = new LinkedHashMap<String, Object>(row);
        copy.put("wall_time", System.currentTimeMillis());
        RECORDS.add(copy);
    }

    private static synchronized void write() {
        try {
            var json = new Gson();
            Files.writeString(Path.of("server-trace.jsonl"), String.join("\n", RECORDS.stream().map(json::toJson).toList()) + "\n");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot preserve server diagnostic trace", failure);
        }
    }
}
