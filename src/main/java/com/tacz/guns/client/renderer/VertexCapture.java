package com.tacz.guns.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;

/** Captures animated vertices before model transforms are reset; submissions own immutable values. */
public final class VertexCapture implements VertexConsumer {
    public record Vertex(float x, float y, float z, int color, float u, float v,
                         int overlay, int light, float nx, float ny, float nz, float lineWidth) {}

    public record Snapshot(List<Vertex> vertices) implements SubmitNodeCollector.CustomGeometryRenderer {
        public Snapshot { vertices = List.copyOf(vertices); }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer output) {
            for (Vertex vertex : vertices) {
                output.addVertex(pose, vertex.x, vertex.y, vertex.z).setColor(vertex.color)
                        .setUv(vertex.u, vertex.v).setOverlay(vertex.overlay).setLight(vertex.light)
                        .setNormal(pose, vertex.nx, vertex.ny, vertex.nz).setLineWidth(vertex.lineWidth);
            }
        }
    }

    private final List<Vertex> vertices = new ArrayList<>();
    private boolean active;
    private float x, y, z, u, v, nx, ny, nz, width;
    private int color, overlay, light;

    private void finishVertex() {
        if (active) vertices.add(new Vertex(x, y, z, color, u, v, overlay, light, nx, ny, nz, width));
        active = false;
    }

    /** Flushes this batch. Later geometry can reuse the capture without changing an earlier submission. */
    public Snapshot drain() {
        finishVertex();
        Snapshot result = new Snapshot(vertices);
        vertices.clear();
        return result;
    }

    private void requireVertex() {
        if (!active) throw new IllegalStateException("No vertex to receive attributes");
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        finishVertex();
        active = true;
        this.x = x; this.y = y; this.z = z;
        color = -1; overlay = light = 0;
        u = v = nx = ny = nz = 0;
        width = 1;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(ARGB.color(a, r, g, b)); }
    @Override
    public VertexConsumer setColor(int color) { requireVertex(); this.color = color; return this; }
    @Override
    public VertexConsumer setUv(float u, float v) { requireVertex(); this.u = u; this.v = v; return this; }
    @Override
    public VertexConsumer setUv1(int u, int v) { requireVertex(); overlay = (u & 65535) | (v & 65535) << 16; return this; }
    @Override
    public VertexConsumer setUv2(int u, int v) { requireVertex(); light = (u & 65535) | (v & 65535) << 16; return this; }
    @Override
    public VertexConsumer setNormal(float x, float y, float z) { requireVertex(); nx = x; ny = y; nz = z; return this; }
    @Override
    public VertexConsumer setLineWidth(float width) { requireVertex(); this.width = width; return this; }
}
