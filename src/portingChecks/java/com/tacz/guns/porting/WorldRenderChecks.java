package com.tacz.guns.porting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.RenderSubmission;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.renderer.entity.MinecartRenderPose;
import com.tacz.guns.client.renderer.entity.TracerGeometry;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WorldRenderChecks {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 1E-6; }
    private static boolean near(Vector3f point, float x, float y, float z) {
        return near(point.x, x) && near(point.y, y) && near(point.z, z);
    }

    public static void main(String[] args) throws Exception {
        RegistryFixture.bootstrap();
        tracerChecks();
        railChecks();
        hierarchyChecks();
        int models = modelChecks();
        System.out.println("World rendering checks passed: " + assertions + " assertions; " + models
                + " shipped Bedrock models emitted CPU geometry (no FML, GPU, skin download or live entity).");
    }

    private static void tracerChecks() {
        var near = TracerGeometry.calculate(10, 1, 1, 0, 1);
        check(near(near.length(), .8) && near(near.width(), .005), "near shooter clips length and keeps minimum width");
        check(!near.visible() && near(near.offsetScale(), .98), "new close tracer hidden, muzzle offset retained");
        check(!TracerGeometry.calculate(10, 2, 2, 4, 1).visible(), "distance two and age four remain hidden");
        check(TracerGeometry.calculate(10, 2, 2, 5, 1).visible(), "age five bypasses distance threshold");
        check(TracerGeometry.calculate(10, 2, 2.001, 0, 1).visible(), "visibility uses current shooter eye distance");
        var far = TracerGeometry.calculate(10, 35, 35, 1, 2);
        check(near(far.width(), .1) && near(far.length(), 8.5), "far tracer scales width and uses speed length");
        check(near(far.offsetScale(), .3), "muzzle offset decays linearly");
        check(TracerGeometry.calculate(10, 50, 50, 0, 1).offsetScale() == 0
                && TracerGeometry.calculate(10, 100, 100, 0, 1).offsetScale() == 0, "offset vanishes at and beyond fifty");
        check(TracerGeometry.calculate(0, 0, 0, 5, 1).length() == 0, "stationary centered tracer has no length");
    }

    private static void railChecks() {
        var state = new MinecartRenderState();
        PoseStack pose = new PoseStack();
        MinecartRenderPose.apply(state, pose);
        check(near(pose.last().pose().transformPosition(new Vector3f()), -.00175F, .37325F, -.00175F),
                "old minecart origin retains source jitter and vertical offset");
        check(near(pose.last().pose().transformDirection(new Vector3f(1, 0, 0)), -1, 0, 0), "old default yaw faces back");
        pose.setIdentity();
        state.x = 10; state.y = 20; state.z = 30;
        state.posOnRail = new Vec3(11, 20, 30);
        state.frontPos = new Vec3(10, 20, 30); state.backPos = new Vec3(12, 20, 30);
        MinecartRenderPose.apply(state, pose);
        check(near(pose.last().pose().transformPosition(new Vector3f()), .99825F, .37325F, -.00175F), "rail center offset relative to entity");
        state.isNewRender = true; state.yRot = 90; state.xRot = 0;
        pose.setIdentity(); MinecartRenderPose.apply(state, pose);
        check(near(pose.last().pose().transformDirection(new Vector3f(1, 0, 0)), 0, 0, -1), "new minecart uses native direct yaw");
        check(near(pose.last().pose().transformPosition(new Vector3f()), -.00175F, .37325F, -.00175F), "new behavior ignores old rail samples");
        pose.setIdentity(); state.displayOffset = 8; MinecartRenderPose.contents(state, pose);
        check(near(pose.last().pose().transformPosition(new Vector3f()), -.375F, 0, .375F), "source content translation and .75 scale");
        check(near(pose.last().pose().transformDirection(new Vector3f(1, 0, 0)), 0, 0, -.75F), "source content rotates ninety degrees");
        pose.setIdentity(); state.displayOffset = 24; MinecartRenderPose.contents(state, pose);
        check(near(pose.last().pose().transformPosition(new Vector3f()).y, .75), "custom display offset retained");
        state.isNewRender = false; state.posOnRail = null; state.yRot = 180;
        state.hurtTime = (float) (Math.PI / 2); state.damageTime = 10; state.hurtDir = 1;
        pose.setIdentity(); MinecartRenderPose.apply(state, pose);
        check(pose.last().pose().transformDirection(new Vector3f(0, 1, 0)).z > 0, "hurt animation rotates in damage direction");
        state.hurtDir = -1; pose.setIdentity(); MinecartRenderPose.apply(state, pose);
        check(pose.last().pose().transformDirection(new Vector3f(0, 1, 0)).z < 0, "opposite hurt direction reverses tilt");
    }

    private static void hierarchyChecks() {
        PoseStack pose = new PoseStack();
        BedrockPart root = new BedrockPart("root"), child = new BedrockPart("child");
        root.setPos(16, 0, 0); child.setPos(0, 16, 0);
        child.cubes.add(new BedrockCubeBox(0, 0, 0, 0, 0, 16, 16, 16, 0, false, 64, 64));
        root.children.add(child);
        VertexCapture capture = new VertexCapture();
        root.render(pose, ItemDisplayContext.NONE, capture, 0, 0);
        var vertices = capture.drain().vertices();
        check(near(vertices.getFirst().x(), 2) && near(vertices.getFirst().y(), 1), "child geometry inherits both bone pivots");
        check(pose.last().pose().equals(new Matrix4f()), "bone traversal restores caller pose");
        root.illuminated = true; root.render(pose, ItemDisplayContext.NONE, capture, 0, 0);
        check(capture.drain().vertices().stream().allMatch(v -> v.light() == LightCoordsUtil.FULL_BRIGHT), "illuminated parent lights descendants");
        root.visible = false; root.render(pose, ItemDisplayContext.NONE, capture, 0, 0);
        check(capture.drain().vertices().isEmpty(), "hidden parent suppresses child mesh");
        int[] callbacks = {0};
        FunctionalBedrockPart special = new FunctionalBedrockPart(part -> (p, v, c, l, o) -> callbacks[0]++, "special");
        special.visible = false; special.render(pose, ItemDisplayContext.NONE, capture, 0, 0);
        check(callbacks[0] == 1, "hidden functional bone still invokes its renderer");
        root.visible = true;
        special.functionalRenderer = part -> (p, v, c, l, o) -> { throw new IllegalStateException("fixture"); };
        root.children.add(special);
        boolean failed = false;
        try { root.render(pose, ItemDisplayContext.NONE, capture, 0, 0); } catch (IllegalStateException expected) { failed = true; }
        check(failed && pose.last().pose().equals(new Matrix4f()), "nested callback failure restores all owned poses");
        class FixtureModel extends BedrockModel { FixtureModel() { super(); } }
        FixtureModel model = new FixtureModel();
        model.delegateRender((p, v, c, l, o) -> { callbacks[0]++; throw new IllegalStateException("fixture"); });
        try (var scope = RenderSubmission.enter(collector(new ArrayList<>()))) {
            try { model.render(pose, ItemDisplayContext.NONE, null, 0, 0); } catch (IllegalStateException expected) { }
            model.render(pose, ItemDisplayContext.NONE, null, 0, 0);
        }
        check(callbacks[0] == 2, "failed delegated work cannot replay on the next model render");
    }

    private static int modelChecks() throws Exception {
        Gson gson = new GsonBuilder().registerTypeAdapter(CubesItem.class, new CubesItem.Deserializer()).create();
        Path assets = Path.of("src/main/resources/assets/tacz");
        List<Path> models;
        try (var paths = Files.walk(assets)) {
            models = paths.filter(p -> p.toString().endsWith(".json") &&
                    (p.toString().replace('\\', '/').contains("/geo_models/") || p.startsWith(assets.resolve("models/bedrock")))).sorted().toList();
        }
        check(models.size() == 269, "264 default pack models plus five internal Bedrock models accounted for");
        for (Path path : models) {
            BedrockModelPOJO pojo;
            try (var reader = Files.newBufferedReader(path)) { pojo = gson.fromJson(reader, BedrockModelPOJO.class); }
            BedrockVersion version = BedrockVersion.isLegacyVersion(pojo) ? BedrockVersion.LEGACY : BedrockVersion.NEW;
            var bones = version == BedrockVersion.NEW ? pojo.getGeometryModelNew().getBones() : pojo.getGeometryModelLegacy().getBones();
            int cubeCount = bones.stream().mapToInt(b -> b.getCubes() == null ? 0 : b.getCubes().size()).sum();
            BedrockModel model = new BedrockModel(pojo, version);
            List<VertexCapture.Snapshot> output = new ArrayList<>();
            try (var scope = RenderSubmission.enter(collector(output))) {
                model.render(new PoseStack(), ItemDisplayContext.NONE, null, 0, 0);
            }
            var vertices = output.stream().flatMap(batch -> batch.vertices().stream()).toList();
            check(vertices.size() == cubeCount * 24 && vertices.stream().allMatch(v ->
                            Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z())
                            && Float.isFinite(v.nx()) && Float.isFinite(v.ny()) && Float.isFinite(v.nz())
                            && Float.isFinite(v.u()) && Float.isFinite(v.v())),
                    "complete finite CPU geometry for " + path);
        }
        return models.size();
    }

    private static SubmitNodeCollector collector(List<VertexCapture.Snapshot> output) {
        return (SubmitNodeCollector) Proxy.newProxyInstance(WorldRenderChecks.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("submitCustomGeometry")) throw new AssertionError(method);
                    output.add((VertexCapture.Snapshot) args[2]);
                    return null;
                });
    }
}
