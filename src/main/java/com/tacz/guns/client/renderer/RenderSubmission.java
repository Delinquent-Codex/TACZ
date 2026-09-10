package com.tacz.guns.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Objects;

/** Carries the target collector through the public model/functional-renderer call graph. */
public final class RenderSubmission implements AutoCloseable {
    private static final ThreadLocal<SubmitNodeCollector> CURRENT = new ThreadLocal<>();
    private final SubmitNodeCollector previous;

    private RenderSubmission(SubmitNodeCollector collector) {
        previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(collector));
    }

    public static RenderSubmission enter(SubmitNodeCollector collector) { return new RenderSubmission(collector); }

    public static SubmitNodeCollector collector() {
        return Objects.requireNonNull(CURRENT.get(), "TACZ geometry must be submitted inside a render collector scope");
    }

    public static void submit(RenderType type, VertexCapture.Snapshot snapshot) {
        if (!snapshot.vertices().isEmpty()) {
            // Positions already include the model's extraction-time pose. Do not apply it twice.
            collector().submitCustomGeometry(new PoseStack(), type, snapshot);
        }
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
