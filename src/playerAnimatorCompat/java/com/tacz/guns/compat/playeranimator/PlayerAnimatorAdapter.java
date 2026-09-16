package com.tacz.guns.compat.playeranimator;

import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.animation.AnimationDataRegisterFactory;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Preserved optional integration. Its companion API migration remains externally blocked. */
public final class PlayerAnimatorAdapter implements PlayerAnimatorIntegration {
    @Override
    public void initialize() {
        AnimationDataRegisterFactory.registerData();
        MinecraftForge.EVENT_BUS.register(new AnimationManager());
    }

    @Override
    public boolean load(ZipFile zipFile, String zipPath) {
        return PlayerAnimatorLoader.load(zipFile, zipPath);
    }

    @Override
    public void load(File file) {
        PlayerAnimatorLoader.load(file);
    }

    @Override
    public void clearAllAnimationCache() {
        PlayerAnimatorAssetManager.get().clearAll();
    }

    @Override
    public boolean hasPlayerAnimator3rd(GunDisplayInstance display) {
        return AnimationManager.hasPlayerAnimator3rd(display);
    }

    @Override
    public void stopAllAnimation(AbstractClientPlayer player) {
        AnimationManager.stopAllAnimation(player);
    }

    @Override
    public void stopAllAnimation(AbstractClientPlayer player, int fadeTime) {
        AnimationManager.stopAllAnimation(player, fadeTime);
    }

    @Override
    public void playAnimation(AbstractClientPlayer player, GunDisplayInstance display, float limbSwingAmount) {
        AnimationManager.playLowerAnimation(player, display, limbSwingAmount);
        AnimationManager.playLoopUpperAnimation(player, display, limbSwingAmount);
        AnimationManager.playRotationAnimation(player, display);
    }

    @Override
    public void registerReloadListener(Consumer<PreparableReloadListener> register) {
        register.accept(PlayerAnimatorAssetManager.get());
    }
}
