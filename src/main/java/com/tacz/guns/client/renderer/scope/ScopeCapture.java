package com.tacz.guns.client.renderer.scope;

import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

import java.util.Objects;

/** Diverts native collector submissions into one scope job during model extraction. */
public final class ScopeCapture implements AutoCloseable {
    private static final ThreadLocal<ScopeCapture> CURRENT = new ThreadLocal<>();
    private final ScopeRenderPlan.Builder builder = new ScopeRenderPlan.Builder();
    private ScopeMaskState mask = ScopeMaskState.UNMASKED;
    private ScopeRenderPlan finished;

    private ScopeCapture() {}

    public static ScopeCapture begin() {
        if (CURRENT.get() != null) throw new IllegalStateException("A scope capture is already active; append to its current mask stage");
        ScopeCapture capture = new ScopeCapture();
        CURRENT.set(capture);
        return capture;
    }

    public static boolean active() { return CURRENT.get() != null; }

    /** Called at the native phase submission boundary, after the collector has captured the pose. */
    public static boolean capture(SubmitNode submit) {
        ScopeCapture capture = CURRENT.get();
        if (capture == null) return false;
        if (submit instanceof ScopeFeatureRenderer.Submit) throw new IllegalStateException("Submit a completed scope job after closing its capture");
        if (submit instanceof ModelFeatureRenderer.Submit<?> model && model.model() instanceof Model.Simple) {
            // Native submitModelPart wraps a mutable arm/part in Model.Simple, whose setupAnim
            // does nothing. Freeze it before a later hand or model reset changes that pose.
            capture.builder.draw(model.renderType(), ScopeNativeGeometry.freezeModelPart(model), capture.mask);
        } else {
            capture.builder.nativeDraw(submit, capture.mask);
        }
        return true;
    }

    public static void withMask(ScopeMaskState mask, Runnable render) {
        ScopeCapture capture = Objects.requireNonNull(CURRENT.get(), "No active scope capture");
        ScopeMaskState previous = capture.mask;
        capture.mask = Objects.requireNonNull(mask);
        try { render.run(); }
        finally { capture.mask = previous; }
    }

    public ScopeRenderPlan plan() {
        return Objects.requireNonNull(finished, "Close scope capture before submitting its plan");
    }

    @Override public void close() {
        if (finished != null) return;
        if (CURRENT.get() != this) throw new IllegalStateException("Scope capture closed on a different thread");
        CURRENT.remove();
        finished = builder.build();
    }
}
