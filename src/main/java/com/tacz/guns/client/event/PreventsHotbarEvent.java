package com.tacz.guns.client.event;

import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class PreventsHotbarEvent {
    public static boolean shouldSuppressOverlays() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof GunSmithTableScreen || screen instanceof GunRefitScreen;
    }
}
