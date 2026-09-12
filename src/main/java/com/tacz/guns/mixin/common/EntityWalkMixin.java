package com.tacz.guns.mixin.common;

import com.llamalad7.mixinextras.sugar.Local;
import com.tacz.guns.api.entity.LegacyWalkDistance;
import com.tacz.guns.api.entity.LegacyWalkProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, remap = false)
public abstract class EntityWalkMixin implements LegacyWalkProvider {
    @Unique private final LegacyWalkDistance tacz$walkDistance = new LegacyWalkDistance();
    @Shadow protected abstract Entity.MovementEmission getMovementEmission();
    @Shadow public abstract boolean isPassenger();

    @Override
    @Unique
    public LegacyWalkDistance tacz$getWalkDistance() { return tacz$walkDistance; }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void tacz$beginWalkTick(CallbackInfo ci) { tacz$walkDistance.beginTick(); }

    // Runs after collision/fall processing, outside the target's local-authority-only sound
    // guard. The old distance field updated for every emitting, non-riding entity on both sides.
    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBlockSpeedFactor()F"))
    private void tacz$recordWalk(MoverType type, Vec3 delta, CallbackInfo ci, @Local(name = "movement") Vec3 movement) {
        if (getMovementEmission().emitsAnything() && !isPassenger()) tacz$walkDistance.move(movement);
    }
}
