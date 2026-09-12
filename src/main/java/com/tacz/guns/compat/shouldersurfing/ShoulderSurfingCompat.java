package com.tacz.guns.compat.shouldersurfing;

import net.minecraftforge.fml.ModList;

public final class ShoulderSurfingCompat {
    private static final String MOD_ID = "shouldersurfing";
    private static boolean INSTALLED = false;

    public static void init() {
        INSTALLED = ModList.isLoaded(MOD_ID);
    }

    public static boolean showCrosshair() {
        if (INSTALLED) {
            return ShoulderSurfingCompatInner.showCrosshair();
        }
        return false;
    }

    public static boolean isInstalled() {
        return INSTALLED;
    }

    /** Keeps optional companion types out of the core camera event's signatures. */
    public static boolean applyRecoil(float delta, boolean pitch) {
        return INSTALLED && ShoulderSurfingCompatInner.applyRecoil(delta, pitch);
    }
}
