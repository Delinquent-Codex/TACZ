package com.tacz.guns.compat.playeranimator;

import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.io.File;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Client-only boundary to the optional Player Animator adapter. */
public interface PlayerAnimatorIntegration {
    void initialize();
    boolean load(ZipFile zipFile, String zipPath);
    void load(File file);
    void clearAllAnimationCache();
    boolean hasPlayerAnimator3rd(GunDisplayInstance display);
    void stopAllAnimation(AbstractClientPlayer player);
    void stopAllAnimation(AbstractClientPlayer player, int fadeTime);
    void playAnimation(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount);
    void registerReloadListener(Consumer<PreparableReloadListener> register);
}
