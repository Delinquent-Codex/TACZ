package com.tacz.guns.client.animation.third;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only values changed by the gun callback are replayed over the target's native animated pose. */
public record HumanoidGunPose(Map<String, Change> changes) {
    public static final HumanoidGunPose EMPTY = new HumanoidGunPose(Map.of());
    public HumanoidGunPose { changes = Map.copyOf(changes); }

    public record PartState(PartPose pose, boolean visible, boolean skipDraw) {
        static PartState capture(ModelPart part) {
            // ModelPart.storePose() records only position/rotation, losing live scale.
            return new PartState(new PartPose(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale), part.visible, part.skipDraw);
        }
        void restore(ModelPart part) { part.loadPose(pose); part.visible = visible; part.skipDraw = skipDraw; }
    }

    public record Change(PartState before, PartState after) {
        public void apply(ModelPart part) {
            PartPose a = before.pose, b = after.pose;
            if (a.x() != b.x()) part.x = b.x();
            if (a.y() != b.y()) part.y = b.y();
            if (a.z() != b.z()) part.z = b.z();
            if (a.xRot() != b.xRot()) part.xRot = b.xRot();
            if (a.yRot() != b.yRot()) part.yRot = b.yRot();
            if (a.zRot() != b.zRot()) part.zRot = b.zRot();
            if (a.xScale() != b.xScale()) part.xScale = b.xScale();
            if (a.yScale() != b.yScale()) part.yScale = b.yScale();
            if (a.zScale() != b.zScale()) part.zScale = b.zScale();
            if (before.visible != after.visible) part.visible = after.visible;
            if (before.skipDraw != after.skipDraw) part.skipDraw = after.skipDraw;
        }
    }

    public static <S extends HumanoidRenderState> HumanoidGunPose capture(HumanoidModel<S> model, S state, Runnable callback) {
        // Model.allParts() is cached at construction; callbacks may receive a model
        // whose existing children were extended later by another renderer.
        Map<String, ModelPart> currentParts = new LinkedHashMap<>();
        visit(currentParts, "root", model.root());
        List<ModelPart> allParts = List.copyOf(currentParts.values());
        List<PartState> saved = allParts.stream().map(PartState::capture).toList();
        try {
            model.setupAnim(state);
            Map<String, ModelPart> bones = bones(model);
            Map<String, PartState> before = new LinkedHashMap<>();
            bones.forEach((name, part) -> before.put(name, PartState.capture(part)));
            callback.run();
            Map<String, Change> result = new LinkedHashMap<>();
            bones.forEach((name, part) -> {
                PartState after = PartState.capture(part);
                if (!before.get(name).equals(after)) result.put(name, new Change(before.get(name), after));
            });
            return new HumanoidGunPose(result);
        } finally {
            for (int i = 0; i < allParts.size(); i++) saved.get(i).restore(allParts.get(i));
        }
    }

    public void apply(HumanoidModel<?> model) {
        if (changes.isEmpty()) return;
        Map<String, ModelPart> bones = bones(model);
        changes.forEach((name, change) -> {
            ModelPart part = bones.get(name);
            if (part != null) change.apply(part);
        });
    }

    private static Map<String, ModelPart> bones(HumanoidModel<?> model) {
        Map<String, ModelPart> result = new LinkedHashMap<>();
        visit(result, "head", model.head);
        visit(result, "body", model.body);
        visit(result, "right_arm", model.rightArm);
        visit(result, "left_arm", model.leftArm);
        return result;
    }

    private static void visit(Map<String, ModelPart> output, String path, ModelPart part) {
        output.put(path, part);
        part.children.forEach((name, child) -> visit(output, path + "/" + name, child));
    }
}
