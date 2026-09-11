package com.tacz.guns.porting;

import com.tacz.guns.api.entity.LegacyWalkDistance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.util.LanguageFeatures;

public final class WalkDistanceChecks {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        LegacyWalkDistance walk = new LegacyWalkDistance();
        check(walk.delta() == 0 && walk.extrapolate(1) == 0, "new entity has no movement history");
        walk.beginTick(); walk.move(new Vec3(3, 100, 4));
        check(walk.delta() == 3, "source horizontal 3-4-5 distance uses .6 scale and ignores height");
        check(walk.extrapolate(0) == 3 && walk.extrapolate(.5F) == 4.5F && walk.extrapolate(1) == 6,
                "script phase extrapolates from current cumulative distance");
        walk.beginTick();
        check(walk.delta() == 0 && walk.extrapolate(1) == 3, "stationary tick stops extrapolation without losing accumulated distance");
        walk.move(new Vec3(0, -100, 0));
        check(walk.delta() == 0, "vertical climbing/falling does not advance horizontal walk cycle");
        walk.move(new Vec3(-3, 0, -4)); walk.move(new Vec3(3, 0, 4));
        check(walk.delta() == 6 && walk.extrapolate(0) == 9, "multiple resolved moves accumulate distance even if net displacement cancels");
        float anchor = walk.extrapolate(.5F);
        check(walk.extrapolate(.5F) - anchor == 0, "same-frame anchored walk distance is zero");
        walk.beginTick(); walk.move(new Vec3(3, 0, 4));
        check(walk.extrapolate(.5F) - anchor == 1.5F, "anchor retains baseline phase across later ticks");
        LegacyWalkDistance other = new LegacyWalkDistance();
        check(other.delta() == 0 && other.extrapolate(0) == 0, "entity histories are independent");

        ClassNode node = new ClassNode();
        try (var input = Entity.class.getResourceAsStream("Entity.class")) { new ClassReader(input).accept(node, 0); }
        var move = node.methods.stream().filter(m -> m.name.equals("move") &&
                m.desc.equals("(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V")).findFirst().orElseThrow();
        int calls = 0, callIndex = -1;
        for (var instruction : move.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/Entity")
                    && call.name.equals("getBlockSpeedFactor") && call.desc.equals("()F")) {
                calls++; callIndex = move.instructions.indexOf(call);
            }
        }
        check(calls == 1, "required movement injection target occurs exactly once in real target bytecode");
        int targetIndex = callIndex;
        long captured = move.localVariables.stream().filter(local -> local.name.equals("movement")
                && local.desc.equals("Lnet/minecraft/world/phys/Vec3;")
                && move.instructions.indexOf(local.start) <= targetIndex && move.instructions.indexOf(local.end) > targetIndex).count();
        check(captured == 1, "named collision-resolved Vec3 local is live at the target hook");
        check(node.methods.stream().anyMatch(m -> m.name.equals("baseTick") && m.desc.equals("()V")), "required tick snapshot hook exists");
        ClassNode mixin = new ClassNode();
        try (var input = WalkDistanceChecks.class.getResourceAsStream("/com/tacz/guns/mixin/common/EntityWalkMixin.class")) {
            new ClassReader(input).accept(mixin, 0);
        }
        check(mixin.version == 69 && MixinEnvironment.CompatibilityLevel.JAVA_17.supports(LanguageFeatures.scan(mixin)),
                "actual Mixin 0.8.7 feature validator accepts new Java 25-compiled mixin at declared JAVA_17 level");
        System.out.println("Walk-distance checks passed: " + assertions + " assertions (accumulator and target bytecode; no Mixin application or gameplay).");
    }
}
