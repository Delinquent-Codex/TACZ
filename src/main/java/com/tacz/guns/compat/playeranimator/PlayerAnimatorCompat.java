package com.tacz.guns.compat.playeranimator;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.OptionalIntegration;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import java.io.File;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

public class PlayerAnimatorCompat {
    public static Identifier LOWER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "lower_animation");
    public static Identifier LOOP_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "loop_upper_animation");
    public static Identifier ONCE_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "once_upper_animation");
    public static Identifier ROTATION_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rotation");

    private static final String MOD_ID = "playeranimator";
    private static PlayerAnimatorIntegration integration;

    public static void init() {
        PlayerAnimatorIntegration loaded = OptionalIntegration.load(MOD_ID, ModList.isLoaded(MOD_ID),
                "com.tacz.guns.compat.playeranimator.PlayerAnimatorAdapter", PlayerAnimatorIntegration.class);
        if (loaded != null) {
            loaded.initialize();
        }
        integration = loaded;
    }

    public static boolean loadAnimationFromZip(ZipFile zipFile, String zipPath) {
        if (isInstalled()) {
            return integration.load(zipFile, zipPath);
        }
        return false;
    }

    public static void loadAnimationFromFile(File file) {
        if (isInstalled()) {
            integration.load(file);
        }
    }

    public static void clearAllAnimationCache() {
        if (isInstalled()) {
            integration.clearAllAnimationCache();
        }
    }

    public static boolean hasPlayerAnimator3rd(LivingEntity livingEntity, GunDisplayInstance display) {
        if (isInstalled() && livingEntity instanceof AbstractClientPlayer) {
            return integration.hasPlayerAnimator3rd(display);
        }
        return false;
    }

    public static void stopAllAnimation(LivingEntity livingEntity) {
        if (isInstalled() && livingEntity instanceof AbstractClientPlayer player) {
            integration.stopAllAnimation(player);
        }
    }

    public static void stopAllAnimation(LivingEntity livingEntity, int fadeTime) {
        if (isInstalled() && livingEntity instanceof AbstractClientPlayer player) {
            integration.stopAllAnimation(player, fadeTime);
        }
    }

    public static void playAnimation(LivingEntity livingEntity, GunDisplayInstance display, float limbSwingAmount) {
        if (isInstalled() && livingEntity instanceof AbstractClientPlayer player) {
            integration.playAnimation(player, display, limbSwingAmount);
        }
    }

    public static boolean isInstalled() {
        return integration != null;
    }

    public static void registerReloadListener(Consumer<PreparableReloadListener> register) {
        if (isInstalled()) {
            integration.registerReloadListener(register);
        }
    }
}
