package com.tacz.guns.client.renderer.scope;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.renderer.VertexCapture;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Reuses native feature builders without their independently sorted draw groups. */
public final class ScopeNativeGeometry {
    private ScopeNativeGeometry() {}

    public record Batch(RenderType type, PreparedRenderType prepared, VertexCapture.Snapshot geometry) {}

    /** Model.Simple has no extracted animation state: its live part pose must be consumed now. */
    static VertexCapture.Snapshot freezeModelPart(ModelFeatureRenderer.Submit<?> submit) {
        VertexCapture vertices = new VertexCapture();
        new EntityModel(ignored -> vertices).build(null, submit);
        return vertices.drain();
    }

    public static List<Batch> capture(FeatureFrameContext context, SubmitNode submit,
                                      Function<RenderType, PreparedRenderType> prepareType) {
        Output output = new Output(prepareType);
        // Each bridge owns its native temporary model/quad state. Exceptions cannot leave the
        // dispatcher's normal feature renderer inside a partially prepared group.
        switch (submit) {
            case CustomFeatureRenderer.Submit custom -> new Custom(output).build(context, custom);
            case ModelFeatureRenderer.Submit<?> model -> new EntityModel(output).build(context, model);
            case TextFeatureRenderer.Submit text -> new Text(output).build(context, text);
            case ItemFeatureRenderer.Submit item -> new Item(output).build(context, item);
            case BlockModelFeatureRenderer.Submit block -> new BlockModel(output).build(context, block);
            default -> throw new IllegalArgumentException("Unsupported native feature inside scope job: " + submit.featureType());
        }
        return output.finish();
    }

    private static final class Output {
        private final Function<RenderType, PreparedRenderType> prepare;
        private final List<Entry> entries = new ArrayList<>();
        private Entry last;
        Output(Function<RenderType, PreparedRenderType> prepare) { this.prepare = Objects.requireNonNull(prepare); }

        VertexConsumer buffer(RenderType type) {
            if (last == null || last.type != type || !type.canConsolidateConsecutiveGeometry()) {
                // Capture textures, scissor and dynamic transforms at the same point as the
                // target getVertexBuilder, before another feature changes preparation state.
                last = new Entry(type, prepare.apply(type), new VertexCapture());
                entries.add(last);
            }
            return last.vertices;
        }

        List<Batch> finish() {
            var batches = new ArrayList<Batch>();
            for (Entry entry : entries) {
                var geometry = entry.vertices.drain();
                if (!geometry.vertices().isEmpty()) batches.add(new Batch(entry.type, entry.prepared, geometry));
            }
            return List.copyOf(batches);
        }
        private record Entry(RenderType type, PreparedRenderType prepared, VertexCapture vertices) {}
    }

    // Access transformer removes only final from getVertexBuilder. Native buildGroup code,
    // including glyph shadows, model animation, baked-quad tint and item foil passes, is reused.
    private static final class Custom extends CustomFeatureRenderer {
        private final Output output;
        Custom(Output output) { this.output = output; }
        @Override protected VertexConsumer getVertexBuilder(RenderType type) { return output.buffer(type); }
        void build(FeatureFrameContext context, Submit submit) { super.buildGroup(context, List.of(submit)); }
    }

    private static final class EntityModel extends ModelFeatureRenderer {
        private final Function<RenderType, VertexConsumer> buffer;
        EntityModel(Output output) { this(output::buffer); }
        EntityModel(Function<RenderType, VertexConsumer> buffer) { this.buffer = buffer; }
        @Override protected VertexConsumer getVertexBuilder(RenderType type) { return buffer.apply(type); }
        void build(FeatureFrameContext context, Submit<?> submit) { super.buildGroup(context, List.of(submit)); }
    }

    private static final class Text extends TextFeatureRenderer {
        private final Output output;
        Text(Output output) { this.output = output; }
        @Override protected VertexConsumer getVertexBuilder(RenderType type) { return output.buffer(type); }
        void build(FeatureFrameContext context, Submit submit) { super.buildGroup(context, List.of(submit)); }
    }

    private static final class Item extends ItemFeatureRenderer {
        private final Output output;
        Item(Output output) { this.output = output; }
        @Override protected VertexConsumer getVertexBuilder(RenderType type) { return output.buffer(type); }
        void build(FeatureFrameContext context, Submit submit) { super.buildGroup(context, List.of(submit)); }
    }

    private static final class BlockModel extends BlockModelFeatureRenderer {
        private final Output output;
        BlockModel(Output output) { this.output = output; }
        @Override protected VertexConsumer getVertexBuilder(RenderType type) { return output.buffer(type); }
        void build(FeatureFrameContext context, Submit submit) { super.buildGroup(context, List.of(submit)); }
    }
}
