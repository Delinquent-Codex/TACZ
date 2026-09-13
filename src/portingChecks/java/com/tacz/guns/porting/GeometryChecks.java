package com.tacz.guns.porting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.renderer.RenderSubmission;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.renderer.TexturedBlit;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import net.minecraft.resources.Identifier;
import com.tacz.guns.client.resource.pojo.model.FaceUVsItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.util.List;

public final class GeometryChecks {
    private static int assertions;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var cube = new BedrockCubeBox(0, 0, 0, 0, 0, 16, 16, 16, 0, false, 64, 64);
        PoseStack pose = new PoseStack();
        VertexCapture capture = new VertexCapture();
        cube.compile(pose.last(), capture, 0x00f00070, 0x00100020, .5F, 1F, 0F, .25F);
        var snapshot = capture.drain();
        var vertices = snapshot.vertices();
        check(vertices.size() == 24, "six quads retain 24 vertices");
        check(vertices.stream().map(v -> List.of(v.x(), v.y(), v.z())).distinct().count() == 8, "cube retains eight unique corners");
        var first = vertices.getFirst();
        check(first.x() == 1 && first.y() == 0 && first.z() == 1, "baseline first east-face corner and 1/16 unit scale");
        check(first.u() == .75F && first.v() == .25F, "baseline box UV atlas coordinates");
        check(first.nx() == 1 && first.ny() == 0 && first.nz() == 0, "baseline east-facing normal");
        check(vertices.stream().allMatch(v -> v.color() == ARGB.color(63, 127, 255, 0)
                        && v.light() == 0x00f00070 && v.overlay() == 0x00100020),
                "color truncation, alpha and packed light/overlay retained for every vertex");
        check(vertices.stream().map(v -> List.of(v.nx(), v.ny(), v.nz())).distinct().count() == 6, "all six normal directions emitted");
        pose.translate(10, 20, 30);
        cube.compile(pose.last(), capture, 0, 0, 1, 1, 1, 1);
        var translated = capture.drain();
        check(translated.vertices().getFirst().x() == 11 && translated.vertices().getFirst().y() == 20
                        && translated.vertices().getFirst().z() == 31, "model translation applied at capture time");
        pose.setIdentity();
        check(translated.vertices().getFirst().x() == 11 && vertices.getFirst().x() == 1,
                "pose reset and capture reuse cannot modify submitted geometry");
        boolean immutable = false;
        try { vertices.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable && capture.drain().vertices().isEmpty(), "snapshots immutable and drained capture empty");
        snapshot.render(pose.last(), capture);
        check(capture.drain().equals(snapshot), "target custom geometry replay retains every attribute");
        pose.mulPose(Axis.YP.rotationDegrees(90));
        snapshot.render(pose.last(), capture);
        var rotated = capture.drain().vertices().getFirst();
        check(Math.abs(rotated.x() - 1) < 1E-6 && Math.abs(rotated.z() + 1) < 1E-6
                && Math.abs(rotated.nz() + 1) < 1E-6, "submission pose rotates position and normal together");
        var mirror = new BedrockCubeBox(0, 0, 0, 0, 0, 16, 16, 16, 0, true, 64, 64);
        pose.setIdentity(); mirror.compile(pose.last(), capture, 0, 0, 1, 1, 1, 1);
        var mirrored = capture.drain().vertices();
        check(mirrored.getFirst().nx() == -1 && mirrored.size() == 24, "mirrored face normals and vertex count retained");
        var flat = new BedrockCubePerFace(0, 0, 0, 16, 16, 0, 0, 16, 16, FaceUVsItem.singleSouthFace());
        flat.compile(pose.last(), capture, 0, 0, 1, 1, 1, 1);
        var flatVertices = capture.drain().vertices();
        check(flatVertices.size() == 24 && flatVertices.subList(20, 24).stream().allMatch(v -> v.nz() == 1),
                "per-face south quad and baseline empty-face degenerates retained");
        check(flatVertices.subList(20, 24).stream().map(v -> List.of(v.u(), v.v())).distinct().count() == 4,
                "slot quad retains four UV corners");
        capture.addVertex(1, 2, 3).setColor(0x12345678).setLineWidth(2);
        capture.addVertex(4, 5, 6);
        var reset = capture.drain().vertices();
        check(reset.get(0).lineWidth() == 2 && reset.get(1).lineWidth() == 1 && reset.get(1).color() == -1,
                "vertex attributes do not leak between emissions");

        SubmitNodeCollector outer = collector(), inner = collector();
        try (var ignored = RenderSubmission.enter(outer)) {
            check(RenderSubmission.collector() == outer, "render scope exposes supplied target collector");
            try {
                try (var nested = RenderSubmission.enter(inner)) {
                    check(RenderSubmission.collector() == inner, "nested renderer uses its collector");
                    throw new IllegalStateException("fixture");
                }
            } catch (IllegalStateException expected) { }
            check(RenderSubmission.collector() == outer, "exception restores outer collector");
        }
        boolean cleared = false;
        try { RenderSubmission.collector(); } catch (NullPointerException expected) { cleared = true; }
        check(cleared, "scope releases target collector on exit");
        texturedBlit();
        System.out.println("Geometry checks passed: " + assertions + " assertions (CPU vertices and collector scope; no GPU/model rendering).");
    }

    private static SubmitNodeCollector collector() {
        return (SubmitNodeCollector) Proxy.newProxyInstance(GeometryChecks.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> { throw new AssertionError("Unexpected collector call " + method); });
    }

    private static void texturedBlit() {
        var pose = new PoseStack(); pose.translate(10, 20, 30);
        var quad = TexturedBlit.capture(pose, 2, 3, 4, 8, 16, 32, 64, 128);
        pose.setIdentity();
        var points = quad.vertices();
        check(points.size() == 4 && points.get(0).x() == 12 && points.get(0).y() == 55 && points.get(0).z() == 30
                && points.get(2).x() == 28 && points.get(2).y() == 23, "blit preserves source quad order and copied pose");
        check(points.get(0).u() == .0625F && points.get(0).v() == .3125F
                && points.get(2).u() == .3125F && points.get(2).v() == .0625F, "blit normalizes source texture offsets and dimensions");
        var inverted = TexturedBlit.capture(pose, 0, 0, 4, 8, -16, -32, 64, 128).vertices();
        check(inverted.get(0).y() == -32 && inverted.get(2).x() == -16 && inverted.get(2).u() == -.1875F,
                "negative source dimensions keep their mirrored geometry and UV semantics");
        Identifier first = Identifier.parse("tacz:fixture/first"), second = Identifier.parse("tacz:fixture/second");
        var submitted = new java.util.ArrayList<Object[]>();
        var collector = (SubmitNodeCollector) Proxy.newProxyInstance(GeometryChecks.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("submitCustomGeometry")) throw new AssertionError(method);
                    submitted.add(args); return null;
                });
        try (var ignored = RenderSubmission.enter(collector)) {
            TexturedBlit.withTexture(first, () -> {
                try {
                    TexturedBlit.withTexture(second, () -> {
                        TexturedBlit.submit(TexturedBlit.currentTexture(), pose, 0, 0, 0, 0, 16, 16, 16, 16);
                        throw new IllegalStateException("interrupt texture binding");
                    });
                } catch (IllegalStateException expected) { }
                check(TexturedBlit.currentTexture().equals(first), "nested failed blit restores enclosing texture binding");
                TexturedBlit.submit(TexturedBlit.currentTexture(), pose, 0, 0, 0, 0, 16, 16, 16, 16);
            });
        }
        check(submitted.size() == 2 && submitted.get(0)[1] == TaczRenderTypes.texturedBlit(second)
                && submitted.get(1)[1] == TaczRenderTypes.texturedBlit(first), "deferred blits retain each bound texture after binding changes");
        boolean unbound = false;
        try { TexturedBlit.currentTexture(); } catch (NullPointerException expected) { unbound = true; }
        check(unbound, "coordinate-only source bridge reports a missing explicit texture binding");
    }
}
