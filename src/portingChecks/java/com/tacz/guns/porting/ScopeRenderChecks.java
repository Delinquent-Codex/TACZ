package com.tacz.guns.porting;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.renderer.scope.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.tacz.guns.client.renderer.scope.ScopeMaskState.Comparison.*;

public final class ScopeRenderChecks {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { assertions++; }
    }

    public static void main(String[] args) throws Exception {
        // Exhaust all representable references/stored values against GL's reference-left definition.
        boolean comparisons = true, writes = true, rejects = true;
        for (int reference = 0; reference < 256; reference++) for (int stored = 0; stored < 256; stored++) {
            var ocular = ScopeMaskState.ocular(reference);
            var aperture = ScopeMaskState.aperture(reference);
            comparisons &= ocular.accepts(stored) == (reference > stored) && aperture.accepts(stored) == (reference == stored);
            writes &= ocular.apply(stored, true) == Math.max(reference, stored);
            writes &= aperture.apply(stored, true) == (reference == stored ? 255 - stored : stored);
            rejects &= ocular.apply(stored, false) == stored && aperture.apply(stored, false) == stored;
        }
        check(comparisons, "all 65,536 GL comparison pairs");
        check(writes, "all eight-bit REPLACE/INVERT results");
        check(rejects, "failed depth or alpha leaves every stored value unchanged");
        check(ScopeMaskState.ocular(-1).reference() == 0 && ScopeMaskState.ocular(999).reference() == 255,
                "GL reference clamps instead of wrapping");
        check(ScopeAperture.divisionReference(126, true) == 128 && ScopeAperture.divisionReference(127, true) == 127,
                "source inversion at the 127/128 boundary");
        check(ScopeAperture.divisionReference(127, false) == 128, "source accepts ocular index 127");
        rejects(() -> ScopeAperture.divisionReference(128, true), "source rejects ocular index 128");
        check(ScopeMaskState.color(GREATER, 127, true).accepts(126)
                        && !ScopeMaskState.color(GREATER, 127, true).accepts(127)
                        && !ScopeMaskState.color(GREATER, 127, true).accepts(128),
                "mixed body retains source's asymmetric GREATER 127 comparison");

        // Each fixture sample represents a distinct screen pixel; alpha and reverse-Z depth are
        // resolved independently of ScopeMaskState, like the original fragment/stencil pipeline.
        List<FragmentDraw> mixed = List.of(
                draw("scope ocular 2", ScopeMaskState.ocular(2), 0, 1, 2),
                draw("scope body", ScopeMaskState.color(EQUAL, 0, true), 0, 1, 2, 3, 4),
                draw("sight ocular 1", ScopeMaskState.ocular(1), 1, 3),
                draw("scope aperture", ScopeMaskState.aperture(2), 0, 1),
                draw("scope blackout", ScopeMaskState.color(EQUAL, 2, true), 0, 1, 2),
                draw("scope reticle", ScopeMaskState.color(EQUAL, 253, true), 0, 1, 2),
                draw("sight reticle", ScopeMaskState.color(EQUAL, 1, false), 1, 3),
                draw("gun", ScopeMaskState.color(GREATER, 127, true), 0, 1, 2, 3, 4));
        SoftwarePasses mixedPasses = new SoftwarePasses(5);
        ScopeRenderPlan.execute(mixed, FragmentDraw::state, mixedPasses);
        check(Arrays.equals(mixedPasses.lastMask, new int[]{253, 253, 2, 1, 0}), "mixed scope/sight overlap, aperture and untouched pixels");
        check(mixedPasses.visible.get(1).equals(List.of(3, 4)), "body sees scope-only intermediate mask before sight writes");
        check(mixedPasses.visible.get(4).equals(List.of(2)) && mixedPasses.visible.get(5).equals(List.of(0, 1)), "blackout and reticle partition the scope");
        check(mixedPasses.visible.get(6).equals(List.of(3)), "sight reticle cannot overwrite overlapping scope");
        check(mixedPasses.visible.get(7).equals(List.of(2, 3, 4)), "gun uses completed aperture mask");
        check(mixedPasses.closed && mixedPasses.copies == 3 && !mixedPasses.feedback, "one copy per mask update, no attachment feedback, closes on success");

        var simple = new SoftwarePasses(2);
        ScopeRenderPlan.execute(List.of(draw("lens", ScopeMaskState.ocular(1), 0),
                new FragmentDraw("reticle", ScopeMaskState.color(EQUAL, 1, false), new int[]{0, 1}, new boolean[]{true, true}, new boolean[]{false, false})),
                FragmentDraw::state, simple);
        check(simple.visible.get(1).equals(List.of(0)), "simple sight disables depth for reticle while retaining mask test");
        var rejected = new SoftwarePasses(4);
        ScopeRenderPlan.execute(List.of(new FragmentDraw("occluded/transparent", ScopeMaskState.ocular(3),
                new int[]{0, 1, 2, 3}, new boolean[]{true, false, true, false}, new boolean[]{true, true, false, false})),
                FragmentDraw::state, rejected);
        check(Arrays.equals(rejected.lastMask, new int[]{3, 0, 0, 0}), "alpha and world depth reject independently");
        var failed = new SoftwarePasses(2);
        failed.throwAt = 1;
        try {
            ScopeRenderPlan.execute(List.of(draw("first", ScopeMaskState.ocular(1), 0), draw("interrupted", ScopeMaskState.aperture(1), 0)),
                    FragmentDraw::state, failed);
            throw new AssertionError("must propagate draw failure");
        } catch (IllegalStateException expected) { check(failed.closed, "failed pass closes its job without swallowing failure"); }
        var empty = new SoftwarePasses(2);
        ScopeRenderPlan.execute(List.<FragmentDraw>of(), FragmentDraw::state, empty);
        check(empty.closed && Arrays.equals(empty.lastMask, new int[2]), "empty plan still clears and closes");

        Random random = new Random(26206510);
        for (int trial = 0; trial < 100; trial++) {
            var draws = new ArrayList<FragmentDraw>();
            int[] referenceMask = new int[32];
            var expectedVisible = new ArrayList<List<Integer>>();
            for (int step = 0; step < 30; step++) {
                var operation = ScopeMaskState.Operation.values()[random.nextInt(3)];
                var comparison = ScopeMaskState.Comparison.values()[random.nextInt(3)];
                int ref = random.nextInt(256);
                boolean depth = random.nextBoolean();
                int[] pixels = new int[32]; boolean[] alpha = new boolean[32], depths = new boolean[32];
                var expected = new ArrayList<Integer>();
                for (int pixel = 0; pixel < 32; pixel++) {
                    pixels[pixel] = pixel; alpha[pixel] = random.nextBoolean(); depths[pixel] = random.nextBoolean();
                    int old = referenceMask[pixel];
                    boolean stencil = comparison == ALWAYS || comparison == EQUAL && ref == old || comparison == GREATER && ref > old;
                    if (alpha[pixel] && (!depth || depths[pixel]) && stencil) {
                        switch (operation) {
                            case COLOR -> expected.add(pixel);
                            case REPLACE -> referenceMask[pixel] = ref;
                            case INVERT -> referenceMask[pixel] = (~old) & 0xff;
                        }
                    }
                }
                expectedVisible.add(expected);
                draws.add(new FragmentDraw("random", new ScopeMaskState(operation, comparison, ref, depth), pixels, alpha, depths));
            }
            var passes = new SoftwarePasses(32);
            ScopeRenderPlan.execute(draws, FragmentDraw::state, passes);
            check(Arrays.equals(referenceMask, passes.lastMask) && expectedVisible.equals(passes.visible), "independent GL-style program " + trial);
        }

        var aperture = ScopeAperture.capture(.125F, -.25F, 1.5F, .6F).vertices();
        check(aperture.size() == 270, "90 fan triangles expanded without shared mutable vertices");
        for (int triangle = 0; triangle < 90; triangle++) {
            var center = aperture.get(triangle * 3);
            boolean matches = center.x() == 180 && center.y() == -360 && center.z() == -90;
            for (int offset = 0; offset < 2; offset++) {
                float angle = (triangle + offset) * ((float) Math.PI * 2) / 90;
                var rim = aperture.get(triangle * 3 + offset + 1);
                matches &= rim.x() == 180 + Mth.cos(angle) * 72 && rim.y() == -360 + Mth.sin(angle) * 72 && rim.z() == -90 && rim.color() == -1;
            }
            check(matches, "source fan positions/winding at triangle " + triangle);
        }
        check(ScopeAperture.capture(0, 0, 1, 0).vertices().stream().allMatch(v -> v.x() == 0 && v.y() == 0), "zero aiming progress collapses aperture");
        check(ScopeAperture.capture(0, 0, 2, 1).vertices().get(1).x() == 160, "radius modifier and full aiming progress");

        var builder = new ScopeRenderPlan.Builder();
        var geometry = new VertexCapture.Snapshot(aperture);
        builder.draw(TaczRenderTypes.laserBeam(), geometry, ScopeMaskState.ocular(1));
        var snapshot = builder.build();
        builder.draw(TaczRenderTypes.laserBeam(), geometry, ScopeMaskState.aperture(1));
        check(snapshot.draws().size() == 1 && builder.build().draws().size() == 2, "plan owns immutable operation list");
        try { snapshot.draws().clear(); throw new AssertionError("mutable plan"); }
        catch (UnsupportedOperationException expected) { assertions++; }

        for (var original : List.of(RenderPipelines.ENTITY_TRANSLUCENT_CULL, TaczRenderTypes.laserBeam().pipeline(),
                RenderPipelines.ENTITY_CUTOUT, RenderPipelines.TEXT)) {
            var color = ScopePipelines.create(original, false, true);
            var mask = ScopePipelines.create(original, true, true);
            var reticle = ScopePipelines.create(original, false, false);
            check(color.getDepthStencilState().equals(original.getDepthStencilState()) && color.getColorTargetState().equals(original.getColorTargetState()), "native color/depth/blending retained " + original.getLocation());
            check(!mask.getDepthStencilState().writeDepth() && mask.getDepthStencilState().depthTest() == original.getDepthStencilState().depthTest()
                            && mask.getColorTargetState().equals(ColorTargetState.DEFAULT), "mask uses native depth comparison without depth writes/blending " + original.getLocation());
            check(reticle.getDepthStencilState() == null, "reticle depth disabled in pipeline " + original.getLocation());
            check(color.getShaderDefines().equals(original.getShaderDefines()) && color.getVertexShader().equals(original.getVertexShader())
                            && color.isCull() == original.isCull() && Arrays.equals(color.getVertexFormatBindings(), original.getVertexFormatBindings())
                            && color.getPrimitiveTopology() == original.getPrimitiveTopology(), "native geometry/lighting/alpha definitions retained " + original.getLocation());
            check(BindGroupLayout.flattenSamplers(color.getBindGroupLayouts()).contains("TaczScopeMask")
                            && BindGroupLayout.flattenUniforms(color.getBindGroupLayouts()).stream().anyMatch(u -> u.name().equals("TaczScopeControl")), "mask bindings declared " + original.getLocation());
        }
        var biased = RenderPipeline.builder().withLocation(net.minecraft.resources.Identifier.parse("tacz:test_biased")).withVertexShader("core/position_color").withFragmentShader("core/position_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, true, 2, 3)).build();
        check(ScopePipelines.create(biased, true, true).getDepthStencilState().equals(new DepthStencilState(CompareOp.GREATER_THAN, false, 2, 3)), "mask preserves depth bias");

        String laser = Files.readString(Path.of("src/main/resources/assets/tacz/shaders/core/laser.fsh"));
        String wrapped = ScopeShader.wrap(laser);
        check(wrapped.startsWith("#version") && wrapped.contains("void tacz_scope_original_main()"), "wrapper preserves GLSL version placement");
        check(wrapped.contains("beam.a < 0.1") && wrapped.contains("tacz_scope_original_main();"), "original alpha rejection executes before mask write");
        check(wrapped.contains("texelFetch(TaczScopeMask, ivec2(gl_FragCoord.xy), 0)") && wrapped.contains("reference <= stored"), "pixel-aligned sampling and reference-left comparison");
        String commented = "#version 150\n// void main() {}\n/* void main(void) {} */\nout vec4 fragColor;\nvoid main(void) {fragColor=vec4(1);}";
        check(ScopeShader.wrap(commented).contains("// void main() {}") && ScopeShader.wrap(commented).contains("void tacz_scope_original_main() {fragColor"), "comments do not redirect main renaming");
        rejects(() -> ScopeShader.wrap("// out vec4 fragColor;\nvoid main() {}"), "comment is not a fragment output");
        rejects(() -> ScopeShader.wrap("out vec4 fragColor;\nvoid main() {}\nvoid main() {}"), "ambiguous main rejected");
        rejects(() -> ScopeShader.wrap(null), "missing current shader rejected");
        System.out.println("Scope rendering checks passed: " + assertions + " assertions (CPU/native descriptions only; no GPU/FML)");
    }

    private record FragmentDraw(String name, ScopeMaskState state, int[] pixels, boolean[] alpha, boolean[] depth) {}
    private static FragmentDraw draw(String name, ScopeMaskState state, int... pixels) {
        boolean[] pass = new boolean[pixels.length]; Arrays.fill(pass, true);
        return new FragmentDraw(name, state, pixels, pass, pass);
    }

    private static final class SoftwarePasses implements ScopeRenderPlan.Passes<FragmentDraw> {
        private final int[][] masks;
        private final List<List<Integer>> visible = new ArrayList<>();
        private int[] lastMask;
        private int copies, throwAt = -1;
        private boolean feedback, closed;
        SoftwarePasses(int pixels) { masks = new int[][]{new int[pixels], new int[pixels]}; }
        @Override public void clearMask(int target) { Arrays.fill(masks[target], 0); lastMask = masks[target]; }
        @Override public void copyMask(int source, int target) { copies++; System.arraycopy(masks[source], 0, masks[target], 0, masks[source].length); }
        @Override public void draw(FragmentDraw draw, int source, int target) {
            if (visible.size() == throwAt) throw new IllegalStateException("fixture interruption");
            feedback |= source == target;
            var accepted = new ArrayList<Integer>();
            visible.add(accepted);
            for (int i = 0; i < draw.pixels.length; i++) {
                int pixel = draw.pixels[i], old = masks[source][pixel];
                boolean fragment = draw.alpha[i] && (!draw.state.depthTest() || draw.depth[i]);
                if (target == -1) {
                    if (fragment && draw.state.accepts(old)) accepted.add(pixel);
                } else masks[target][pixel] = draw.state.apply(old, fragment);
            }
            lastMask = masks[target == -1 ? source : target];
        }
        @Override public void close() { closed = true; }
    }
}
