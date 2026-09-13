package com.tacz.guns.client.renderer.scope;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector4f;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Executes each complete scope job atomically in the native feature draw phase, on either GPU backend. */
public final class ScopeFeatureRenderer implements FeatureRenderer<ScopeFeatureRenderer.Submit> {
    public static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("TACZ Scope");
    private final List<List<List<PreparedDraw>>> groups = new ArrayList<>();
    private final Map<PipelineKey, RenderPipeline> pipelines = new HashMap<>();
    private final ShaderSource shaderSource;
    private final java.util.function.Function<RenderType, PreparedRenderType> prepareType;

    public ScopeFeatureRenderer() {
        this((id, type) -> Minecraft.getInstance().getShaderManager().getShader(id, type), RenderType::prepare);
    }

    /** Allows an offscreen context to supply its own textures/transforms and reloadable shader source. */
    public ScopeFeatureRenderer(ShaderSource shaderSource, java.util.function.Function<RenderType, PreparedRenderType> prepareType) {
        this.shaderSource = Objects.requireNonNull(shaderSource);
        this.prepareType = Objects.requireNonNull(prepareType);
    }

    public record Submit(ScopeRenderPlan plan) implements SubmitNode {
        public Submit { Objects.requireNonNull(plan); }
        @Override public FeatureRendererType<Submit> featureType() { return TYPE; }
    }

    public static void submit(SubmitNodeCollector collector, int order, ScopeRenderPlan plan) {
        if (plan.draws().isEmpty()) return;
        if (!(collector.order(order) instanceof SubmitNodeCollection collection)) {
            throw new IllegalArgumentException("Scope jobs require the native SubmitNodeCollection");
        }
        // One unbatchable node contains the ENTIRE sequence, including clipped gun geometry.
        // Native phase grouping can move jobs, but cannot split/reorder the operations within one.
        collection.translucentCustomGeometry.submit(new Submit(plan));
    }

    @Override
    public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean strictlyOrdered) {
        var jobs = new ArrayList<List<PreparedDraw>>();
        for (Submit submit : submits) {
            var draws = new ArrayList<PreparedDraw>();
            for (ScopeRenderPlan.Command command : submit.plan.draws()) {
                switch (command) {
                    case ScopeRenderPlan.Draw draw -> prepareDraw(context, draws, draw.type(), prepareType.apply(draw.type()), draw.geometry(), draw.mask());
                    case ScopeRenderPlan.NativeDraw nativeDraw -> {
                        for (var batch : ScopeNativeGeometry.capture(context, nativeDraw.submit(), prepareType)) {
                            prepareDraw(context, draws, batch.type(), batch.prepared(), batch.geometry(), nativeDraw.mask());
                        }
                    }
                }
            }
            jobs.add(List.copyOf(draws));
        }
        groups.add(List.copyOf(jobs));
    }

    private static void prepareDraw(FeatureFrameContext context, List<PreparedDraw> draws, RenderType type, PreparedRenderType prepared,
                                    com.tacz.guns.client.renderer.VertexCapture.Snapshot geometry, ScopeMaskState mask) {
        var staged = context.stagedVertexBuffer().appendDraw(type.format(), type.primitiveTopology(),
                type.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null);
        geometry.render(new PoseStack().last(), context.stagedVertexBuffer().getVertexBuilder(staged));
        draws.add(new PreparedDraw(prepared, staged, mask));
    }

    @Override
    public void executeGroup(FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
        for (List<PreparedDraw> draws : groups.get(groupIndex)) {
            if (!draws.isEmpty()) {
                PreparedDraw masked = draws.stream().filter(draw -> !draw.mask.equals(ScopeMaskState.UNMASKED)).findFirst().orElse(null);
                if (masked == null) {
                    for (PreparedDraw draw : draws) drawNative(context, draw);
                } else {
                    ScopeRenderPlan.execute(draws, PreparedDraw::mask, new NativePasses(context, masked));
                }
            }
        }
    }

    @Override public void finishExecute(FeatureFrameContext context) { groups.clear(); }
    @Override public void close() { groups.clear(); pipelines.clear(); }

    private record PreparedDraw(PreparedRenderType type, StagedVertexBuffer.Draw staged, ScopeMaskState mask) {}
    private record PipelineKey(RenderPipeline original, boolean writesMask, boolean depthTest) {}

    private static void drawNative(FeatureFrameContext context, PreparedDraw draw) {
        var info = context.stagedVertexBuffer().getExecuteInfo(draw.staged);
        if (info != null) draw.type.drawFromBuffer(info);
    }

    private RenderPipeline pipeline(PreparedDraw draw) {
        RenderPipeline original = draw.type.pipeline();
        var key = new PipelineKey(original, draw.mask.writesMask(), draw.mask.depthTest());
        RenderPipeline pipeline = pipelines.computeIfAbsent(key,
                ignored -> ScopePipelines.create(original, key.writesMask, key.depthTest));
        // The device owns the compiled cache and clears it on shader reload. Always ask it, so a
        // replacement resource pack is used after reload instead of keeping a stale program.
        var compiled = RenderSystem.getDevice().precompilePipeline(pipeline, (id, type) ->
                type == ShaderType.FRAGMENT && id.equals(pipeline.getFragmentShader())
                        ? ScopeShader.wrap(shaderSource.get(original.getFragmentShader(), type)) : shaderSource.get(id, type));
        if (!compiled.isValid()) throw new IllegalStateException("Failed to compile scope pipeline " + pipeline.getLocation());
        return pipeline;
    }

    private static GpuTextureView color(PreparedRenderType type) {
        return Objects.requireNonNull(RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride
                : type.outputTarget().getRenderTarget().getColorTextureView());
    }

    private static GpuTextureView depth(PreparedRenderType type) {
        var target = type.outputTarget().getRenderTarget();
        return !target.useDepth ? null : RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride
                : target.getDepthTextureView();
    }

    private final class NativePasses implements ScopeRenderPlan.Passes<PreparedDraw> {
        private final FeatureFrameContext context;
        private final CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        private final int width, height;
        private final GpuTexture[] masks = new GpuTexture[2];
        private final GpuTextureView[] views = new GpuTextureView[2];
        private boolean closed;

        private NativePasses(FeatureFrameContext context, PreparedDraw first) {
            this.context = context;
            GpuTextureView targetColor = color(first.type);
            width = targetColor.getWidth(0);
            height = targetColor.getHeight(0);
            try {
                for (int i = 0; i < 2; i++) {
                    masks[i] = RenderSystem.getDevice().createTexture("TACZ scope mask " + i,
                            GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                                    | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
                    views[i] = RenderSystem.getDevice().createTextureView(masks[i]);
                }
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        @Override public void clearMask(int target) {
            encoder.clearColorTexture(masks[target], new Vector4f());
        }

        @Override public void copyMask(int source, int target) {
            encoder.copyTextureToTexture(masks[source], masks[target], 0, 0, 0, 0, 0, width, height);
        }

        @Override public void draw(PreparedDraw draw, int source, int target) {
            // Lasers and other unmasked features retain their native output target/pipeline.
            if (draw.mask.equals(ScopeMaskState.UNMASKED)) {
                drawNative(context, draw);
                return;
            }
            GpuTextureView targetColor = color(draw.type);
            GpuTextureView targetDepth = depth(draw.type);
            if (targetColor.getWidth(0) != width || targetColor.getHeight(0) != height) {
                throw new IllegalArgumentException("Masked scope outputs must share a pixel coordinate space");
            }
            var info = context.stagedVertexBuffer().getExecuteInfo(draw.staged);
            if (info == null) return;
            RenderPipeline pipeline = pipeline(draw);
            GpuBufferSlice control;
            int alignment = RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment();
            try (var uniform = encoder.transientMemory().allocateGpuMapped(16, alignment, GpuBuffer.USAGE_UNIFORM)) {
                uniform.data().order(ByteOrder.nativeOrder()).putInt(draw.mask.operation().ordinal())
                        .putInt(draw.mask.comparison().ordinal()).putInt(draw.mask.reference()).putInt(0);
                control = uniform.slice();
            }
            try (var pass = encoder.createRenderPass(() -> "TACZ scope " + draw.mask.operation(),
                    target == -1 ? targetColor : views[target], Optional.empty(), targetDepth, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                var scissor = draw.type.scissorState();
                if (scissor.enabled()) pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", draw.type.dynamicTransforms());
                pass.setUniform("TaczScopeControl", control);
                pass.setVertexBuffer(0, info.vertexBuffer().slice());
                for (var texture : draw.type.textures()) pass.bindTexture(texture.name(), texture.textureView(), texture.sampler());
                pass.bindTexture("TaczScopeMask", views[source], RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setIndexBuffer(info.indexBuffer(), info.indexType());
                pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            // Command submission is deferred. Keep both views and their textures alive until the
            // GPU fence completes, including partial jobs interrupted by an exception or resize.
            RenderSystem.queueFencedTask(() -> {
                for (GpuTextureView view : views) if (view != null) view.close();
                for (GpuTexture mask : masks) if (mask != null) mask.close();
            });
        }
    }
}
