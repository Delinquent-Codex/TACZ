package com.tacz.guns.porting.scopefixture;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.client.renderer.scope.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.fml.ModList;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/** Real Forge client lifecycle and transformed production methods; no TACZ entry point or companion mods. */
@Mod("tacz_scope_fixture")
public final class ScopeFixture {
    private int assertions;
    private boolean ran;
    private String previousState;
    private String worldId;

    public ScopeFixture() { TickEvent.RenderTickEvent.Post.BUS.addListener(this::tick); }

    private void tick(TickEvent.RenderTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        String state = "screen=" + minecraft.gui.screen() + ", overlay=" + minecraft.gui.overlay();
        if (!state.equals(previousState)) { System.out.println("SCOPE_FML_WAIT " + state); previousState = state; }
        if (ran || minecraft.gui.overlay() != null) return;
        if (worldId == null) {
            // Item defaults are bound by the real world data load in 26.2. A menu-only
            // fixture cannot construct real default stacks; never patch/bind them by hand.
            worldId = "scope-fixture-" + java.util.UUID.randomUUID();
            var parent = minecraft.gui.screen();
            minecraft.createWorldOpenFlows().createFreshLevel(worldId,
                    new LevelSettings("Scope fixture", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(262L, false, false), WorldPresets::createTestWorldDimensions, parent);
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) return;
        ran = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("fixture", "production scope renderer and three Mixins; NOT full TACZ");
        result.put("minecraft", "26.2");
        result.put("forge", "65.1.0");
        result.put("world", worldId);
        result.put("java", System.getProperty("java.runtime.version"));
        result.put("device", RenderSystem.getDevice().getDeviceInfo().toString());
        result.put("backend", RenderSystem.getDevice().getDeviceInfo().backendName());
        result.put("mods", ModList.getMods().stream().map(mod -> mod.getModId() + "@" + mod.getVersion()).toList());
        result.put("screen", minecraft.gui.screen() == null ? null : minecraft.gui.screen().getClass().getName());
        try {
            verify(minecraft);
            result.put("status", "passed");
        } catch (Throwable failure) {
            result.put("status", "failed");
            result.put("failure", failure.toString());
            failure.printStackTrace();
        } finally {
            result.put("assertions", assertions);
            try {
                Files.writeString(Path.of("scope-fixture-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            } catch (Exception failure) { throw new IllegalStateException("Cannot record fixture result", failure); }
            System.out.println("SCOPE_FML_RESULT " + result);
            minecraft.stop();
        }
    }

    private void verify(Minecraft minecraft) throws Exception {
        var access = RenderTypeFeatureRenderer.class.getDeclaredMethod("getVertexBuilder", net.minecraft.client.renderer.rendertype.RenderType.class);
        check(Modifier.isProtected(access.getModifiers()) && !Modifier.isFinal(access.getModifiers()), "real Forge AT removes final and retains protected");
        var dispatcher = minecraft.gameRenderer.featureRenderDispatcher();
        var field = FeatureRenderDispatcher.class.getDeclaredField("featureRenderers");
        field.setAccessible(true);
        var renderers = (FeatureRendererMap) field.get(dispatcher);
        check(renderers.getOrThrow(ScopeFeatureRenderer.TYPE) instanceof ScopeFeatureRenderer, "constructor Mixin installs production scope renderer in actual game dispatcher");

        var solid = RenderTypes.entityCutout(Identifier.withDefaultNamespace("textures/entity/pig/pig_temperate.png"));
        var translucent = RenderTypes.entityTranslucent(Identifier.withDefaultNamespace("textures/entity/pig/pig_temperate.png"));
        var pose = new PoseStack();
        var storage = new SubmitNodeStorage();
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("panel", CubeListBuilder.create().addBox(0, 0, 0, 16, 16, 0), PartPose.ZERO);
        var part = LayerDefinition.create(mesh, 16, 16).bakeRoot();

        storage.submitCustomGeometry(pose, solid, (p, out) -> {});
        storage.submitModelPart(part, pose, translucent, 15728880, 0, null);
        var passthrough = drain(storage);
        check(passthrough.size() == 2 && passthrough.stream().anyMatch(CustomFeatureRenderer.Submit.class::isInstance)
                && passthrough.stream().anyMatch(ModelFeatureRenderer.Submit.class::isInstance), "both transformed phase methods pass through outside capture");

        ScopeCapture capture = ScopeCapture.begin();
        try (capture) {
            storage.submitCustomGeometry(pose, solid, (p, out) -> {});
            ScopeCapture.withMask(ScopeMaskState.ocular(1), () -> storage.submitModelPart(part, pose, translucent, 15728880, 0, null));
            check(drain(storage).isEmpty(), "simple and translucent submissions are cancelled in native phases during capture");
        }
        check(!ScopeCapture.active(), "capture closes on the client thread");
        var draws = capture.plan().draws();
        check(draws.size() == 2 && draws.getFirst() instanceof ScopeRenderPlan.NativeDraw
                && draws.getLast() instanceof ScopeRenderPlan.Draw, "native submit order and Model.Simple extraction-time freeze survive real Mixins");
        check(draws.getFirst().mask().equals(ScopeMaskState.UNMASKED) && draws.getLast().mask().equals(ScopeMaskState.ocular(1)), "captured masks match submission stages");
        part.x = 1000;
        check(((ScopeRenderPlan.Draw) draws.getLast()).geometry().vertices().stream().allMatch(v -> v.x() <= 1), "captured model vertices do not follow later bone mutation");

        ScopeFeatureRenderer.submit(storage, 7, capture.plan());
        var jobs = drain(storage);
        check(jobs.size() == 1 && jobs.getFirst() instanceof ScopeFeatureRenderer.Submit job && job.plan() == capture.plan(), "complete scope enters native phase once as an atomic job");

        ScopeCapture.submit(storage, 3, () -> {
            storage.submitCustomGeometry(pose, solid, (p, out) -> {});
            ScopeCapture.withMask(ScopeMaskState.ocular(4), () -> ScopeCapture.submit(storage, 9, () -> {
                check(ScopeCapture.active(), "nested source bridge reuses outer capture");
                storage.submitCustomGeometry(pose, translucent, (p, out) -> {});
            }));
            check(drain(storage).isEmpty(), "nested source bridge does not publish a partial scope job");
            storage.submitCustomGeometry(pose, solid, (p, out) -> {});
        });
        var bridged = drain(storage);
        check(bridged.size() == 1 && bridged.getFirst() instanceof ScopeFeatureRenderer.Submit,
                "outer source bridge submits the complete job exactly once");
        var bridgedDraws = ((ScopeFeatureRenderer.Submit) bridged.getFirst()).plan().draws();
        check(bridgedDraws.size() == 3 && bridgedDraws.get(0).mask().equals(ScopeMaskState.UNMASKED)
                        && bridgedDraws.get(1).mask().equals(ScopeMaskState.ocular(4))
                        && bridgedDraws.get(2).mask().equals(ScopeMaskState.UNMASKED),
                "nested bridge inherits its parent mask and preserves surrounding order");
        check(!ScopeCapture.active(), "source bridge releases capture after success");
        try {
            ScopeCapture.submit(storage, 0, () -> {
                storage.submitCustomGeometry(pose, solid, (p, out) -> {});
                throw new IllegalStateException("bridge failure");
            });
            throw new AssertionError("source bridge swallowed extraction failure");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("bridge failure"), "source bridge propagates extraction failure");
        }
        check(drain(storage).isEmpty() && !ScopeCapture.active(), "failed source bridge publishes no partial job and releases capture");
        ScopeCapture.submit(storage, 0, () -> storage.submitCustomGeometry(pose, solid, (p, out) -> {}));
        check(drain(storage).size() == 1, "source bridge can submit again after failure");

        try (var failed = ScopeCapture.begin()) {
            ScopeCapture.withMask(ScopeMaskState.ocular(2), () -> { throw new IllegalStateException("fixture failure"); });
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("fixture failure"), "fixture exercises exceptional cleanup");
        }
        check(!ScopeCapture.active(), "exception restores capture ownership");
        storage.submitCustomGeometry(pose, solid, (p, out) -> {});
        check(drain(storage).size() == 1, "failed capture does not swallow later vanilla submissions");

        // Preparing empty geometry still drives the actual dispatcher grouping and production
        // native builders, without modifying the visible framebuffer or borrowing fake services.
        try (var empty = ScopeCapture.begin()) {
            storage.submitCustomGeometry(pose, solid, (p, out) -> {});
            empty.close();
            ScopeFeatureRenderer.submit(storage, 0, empty.plan());
        }
        dispatcher.renderAllFeatures(storage);
        check(drain(storage).isEmpty(), "real dispatcher prepares, executes and drains an injected scope job");
        dispatcher.renderAllFeatures(new SubmitNodeStorage());
        check(!ScopeCapture.active(), "following empty frame finishes without retained capture state");
        verifyAssets(minecraft);
        LiveScopeDrawChecks.verify(minecraft, this::check);
    }

    private void verifyAssets(Minecraft minecraft) {
        var pose = new PoseStack();
        // These services are supplied by the running client after its real resource reload.
        var context = new FeatureFrameContext(null, minecraft.font, null, null, null, null, null, null);
        var storage = new SubmitNodeStorage();
        ScopeCapture capture = ScopeCapture.begin();
        try (capture) {
            storage.submitText(pose, 0, 0, FormattedCharSequence.forward("TACZ", Style.EMPTY), true,
                    Font.DisplayMode.NORMAL, 15728880, -1, 0, 0);
        }
        check(drain(storage).isEmpty() && capture.plan().draws().size() == 1, "real font submission is intercepted once by the native phase Mixin");
        var text = (ScopeRenderPlan.NativeDraw) capture.plan().draws().getFirst();
        var glyphs = ScopeNativeGeometry.capture(context, text.submit(), RenderType::prepare);
        check(glyphs.stream().mapToInt(batch -> batch.geometry().vertices().size()).sum() == 32,
                "loaded vanilla font emits four glyphs and four shadow quads through the production bridge");

        for (var item : List.of(Items.DIAMOND_SWORD, Items.COMPASS)) {
            var stack = item.getDefaultInstance();
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            var state = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, null, null, 0);
            var itemCapture = ScopeCapture.begin();
            try (itemCapture) { state.submit(pose, storage, 15728880, 0, 0); }
            check(drain(storage).isEmpty() && !itemCapture.plan().draws().isEmpty(), "real baked " + item + " submits into scope capture");
            var itemSubmits = itemCapture.plan().draws().stream().map(ScopeRenderPlan.NativeDraw.class::cast)
                    .map(ScopeRenderPlan.NativeDraw::submit).map(ItemFeatureRenderer.Submit.class::cast).toList();
            var expectedFoil = item == Items.COMPASS ? ItemStackRenderState.FoilType.SPECIAL : ItemStackRenderState.FoilType.STANDARD;
            check(itemSubmits.stream().allMatch(submit -> submit.foilType() == expectedFoil && !submit.quads().isEmpty()),
                    "real item model resolves " + expectedFoil + " glint with nonempty baked quads");
            int quads = itemSubmits.stream().mapToInt(submit -> submit.quads().size()).sum();
            var batches = itemSubmits.stream().flatMap(submit -> ScopeNativeGeometry.capture(context, submit, RenderType::prepare).stream()).toList();
            int glint = batches.stream().filter(batch -> batch.type() == RenderTypes.glint() || batch.type() == RenderTypes.glintTranslucent())
                    .mapToInt(batch -> batch.geometry().vertices().size()).sum();
            int vertices = batches.stream().mapToInt(batch -> batch.geometry().vertices().size()).sum();
            check(glint == quads * 4 && vertices == quads * 8, "production native bridge retains every base and " + expectedFoil + " foil quad");
            check(batches.stream().flatMap(batch -> batch.geometry().vertices().stream()).allMatch(v -> Float.isFinite(v.u()) && Float.isFinite(v.v())),
                    "real " + expectedFoil + " decal/atlas UV values stay finite");
        }
    }

    private static List<SubmitNode> drain(SubmitNodeStorage storage) {
        var result = new ArrayList<SubmitNode>();
        storage.drainPhases(phase -> phase.sortInto(new FeatureRenderPhase.Output() {
            @Override public void accept(SubmitNode submit, boolean strictlyOrdered) { result.add(submit); }
            @Override public <S extends SubmitNode> void acceptFeatureGroup(FeatureRendererType<S> type, Collection<S> submits, boolean strictlyOrdered) { result.addAll(submits); }
        }));
        return result;
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
        System.out.println("SCOPE_FML_CHECK " + message);
    }
}
