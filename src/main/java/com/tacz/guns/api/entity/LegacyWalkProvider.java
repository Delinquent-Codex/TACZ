package com.tacz.guns.api.entity;

import net.minecraft.world.entity.Entity;

/** Movement history supplied on Entity by the required port mixin. */
public interface LegacyWalkProvider {
    LegacyWalkDistance tacz$getWalkDistance();

    static LegacyWalkDistance of(Entity entity) { return ((LegacyWalkProvider) entity).tacz$getWalkDistance(); }
}
