package com.tacz.guns.client.renderer.scope;

import com.tacz.guns.client.renderer.VertexCapture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A complete lens/reticle/body job. Draws inside a job must never be sorted or consolidated. */
public record ScopeRenderPlan(List<Command> draws) {
    public ScopeRenderPlan { draws = List.copyOf(draws); }

    public sealed interface Command permits Draw, NativeDraw {
        ScopeMaskState mask();
    }

    public record NativeDraw(SubmitNode submit, ScopeMaskState mask) implements Command {
        public NativeDraw { Objects.requireNonNull(submit); Objects.requireNonNull(mask); }
    }

    public record Draw(RenderType type, VertexCapture.Snapshot geometry, ScopeMaskState mask) implements Command {
        public Draw {
            Objects.requireNonNull(type);
            Objects.requireNonNull(geometry);
            Objects.requireNonNull(mask);
        }
    }

    public static final class Builder {
        private final List<Command> draws = new ArrayList<>();

        public void draw(RenderType type, VertexCapture.Snapshot geometry, ScopeMaskState mask) {
            if (!geometry.vertices().isEmpty()) draws.add(new Draw(type, geometry, mask));
        }

        public void nativeDraw(SubmitNode submit, ScopeMaskState mask) {
            draws.add(new NativeDraw(submit, mask));
        }

        public ScopeRenderPlan build() { return new ScopeRenderPlan(draws); }
    }

    /** Shared command ordering for the native backend and independent software fixtures. */
    public static <D> void execute(List<D> draws, java.util.function.Function<D, ScopeMaskState> state,
                                   Passes<D> passes) {
        try (passes) {
            passes.clearMask(0);
            int current = 0;
            for (D draw : draws) {
                ScopeMaskState mask = state.apply(draw);
                if (mask.writesMask()) {
                    int next = 1 - current;
                    // Preserve pixels outside the geometry without ever sampling a render attachment.
                    passes.copyMask(current, next);
                    passes.draw(draw, current, next);
                    current = next;
                } else {
                    passes.draw(draw, current, -1);
                }
            }
        }
    }

    public interface Passes<D> extends AutoCloseable {
        void clearMask(int target);
        void copyMask(int source, int target);
        /** target == -1 selects the draw's original color attachment. */
        void draw(D draw, int source, int target);
        @Override void close();
    }
}
