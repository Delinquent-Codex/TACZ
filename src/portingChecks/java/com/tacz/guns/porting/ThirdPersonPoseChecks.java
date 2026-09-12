package com.tacz.guns.porting;

import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.client.animation.third.HumanoidGunPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;

public final class ThirdPersonPoseChecks {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static boolean near(float a, float b) { return Math.abs(a - b) < .00001F; }

    public static void main(String[] args) throws Exception {
        var root = LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64).bakeRoot();
        HumanoidModel<HumanoidRenderState> model = new HumanoidModel<>(root);
        var state = new HumanoidRenderState();
        state.ageInTicks = 10;
        state.yRot = 20;
        state.xRot = 40;
        state.walkAnimationPos = 3;
        state.walkAnimationSpeed = .6F;
        ThirdPersonManager.registerDefault();
        var defaults = ThirdPersonManager.getAnimation("default");
        model.rightArm.x = 13;
        model.head.visible = false;
        PartPose oldRight = model.rightArm.storePose();
        var hold = HumanoidGunPose.capture(model, state, () -> defaults.animateGunHold(null, model.rightArm, model.leftArm, model.body, model.head));
        check(model.rightArm.storePose().equals(oldRight) && !model.head.visible, "capture restores original shared pose and visibility");
        check(hold.changes().size() == 2, "default hold changes only arms");
        model.setupAnim(state);
        float originalZ = model.rightArm.zRot;
        float originalLeg = model.rightLeg.xRot;
        hold.apply(model);
        check(near(model.rightArm.yRot, -.3F + (float)Math.toRadians(20)) && near(model.leftArm.yRot, .8F + (float)Math.toRadians(20)), "source hold yaw");
        check(near(model.rightArm.xRot, -1.4F + (float)Math.toRadians(40)) && model.leftArm.xRot == model.rightArm.xRot, "source hold pitch");
        check(model.rightArm.zRot == originalZ && model.rightLeg.xRot == originalLeg, "unchanged bob and leg pose retained");
        var aim = HumanoidGunPose.capture(model, state, () -> defaults.animateGunAim(null, model.rightArm, model.leftArm, model.body, model.head, .5F));
        model.setupAnim(state); aim.apply(model);
        check(near(model.rightArm.yRot, -.325F + (float)Math.toRadians(20)) && near(model.rightArm.xRot, -1.5F + (float)Math.toRadians(40)), "source half-aim interpolation");
        model.rightArm.yRot = 3; model.setupAnim(state); hold.apply(model);
        check(near(model.rightArm.yRot, -.3F + (float)Math.toRadians(20)), "captured hold remains independent of later animation calls");

        var minigun = ThirdPersonManager.getAnimation("minigun");
        var mini = HumanoidGunPose.capture(model, state, () -> minigun.animateGunHold(null, model.rightArm, model.leftArm, model.body, model.head));
        model.setupAnim(state);
        float rightX = model.rightArm.x, leftX = model.leftArm.x;
        mini.apply(model);
        float bodyYaw = (float)Math.toRadians(20) + .8F;
        check(near(model.body.yRot, bodyYaw), "source minigun torso yaw");
        check(near(model.rightArm.x, (float)(rightX * Math.cos(-bodyYaw))) && near(model.rightArm.z, (float)(rightX * Math.sin(-bodyYaw))), "source minigun right pivot");
        check(near(model.leftArm.x, (float)(leftX * Math.cos(-bodyYaw))) && near(model.leftArm.z, (float)(leftX * Math.sin(-bodyYaw))), "source minigun left pivot");
        var miniAim = HumanoidGunPose.capture(model, state, () -> minigun.animateGunAim(null, model.rightArm, model.leftArm, model.body, model.head, 1));
        check(miniAim.equals(mini), "minigun aim retains its hold pose");

        ModelPart sleeve = new ModelPart(List.of(), Map.of());
        model.rightArm.children.put("porting_sleeve", sleeve);
        var custom = HumanoidGunPose.capture(model, state, () -> {
            model.rightArm.xScale = .4F;
            sleeve.yRot = 1.2F;
            sleeve.visible = false;
            sleeve.skipDraw = true;
        });
        check(sleeve.yRot == 0 && sleeve.visible && !sleeve.skipDraw, "child changes restored after extraction");
        model.setupAnim(state); custom.apply(model);
        check(model.rightArm.xScale == .4F && sleeve.yRot == 1.2F && !sleeve.visible && sleeve.skipDraw, "custom child pose, scale and visibility replayed");
        PartPose beforeFailure = model.rightArm.storePose();
        boolean failed = false;
        try {
            HumanoidGunPose.capture(model, state, () -> { model.rightArm.x = 1000; sleeve.visible = true; throw new IllegalStateException("fixture"); });
        } catch (IllegalStateException expected) { failed = true; }
        check(failed && model.rightArm.storePose().equals(beforeFailure) && model.rightArm.xScale == .4F && !sleeve.visible, "callback failure restores parent and child transforms including scale");
        var other = new HumanoidModel<HumanoidRenderState>(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(.5F), 0), 64, 32).bakeRoot());
        other.setupAnim(state); other.rightArm.zRot = .77F;
        hold.apply(other);
        check(other.rightArm.zRot == .77F && near(other.rightArm.yRot, -.3F + (float)Math.toRadians(20)), "standard armor model receives only callback-modified values");
        custom.apply(other);
        check(other.rightArm.xScale == .4F, "model lacking a custom child still receives its matching parent patch");
        boolean immutable = false;
        try { hold.changes().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "pose changes immutable after extraction");

        ClassNode renderer = readClass("net/minecraft/client/renderer/entity/EntityRenderer");
        check(renderer.methods.stream().anyMatch(m -> m.name.equals("createRenderState") && m.desc.equals("(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;")), "final entity extraction hook descriptor");
        ClassNode humanoid = readClass("net/minecraft/client/model/HumanoidModel");
        check(humanoid.methods.stream().anyMatch(m -> m.name.equals("setupAnim") && m.desc.equals("(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V")), "native humanoid pose hook descriptor");
        System.out.println("Third-person pose checks passed: " + assertions + " assertions (real model/builtin callbacks; no live entity, FML or Player Animator)");
    }

    private static ClassNode readClass(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = ThirdPersonPoseChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
}
