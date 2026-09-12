package com.tacz.guns.porting;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.renderer.BeamGeometry;
import com.tacz.guns.client.renderer.MuzzleFlashGeometry;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.renderer.other.LayerGunTransform;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EffectGeometryChecks {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static boolean near(float a, float b) { return Math.abs(a - b) < .00001F; }

    public static void main(String[] args) throws Exception {
        float[][] baseline = {
                {-1,-1,0,0,0}, {-1,1,0,0,1}, {-1,1,-25,1,1}, {-1,-1,-25,1,0},
                {-1,1,0,0,0}, {1,1,0,0,1}, {1,1,-25,1,1}, {-1,1,-25,1,0},
                {1,1,0,0,0}, {1,-1,0,0,1}, {1,-1,-25,1,1}, {1,1,-25,1,0},
                {1,-1,0,0,1}, {-1,-1,0,0,1}, {-1,-1,-25,1,1}, {1,-1,-25,1,0}
        };
        PoseStack pose = new PoseStack();
        VertexCapture capture = new VertexCapture();
        BeamGeometry.emit(-25, 2, capture, pose.last(), 18, 52, 86, true);
        var faded = capture.drain();
        check(faded.vertices().size() == 16, "four original beam faces");
        for (int i = 0; i < 16; i++) {
            var v = faded.vertices().get(i); var expected = baseline[i];
            check(v.x() == expected[0] && v.y() == expected[1] && v.z() == expected[2]
                            && v.u() == expected[3] && v.v() == expected[4]
                            && v.color() == ARGB.color(i % 4 < 2 ? 255 : 0, 18, 52, 86)
                            && v.light() == LightCoordsUtil.FULL_BRIGHT && v.overlay() == OverlayTexture.NO_OVERLAY,
                    "baseline beam position, UV, color, fade and light at vertex " + i);
        }
        BeamGeometry.emit(-25, 2, capture, pose.last(), 18, 52, 86, false);
        check(capture.drain().vertices().stream().allMatch(v -> v.color() == 0xff123456), "disabled fade retains opaque end");
        pose.translate(3, 4, 5); pose.mulPose(Axis.YP.rotationDegrees(90)); pose.scale(.5F, .5F, .5F);
        BeamGeometry.emit(-25, 2, capture, pose.last(), 18, 52, 86, true);
        var transformed = capture.drain();
        for (int i = 0; i < 16; i++) {
            Vector3f expected = pose.last().pose().transformPosition(new Vector3f(baseline[i][0], baseline[i][1], baseline[i][2]));
            var v = transformed.vertices().get(i);
            check(near(v.x(), expected.x) && near(v.y(), expected.y) && near(v.z(), expected.z), "animated beam transform " + i);
        }
        pose.setIdentity(); transformed.render(pose.last(), capture);
        check(capture.drain().equals(transformed), "beam submission survives caller pose reset");
        BeamGeometry.emit(-2, .008F, capture, pose.last(), 255, 0, 0, false);
        var third = capture.drain().vertices();
        check(third.getFirst().x() == -.004F && third.get(2).z() == -2, "default third-person dimensions");

        var laser = TaczRenderTypes.laserBeam(); var pipeline = laser.pipeline();
        check(pipeline.getColorTargetState().blendFunction().orElseThrow().equals(BlendFunction.OVERLAY), "source-alpha additive color and replacement alpha");
        check(pipeline.getDepthStencilState().depthTest() == CompareOp.GREATER_THAN_OR_EQUAL && pipeline.getDepthStencilState().writeDepth(), "target reverse-Z test and original depth write");
        check(!pipeline.isCull() && laser.primitiveTopology() == PrimitiveTopology.QUADS && laser.sortOnUpload(), "double-sided sorted quads");
        check(laser.format() == DefaultVertexFormat.POSITION_TEX_COLOR && laser.outputTarget() == OutputTarget.ITEM_ENTITY_TARGET, "unlit native vertex format and source output target");
        check(TaczRenderTypes.laserBeamEntity().format() == DefaultVertexFormat.ENTITY, "optional accelerated beam shares the entity format");
        TaczRenderTypes.registerPipelines(); TaczRenderTypes.registerPipelines();
        check(RenderPipelines.getStaticPipelines().stream().filter(p -> p == pipeline).count() == 1, "pipeline registered once for native shader reload");
        String shader = Files.readString(Path.of("src/main/resources/assets/tacz/shaders/core/laser.fsh"));
        check(shader.contains("beam.a < 0.1") && shader.contains("texture(Sampler0, texCoord0) * vertexColor")
                && !shader.contains("apply_fog("), "shipped fragment shader retains baseline cutoff, color and no fog; GPU compile is separate");

        var full = MuzzleFlashGeometry.capture(pose.last(), 2, 25, 0, 0, 7);
        check(full.background().vertices().size() == 24 && full.glow().vertices().size() == 24, "both original flash layers, including the five degenerate omitted faces");
        check(full.background().vertices().subList(20, 24).stream().allMatch(v -> near(Math.abs(v.x()), .5F) && near(Math.abs(v.y()), .5F) && near(v.z(), -.03125F)), "baseline full flash square and depth");
        check(full.glow().vertices().subList(20, 24).stream().allMatch(v -> near(Math.abs(v.x()), .25F) && near(Math.abs(v.y() - .05F), .25F) && near(v.z(), -.015625F)), "half-size glow with original offset");
        check(full.background().vertices().stream().allMatch(v -> v.light() == LightCoordsUtil.FULL_BRIGHT && v.overlay() == 7), "flash remains illuminated and preserves overlay");
        var warmup = MuzzleFlashGeometry.capture(pose.last(), 2, 5, 0, 0, 7);
        check(warmup.background().vertices().subList(20, 24).stream().allMatch(v -> near(Math.abs(v.x()), .1F)), "five milliseconds gives one-fifth flash size");
        check(MuzzleFlashGeometry.capture(pose.last(), 2, 50, 0, 0, 7).equals(full), "size plateaus through final visible millisecond");
        check(MuzzleFlashGeometry.TIME_RANGE == 50, "original fifty-millisecond visibility window");
        var rotated = MuzzleFlashGeometry.capture(pose.last(), 2, 25, 90, 0, 7);
        var a = full.background().vertices().get(20); var b = rotated.background().vertices().get(20);
        check(near(b.x(), -a.y()) && near(b.y(), a.x()) && near(b.z(), a.z()), "shot rotation captured for both layers");
        pose.translate(10, 20, 30);
        var anchored = MuzzleFlashGeometry.capture(pose.last(), 2, 25, 0, 0, 7);
        pose.setIdentity(); anchored.background().render(pose.last(), capture);
        check(capture.drain().equals(anchored.background()) && near(anchored.background().vertices().get(20).x(), a.x() + 10), "flash keeps shot anchor after model mutation");

        Vector3f position = new Vector3f(-2, 20, 4), angles = new Vector3f(0, 0, -30), scale = new Vector3f(.6F);
        LayerGunTransform carried = LayerGunTransform.capture(position, angles, scale);
        position.zero(); angles.zero(); scale.zero();
        carried.apply(pose);
        Vector3f origin = pose.last().pose().transformPosition(new Vector3f());
        check(near(origin.x, .125F) && near(origin.y, .25F) && near(origin.z, .25F), "carried-gun baseline position survives changed pack vectors");
        Vector3f tip = pose.last().pose().transformPosition(new Vector3f(1, 0, 0));
        check(near(tip.x, .125F - .6F * (float)Math.cos(Math.PI / 6)) && near(tip.y, .55F) && near(tip.z, .25F), "carried-gun negative XY scale and source Euler rotation order");
        pose.setIdentity();
        LayerGunTransform.capture(new Vector3f(), new Vector3f(90, 0, 0), new Vector3f(1, 2, 3)).apply(pose);
        Vector3f raised = pose.last().pose().transformPosition(new Vector3f(0, 1, 0));
        check(near(raised.x, 0) && near(raised.y, 1.5F) && near(raised.z, 3), "nonuniform carried-gun scale applied after rotation in model coordinates");
        ClassNode layer = readClass("net/minecraft/client/renderer/entity/layers/ItemInHandLayer");
        check(layer.methods.stream().anyMatch(m -> m.name.equals("submit") && m.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V")), "native carried-gun wrapper descriptor");
        check(layer.methods.stream().anyMatch(m -> m.name.equals("submitArmWithItem") && m.desc.equals("(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")), "native arm wrapper descriptor");
        ClassNode armed = readClass("net/minecraft/client/renderer/entity/state/ArmedEntityRenderState");
        check(armed.methods.stream().anyMatch(m -> m.name.equals("extractArmedEntityRenderState") && m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemModelResolver;F)V")), "native carried-gun extraction descriptor");
        ClassNode avatar = readClass("net/minecraft/client/renderer/entity/player/AvatarRenderer");
        var hand = avatar.methods.stream().filter(m -> m.name.equals("renderHand")).findFirst().orElseThrow();
        int submissions = 0;
        for (var instruction : hand.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/renderer/SubmitNodeCollector")
                    && call.name.equals("submitModelPart") && call.desc.equals("(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V")) submissions++;
        }
        check(submissions == 1, "required first-person arm injection exists once in actual target bytecode; FML application still pending");
        var bob = GunHurtBobTweak.capture(5, 10, 80, 500, .05F);
        check(bob.replacesVanilla() && near(bob.yaw(), 4) && near(bob.pitch(), -.13656323F), "source quartic hurt easing and scaled yaw/pitch at inclusive 500 ms boundary");
        check(!GunHurtBobTweak.capture(5, 10, 80, 501, .05F).replacesVanilla(), "ordinary hurt bob resumes after 500 ms");
        pose.setIdentity();
        var finished = GunHurtBobTweak.capture(-.5F, 10, 80, 100, .05F);
        check(finished.apply(pose) && pose.last().pose().equals(new org.joml.Matrix4f()), "finished hurt tick suppresses vanilla bob without moving the camera");
        GunHurtBobTweak.markTimestamp(.9F);
        check(near(bob.yaw(), 4) && near(bob.pitch(), -.13656323F), "later hit cannot change the captured camera frame");
        pose.setIdentity(); bob.apply(pose);
        var expectedBob = new org.joml.Matrix4f().rotateY((float)Math.toRadians(-4))
                .rotateX((float)Math.toRadians(bob.pitch())).rotateY((float)Math.toRadians(4));
        check(pose.last().pose().equals(expectedBob, .00001F), "source yaw-pitch-yaw hurt matrix order");
        ClassNode gameRenderer = readClass("net/minecraft/client/renderer/GameRenderer");
        for (String method : new String[]{"bobHurt", "bobView"}) {
            check(gameRenderer.methods.stream().anyMatch(m -> m.name.equals(method)
                    && m.desc.equals("(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V")), "camera-state " + method + " descriptor");
        }
        check(gameRenderer.methods.stream().anyMatch(m -> m.name.equals("renderItemInHand")
                && m.desc.equals("(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V")), "native hand/world bob scope descriptor");
        ClassNode camera = readClass("net/minecraft/client/Camera");
        check(camera.methods.stream().anyMatch(m -> m.name.equals("extractRenderState")
                && m.desc.equals("(Lnet/minecraft/client/renderer/state/level/CameraRenderState;F)V")), "gun hurt snapshot extraction target");
        System.out.println("Effect geometry checks passed: " + assertions + " assertions (CPU geometry/pipeline descriptions; no GPU or AR runtime)");
    }

    private static ClassNode readClass(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = EffectGeometryChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
}
