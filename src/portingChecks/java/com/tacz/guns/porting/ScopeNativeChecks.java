package com.tacz.guns.porting;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.renderer.scope.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;

/** Native feature builders with explicit small model/glyph/quad fixtures; no asset bake or FML. */
public final class ScopeNativeChecks {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static final RenderType TYPE = TaczRenderTypes.laserBeam();
    private static final Function<RenderType, PreparedRenderType> PREPARE = type -> new PreparedRenderType(
            type.pipeline(), type.outputTarget(), null, new com.mojang.blaze3d.systems.ScissorState(), List.of());

    public static void main(String[] args) throws Exception {
        var method = RenderTypeFeatureRenderer.class.getDeclaredMethod("getVertexBuilder", RenderType.class);
        check(Modifier.isProtected(method.getModifiers()) && !Modifier.isFinal(method.getModifiers()), "actual target access transform enables native builder destination override");
        var pose = new PoseStack(); pose.translate(2, 3, 4);
        var vertices = new VertexCapture(); vertices.addVertex(1, 2, 3).setColor(0xff123456).setUv(.25F, .75F);
        var custom = new CustomFeatureRenderer.Submit(pose.last().copy(), TYPE, vertices.drain());
        pose.setIdentity();
        var customBatches = ScopeNativeGeometry.capture(null, custom, PREPARE);
        var v = customBatches.getFirst().geometry().vertices().getFirst();
        check(v.x() == 3 && v.y() == 5 && v.z() == 7 && v.color() == 0xff123456 && v.u() == .25F, "native custom builder applies captured pose exactly once");
        check(customBatches.getFirst().prepared().pipeline() == TYPE.pipeline(), "prepared context accompanies its native batch");
        try {
            ScopeNativeGeometry.capture(null, new CustomFeatureRenderer.Submit(pose.last().copy(), TYPE, (p, out) -> {
                out.addVertex(0, 0, 0); throw new IllegalStateException("fixture failure");
            }), PREPARE);
            throw new AssertionError("expected renderer failure");
        } catch (IllegalStateException expected) { assertions++; }
        check(ScopeNativeGeometry.capture(null, custom, PREPARE).getFirst().geometry().equals(customBatches.getFirst().geometry()), "failed native builder cannot retain vertices or mutable group state");

        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("panel", CubeListBuilder.create().addBox(-8, -8, 0, 16, 16, 0), PartPose.ZERO);
        var root = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        int[] setupCalls = {0};
        Model<Float> model = new Model<>(root, ignored -> TYPE) {
            @Override public void setupAnim(Float state) { super.setupAnim(state); root().x = state; setupCalls[0]++; }
        };
        var modelNode = new ModelFeatureRenderer.Submit<>(TYPE, pose.last().copy(), model, 16F, 1234, 4321, 0xff123456, null, null);
        var modelBatches = ScopeNativeGeometry.capture(null, modelNode, PREPARE);
        check(setupCalls[0] == 1 && modelBatches.getFirst().geometry().vertices().size() == 24, "native model animation and all cube faces run once");
        check(modelBatches.getFirst().geometry().vertices().stream().allMatch(p -> p.x() >= .5F && p.x() <= 1.5F && p.light() == 1234 && p.overlay() == 4321 && p.color() == 0xff123456), "native model transform, packed light/overlay and tint survive capture");
        root.x = 1000;
        check(modelBatches.getFirst().geometry().vertices().stream().allMatch(p -> p.x() < 2), "prepared model geometry is detached from later bone changes");

        // Native font preparation and BakedSheetGlyph implement layout, shadow, style and vertices.
        // Only glyph atlas selection/metrics are supplied by the fixture provider.
        var renderTypes = new GlyphRenderTypes(TYPE, TYPE, TYPE, TYPE.pipeline());
        var glyph = new BakedSheetGlyph(GlyphInfo.simple(6), renderTypes, null, 0, 1, 0, 1, 0, 5, 0, 7);
        Font font = new Font(new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription description) { return new GlyphSource() {
                @Override public BakedGlyph getGlyph(int codepoint) { return glyph; }
                @Override public BakedGlyph getRandomGlyph(RandomSource random, int width) { return glyph; }
            }; }
            @Override public EffectGlyph effect() { return glyph; }
        });
        var context = new FeatureFrameContext(null, font, null, null, null, null, null, null);
        var text = new TextFeatureRenderer.Submit(new Matrix4f().translation(10, 20, 30), 0, 0,
                FormattedCharSequence.forward("AB", Style.EMPTY), false, Font.DisplayMode.NORMAL, 777, 0xff123456, 0, 0);
        var textBatches = ScopeNativeGeometry.capture(context, text, PREPARE);
        var textVertices = textBatches.getFirst().geometry().vertices();
        check(textVertices.size() == 8 && textVertices.stream().allMatch(p -> p.light() == 777 && p.color() == 0xff123456), "native text builder emits both glyphs with requested light and color");
        check(textVertices.stream().mapToDouble(VertexCapture.Vertex::x).min().orElseThrow() == 10
                && textVertices.stream().mapToDouble(VertexCapture.Vertex::x).max().orElseThrow() == 21, "native glyph advance and submitted text transform retained");
        var shadowText = new TextFeatureRenderer.Submit(new Matrix4f(), 0, 0, text.string(), true, Font.DisplayMode.NORMAL, 777, 0xff123456, 0, 0);
        var shadowVertices = ScopeNativeGeometry.capture(context, shadowText, PREPARE).getFirst().geometry().vertices();
        check(shadowVertices.size() == 16 && shadowVertices.stream().map(VertexCapture.Vertex::color).distinct().count() == 2, "native shadow foreground ordering and darker color retained");

        var material = new BakedQuad.MaterialInfo(null, null, TYPE, 0, false, 0);
        var quad = new BakedQuad(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0),
                0, 0, 0, 0, Direction.NORTH, material);
        var item = new ItemFeatureRenderer.Submit(pose.last().copy(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, 456, 654, 0,
                new int[]{0xff123456}, List.of(quad), ItemStackRenderState.FoilType.NONE);
        var itemVertices = ScopeNativeGeometry.capture(null, item, PREPARE).getFirst().geometry().vertices();
        check(itemVertices.size() == 4 && itemVertices.stream().allMatch(p -> p.color() == 0xff123456 && p.light() == 456 && p.overlay() == 654), "native item quad builder retains layer tint/light/overlay");
        BlockStateModelPart part = new BlockStateModelPart() {
            @Override public List<BakedQuad> getQuads(Direction direction) { return direction == null ? List.of(quad) : List.of(); }
            @Override public boolean useAmbientOcclusion() { return false; }
            @Override public Material.Baked particleMaterial() { return null; }
            @Override public int materialFlags() { return 0; }
        };
        var block = new BlockModelFeatureRenderer.Submit(pose.last().copy(), TYPE, List.of(part), new int[]{0xff123456}, 456, 654, -1, null);
        check(ScopeNativeGeometry.capture(null, block, PREPARE).getFirst().geometry().equals(new VertexCapture.Snapshot(itemVertices)), "native block model builder preserves the matching tinted quad");

        check(!ScopeCapture.capture(custom) && !ScopeCapture.active(), "ordinary native submissions pass through outside scope extraction");
        ScopeCapture capture = ScopeCapture.begin();
        try (capture) {
            ScopeCapture.capture(custom);
            ScopeCapture.withMask(ScopeMaskState.ocular(3), () -> {
                ScopeCapture.capture(modelNode);
                try { ScopeCapture.withMask(ScopeMaskState.aperture(3), () -> { throw new IllegalStateException("interrupt stage"); }); }
                catch (IllegalStateException expected) { assertions++; }
                ScopeCapture.capture(text);
            });
            ScopeCapture.capture(item);
            try { ScopeCapture.begin(); throw new AssertionError("nested owner should fail"); }
            catch (IllegalStateException expected) { assertions++; }
        }
        var commands = capture.plan().draws();
        check(commands.size() == 4 && commands.get(0).mask().equals(ScopeMaskState.UNMASKED)
                && commands.get(1).mask().equals(ScopeMaskState.ocular(3)) && commands.get(2).mask().equals(ScopeMaskState.ocular(3))
                && commands.get(3).mask().equals(ScopeMaskState.UNMASKED), "stage order and mask restoration survive nested failure");
        check(((ScopeRenderPlan.NativeDraw) commands.get(2)).submit() == text && !ScopeCapture.active(), "native text stays inside its mask stage and capture closes");
        check(!ScopeCapture.capture(custom), "later normal draw cannot leak into closed scope plan");
        capture.close(); check(capture.plan().draws().size() == 4, "close is idempotent");
        root.x = 16;
        var simple = new Model.Simple(root, ignored -> TYPE);
        ScopeCapture partCapture = ScopeCapture.begin();
        try (partCapture) {
            ScopeCapture.withMask(ScopeMaskState.color(ScopeMaskState.Comparison.EQUAL, 0, true), () ->
                    ScopeCapture.capture(new ModelFeatureRenderer.Submit<>(TYPE, pose.last().copy(), simple,
                            net.minecraft.util.Unit.INSTANCE, 1234, 4321, 0xff123456, null, null)));
        }
        root.x = 1000;
        var partDraw = (com.tacz.guns.client.renderer.scope.ScopeRenderPlan.Draw) partCapture.plan().draws().getFirst();
        check(partDraw.geometry().vertices().size() == 24 && partDraw.geometry().vertices().stream().allMatch(p -> p.x() >= .5F && p.x() <= 1.5F),
                "native submitModelPart arm geometry freezes before later shared-model pose changes");
        check(partDraw.geometry().vertices().stream().allMatch(p -> p.color() == 0xff123456 && p.light() == 1234 && p.overlay() == 4321)
                && partDraw.mask().equals(ScopeMaskState.color(ScopeMaskState.Comparison.EQUAL, 0, true)), "frozen arm retains its gun mask, tint, light and overlay");
        sequences();
        System.out.println("Scope native checks passed: " + assertions + " assertions (native builders and explicit fixtures; no FML/atlas/model assets)");
    }

    private record Step(String part, ScopeMaskState mask) {}
    private static Step plain(String part) { return new Step(part, ScopeMaskState.UNMASKED); }
    private static Step color(String part, int reference, boolean depth) {
        return new Step(part, ScopeMaskState.color(ScopeMaskState.Comparison.EQUAL, reference, depth));
    }
    private static Step write(int index) { return new Step("ocular" + index, ScopeMaskState.ocular(index + 1)); }
    private static Step aperture(int index) { return new Step("aperture" + index, ScopeMaskState.aperture(index + 1)); }

    private static List<Step> sequence(com.tacz.guns.client.renderer.scope.ScopeSequence.Mode mode, int divisions, boolean... scope) {
        var names = new ArrayList<String>();
        ScopeCapture capture = ScopeCapture.begin();
        try (capture) {
            com.tacz.guns.client.renderer.scope.ScopeSequence.render(mode, new com.tacz.guns.client.renderer.scope.ScopeSequence.Parts() {
                private void emit(String name) {
                    names.add(name);
                    ScopeCapture.capture(new CustomFeatureRenderer.Submit(new PoseStack().last().copy(), TYPE, (p, v) -> {}));
                }
                @Override public int ocularCount() { return scope.length; }
                @Override public int divisionCount() { return divisions; }
                @Override public boolean scopeOcular(int index) { return scope[index]; }
                @Override public void ring() { emit("ring"); }
                @Override public void body() { emit("body"); }
                @Override public void ocular(int index) { emit("ocular" + index); }
                @Override public void division(int index) { emit("division" + index); }
                @Override public void aperture(int index) { emit("aperture" + index); }
                @Override public void remaining() { emit("remaining"); }
            });
        }
        var result = new ArrayList<Step>();
        for (int i = 0; i < names.size(); i++) result.add(new Step(names.get(i), capture.plan().draws().get(i).mask()));
        return List.copyOf(result);
    }

    private static void sequences() {
        var sight = com.tacz.guns.client.renderer.scope.ScopeSequence.Mode.SIGHT;
        var scope = com.tacz.guns.client.renderer.scope.ScopeSequence.Mode.SCOPE;
        var mixed = com.tacz.guns.client.renderer.scope.ScopeSequence.Mode.MIXED;
        check(sequence(sight, 3, false, false).equals(List.of(write(1), write(0), color("division0", 1, false),
                color("division1", 2, false), color("division2", 3, false), plain("body"), plain("remaining"))),
                "sight preserves reverse ocular writes, every depth-disabled division and final unmasked body");
        check(sequence(scope, 3, false, false).equals(List.of(plain("ring"), write(1), write(0), color("body", 0, true),
                aperture(0), aperture(1), color("ocular0", 1, true), color("division0", 254, true),
                color("ocular1", 2, true), color("division1", 253, true), plain("remaining"))),
                "scope preserves ring/body ordering and pairs only matching ocular/division paths after inversion");
        check(sequence(mixed, 3, false, true, false).equals(List.of(plain("ring"), write(1), color("body", 0, true),
                write(2), write(0), aperture(1), color("division0", 1, true), color("ocular1", 2, true),
                color("division1", 253, true), color("division2", 3, true), plain("remaining"))),
                "mixed scope partitions both ocular writes around the body and inverts only scope oculars");
        check(sequence(scope, 0).equals(List.of(plain("ring"), color("body", 0, true), plain("remaining"))),
                "missing ocular/reticle paths retain available body geometry");
        check(sequence(scope, 128, new boolean[128]).contains(color("division127", 127, true)), "source index 127 remains valid");
        try { sequence(scope, 129, new boolean[129]); throw new AssertionError("expected source division bound"); }
        catch (IllegalArgumentException expected) { assertions++; }
        check(!ScopeCapture.active(), "rejected optic path count closes capture without leaking mask state");
    }
}
