package com.tacz.guns.compat.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.Perspective;
import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.client.InputHandler;

public class ShoulderSurfingCompatInner {
    public static boolean showCrosshair() {
        Perspective current = Perspective.current();
        return current == Perspective.SHOULDER_SURFING && !InputHandler.FREE_LOOK.isDown();
    }

    public static boolean applyRecoil(float delta, boolean pitch) {
        var instance = IShoulderSurfing.getInstance();
        if (!instance.isShoulderSurfing()) return false;
        var camera = instance.getCamera();
        if (pitch) camera.setXRot(camera.getXRot() - delta);
        else camera.setYRot(camera.getYRot() - delta);
        return true;
    }
}
