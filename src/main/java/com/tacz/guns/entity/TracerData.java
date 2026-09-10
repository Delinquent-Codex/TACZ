package com.tacz.guns.entity;

import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

/** Reads the public persistent-data tracer overrides, retaining the baseline's short-array fallbacks. */
public final class TracerData {
    public static final String COLOR_KEY = "tacz:tracer_override";
    public static final String SIZE_KEY = "tacz:tracer_size";
    private TracerData() {}

    public static Optional<float[]> color(CompoundTag data) {
        return data.getIntArray(COLOR_KEY).flatMap(values -> {
            if (values.length == 0) return Optional.empty();
            float red = values[0] / 255F;
            if (values.length == 1) return Optional.of(new float[]{red, red, red, 1});
            if (values.length == 2) return Optional.of(new float[]{red, red, red, values[1] / 255F});
            return Optional.of(new float[]{red, values[1] / 255F, values[2] / 255F, values.length == 3 ? 1 : values[3] / 255F});
        });
    }

    public static float size(CompoundTag data) { return data.getFloatOr(SIZE_KEY, 1); }
}
