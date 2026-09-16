package com.tacz.guns.compat.kubejs;

import net.minecraftforge.eventbus.api.bus.BusGroup;

/** TACZ-owned lifecycle boundary; it contains no KubeJS or Rhino class signatures. */
public interface KubeJSIntegration {
    void install(BusGroup modBus);
}
