package com.tacz.guns.compat.oculus.legacy;

import net.coderbot.iris.shadows.ShadowRenderingState;

public final class OculusCompatLegacy {
    public static boolean isRenderShadow() {
        return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }

}
