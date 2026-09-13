package com.tacz.guns.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Collector-based replacement for a blit with a globally bound shader texture. */
public final class TexturedBlit {
    private static final ThreadLocal<Identifier> TEXTURE = new ThreadLocal<>();
    private TexturedBlit() {}

    /** Keeps the original coordinate-only API usable with an explicit, nestable texture binding. */
    public static void withTexture(Identifier texture, Runnable render) {
        Identifier previous = TEXTURE.get();
        TEXTURE.set(Objects.requireNonNull(texture));
        try { render.run(); }
        finally {
            if (previous == null) TEXTURE.remove();
            else TEXTURE.set(previous);
        }
    }

    public static Identifier currentTexture() {
        return Objects.requireNonNull(TEXTURE.get(), "26.2 blits require an explicit texture or RenderHelper.withBlitTexture binding");
    }

    public static void submit(Identifier texture, PoseStack pose, float x, float y, float uOffset, float vOffset,
                              float width, float height, float textureWidth, float textureHeight) {
        RenderSubmission.submit(TaczRenderTypes.texturedBlit(Objects.requireNonNull(texture)),
                capture(pose, x, y, uOffset, vOffset, width, height, textureWidth, textureHeight));
    }

    public static VertexCapture.Snapshot capture(PoseStack pose, float x, float y, float uOffset, float vOffset,
                                                  float width, float height, float textureWidth, float textureHeight) {
        var vertices = new VertexCapture();
        float minU = uOffset / textureWidth, maxU = (uOffset + width) / textureWidth;
        float minV = vOffset / textureHeight, maxV = (vOffset + height) / textureHeight;
        vertices.addVertex(pose.last(), x, y + height, 0).setUv(minU, maxV).setColor(-1);
        vertices.addVertex(pose.last(), x + width, y + height, 0).setUv(maxU, maxV).setColor(-1);
        vertices.addVertex(pose.last(), x + width, y, 0).setUv(maxU, minV).setColor(-1);
        vertices.addVertex(pose.last(), x, y, 0).setUv(minU, minV).setColor(-1);
        return vertices.drain();
    }
}
