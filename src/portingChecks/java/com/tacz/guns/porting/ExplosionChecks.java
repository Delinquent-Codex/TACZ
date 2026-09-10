package com.tacz.guns.porting;

import com.tacz.guns.util.block.ExplosionExposure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ServerExplosion;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;

public final class ExplosionChecks {
    private static int assertions;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        RegistryFixture.bootstrap();
        check(Modifier.isProtected(ServerExplosion.class.getDeclaredMethod("interactWithBlocks", List.class).getModifiers()),
                "target block finalizer access transformer is effective on test classpath");
        check(Modifier.isProtected(ServerExplosion.class.getDeclaredMethod("createFire", List.class).getModifiers()),
                "target fire finalizer access transformer is effective on test classpath");
        AABB bounds = new AABB(2, -1, -1, 4, 1, 1);
        Vec3 origin = Vec3.ZERO;
        Vec3[] samples = ExplosionExposure.samples(bounds);
        check(samples.length == 15 && new HashSet<>(Arrays.asList(samples)).size() == 15,
                "eight corners, six face centers and center remain distinct");
        check(Arrays.stream(samples).filter(p -> Math.abs(p.y) == 1 && Math.abs(p.z) == 1).count() == 8,
                "all eight corners retained");
        check(samples[14].equals(new Vec3(3, 0, 0)), "center sample retained");
        check(ExplosionExposure.livingStrength(origin, bounds, 4, p -> true) == .5,
                "nearest face sets falloff rather than center or averaged exposure");
        check(ExplosionExposure.livingStrength(origin, bounds, 4, p -> false) == 2,
                "fully blocked target is outside damage range");
        check(ExplosionExposure.livingStrength(origin, bounds, 4, p -> p.x == 4) == 1,
                "only visible far face reaches zero-damage boundary");
        check(ExplosionExposure.livingStrength(origin, bounds, 4, p -> p.equals(bounds.getCenter())) == .75,
                "one visible center sample determines exposure");
        check(ExplosionExposure.livingStrength(origin, bounds, 4, p -> p.equals(new Vec3(2, 1, 1))) == Math.sqrt(6) / 4,
                "partial cover with one visible corner retains radial distance");
        check(ExplosionExposure.livingStrength(origin, bounds, 1, p -> true) > 1,
                "visible target beyond radius takes no damage");
        check(ExplosionExposure.livingStrength(bounds.getCenter(), bounds, 4, p -> true) == 0,
                "explosion at entity center has full damage");
        for (float radius : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            check(ExplosionExposure.livingStrength(origin, bounds, radius, p -> { throw new AssertionError("invalid-radius ray"); })
                            == Double.POSITIVE_INFINITY && ExplosionExposure.distanceStrength(0, radius) == Double.POSITIVE_INFINITY,
                    "invalid radius cannot produce NaN entity damage: " + radius);
        }
        check(ExplosionExposure.distanceStrength(2, 4) == .5 && ExplosionExposure.distanceStrength(4, 4) == 1,
                "nonliving falloff keeps original radius, not vanilla double radius");
        Vec3 offset = new Vec3(25, -64, 17);
        check(ExplosionExposure.livingStrength(origin.add(offset), bounds.move(offset), 4, p -> true) == .5,
                "exposure invariant under world translation");
        System.out.println("Explosion checks passed: " + assertions + " assertions (geometry and target access; no live damage, blocks or effects).");
    }
}
