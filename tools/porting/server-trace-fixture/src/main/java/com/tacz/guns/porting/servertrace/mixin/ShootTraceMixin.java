package com.tacz.guns.porting.servertrace.mixin;

import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.porting.servertrace.ServerTrace;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(value = LivingEntityShoot.class, remap = false)
public abstract class ShootTraceMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;
    @Unique private Map<String, Object> taczTrace;

    @Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;", at = @At("HEAD"))
    private void before(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp, float charge, boolean context,
                        CallbackInfoReturnable<ShootResult> callback) {
        taczTrace = ServerTrace.begin(shooter, data, timestamp, charge);
    }

    @Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;", at = @At("RETURN"))
    private void after(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp, float charge, boolean context,
                       CallbackInfoReturnable<ShootResult> callback) {
        ServerTrace.end(taczTrace, callback.getReturnValue());
    }
}
