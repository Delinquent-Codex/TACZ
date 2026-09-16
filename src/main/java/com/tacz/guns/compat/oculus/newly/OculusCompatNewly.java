package com.tacz.guns.compat.oculus.newly;

import net.irisshaders.iris.shadows.ShadowRenderingState;

public final class OculusCompatNewly {
    public static boolean isRenderShadow() {
        return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }

}
