package com.tacz.guns.porting.scopefixture;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.scope.ScopeCapture;
import com.tacz.guns.client.renderer.scope.ScopeFeatureRenderer;
import com.tacz.guns.client.renderer.scope.ScopeMaskState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Pixel comparisons using the live client's shader manager, textures, font, item models and dispatcher. */
final class LiveScopeDrawChecks {
    static void verify(Minecraft minecraft, BiConsumer<Boolean, String> check) {
        var oldColor = RenderSystem.outputColorTextureOverride;
        var oldDepth = RenderSystem.outputDepthTextureOverride;
        var oldProjection = RenderSystem.getProjectionMatrixBuffer();
        var oldProjectionType = RenderSystem.getProjectionType();
        var target = new TextureTarget("scope live asset fixture", 128, 128, true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
        var projection = new ProjectionMatrixBuffer("scope live asset fixture");
        RenderSystem.getModelViewStack().pushMatrix().identity();
        try {
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
            RenderSystem.setProjectionMatrix(projection.getBuffer(new Matrix4f().setOrtho(-2, 2, -2, 2, -10, 10, true)), ProjectionType.ORTHOGRAPHIC);

            compare(minecraft, target, "vanilla font with shadows", storage -> {
                var pose = new PoseStack();
                pose.translate(-1.3F, .8F, 0);
                pose.scale(.08F, -.08F, .08F);
                storage.submitText(pose, 0, 0, FormattedCharSequence.forward("TACZ", Style.EMPTY), true,
                        Font.DisplayMode.NORMAL, 15728880, -1, 0, 0);
            }, false, check);
            compare(minecraft, target, "vanilla textured cutout", storage -> {
                var type = RenderTypes.entityCutout(Identifier.withDefaultNamespace("textures/entity/pig/pig_temperate.png"));
                storage.submitCustomGeometry(new PoseStack(), type, (pose, out) -> {
                    out.addVertex(-1, -1, 0).setColor(-1).setUv(0, 0).setOverlay(0).setLight(15728880).setNormal(0, 0, 1);
                    out.addVertex(1, -1, 0).setColor(-1).setUv(1, 0).setOverlay(0).setLight(15728880).setNormal(0, 0, 1);
                    out.addVertex(1, 1, 0).setColor(-1).setUv(1, 1).setOverlay(0).setLight(15728880).setNormal(0, 0, 1);
                    out.addVertex(-1, 1, 0).setColor(-1).setUv(0, 1).setOverlay(0).setLight(15728880).setNormal(0, 0, 1);
                });
            }, false, check);
            for (var item : java.util.List.of(Items.DIAMOND_SWORD, Items.COMPASS)) {
                var stack = item.getDefaultInstance();
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                var state = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, minecraft.level, minecraft.player, 0);
                compare(minecraft, target, "baked glint item " + item,
                        storage -> state.submit(new PoseStack(), storage, 15728880, 0, 0), true, check);
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
            RenderSystem.outputColorTextureOverride = oldColor;
            RenderSystem.outputDepthTextureOverride = oldDepth;
            RenderSystem.queueFencedTask(() -> { target.destroyBuffers(); projection.close(); });
        }
    }

    private static void compare(Minecraft minecraft, TextureTarget target, String name, Consumer<SubmitNodeStorage> scene,
                                boolean animatedGlint, BiConsumer<Boolean, String> check) {
        byte[] vanilla = draw(minecraft, target, scene, null);
        byte[] allowed = draw(minecraft, target, scene, List.of(ScopeMaskState.color(ScopeMaskState.Comparison.EQUAL, 0, true)));
        byte[] denied = draw(minecraft, target, scene, List.of(ScopeMaskState.color(ScopeMaskState.Comparison.EQUAL, 1, true)));
        System.out.println("SCOPE_LIVE_PIXELS " + name + " native=" + signature(vanilla) + " allowed=" + signature(allowed) + " denied=" + signature(denied));
        check.accept(nonzero(vanilla), "live GPU " + name + " produces pixels through native dispatcher");
        // Glint UV matrices depend on wall time; compare coverage, not time-varying RGB.
        check.accept(animatedGlint ? sameAlpha(vanilla, allowed) : Arrays.equals(vanilla, allowed),
                "live GPU " + name + " retains " + (animatedGlint ? "alpha coverage" : "every RGBA byte") + " through allowing scope mask");
        check.accept(!nonzero(denied), "live GPU " + name + " is fully clipped by rejecting scope mask");
        byte[] maskOnly = draw(minecraft, target, scene, List.of(ScopeMaskState.ocular(1)));
        check.accept(!nonzero(maskOnly), "live GPU " + name + " writes its mask without changing the color target");
        byte[] written = draw(minecraft, target, scene, List.of(ScopeMaskState.ocular(1), ScopeMaskState.color(ScopeMaskState.Comparison.EQUAL, 1, true)));
        check.accept(animatedGlint ? sameAlpha(vanilla, written) : Arrays.equals(vanilla, written),
                "live GPU " + name + " survives its own alpha-tested ocular mask");
        byte[] inverted = draw(minecraft, target, scene, List.of(ScopeMaskState.ocular(1), ScopeMaskState.aperture(1), ScopeMaskState.color(ScopeMaskState.Comparison.EQUAL, 254, true)));
        check.accept(animatedGlint ? sameAlpha(vanilla, inverted) : Arrays.equals(vanilla, inverted),
                "live GPU " + name + " survives mask inversion with native shader bindings");
    }

    private static byte[] draw(Minecraft minecraft, TextureTarget target, Consumer<SubmitNodeStorage> scene, List<ScopeMaskState> masks) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        // JOML's no-argument Vector4f has w=1; this comparison needs transparent black.
        encoder.clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(0, 0, 0, 0), target.getDepthTexture(), 0);
        encoder.submit();
        var storage = new SubmitNodeStorage();
        if (masks == null) scene.accept(storage);
        else {
            ScopeCapture.submit(storage, 0, () -> {
                for (var mask : masks) ScopeCapture.withMask(mask, () -> scene.accept(storage));
            });
        }
        minecraft.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage);
        var copy = RenderSystem.getDevice().createCommandEncoder();
        try (var readback = RenderSystem.getDevice().createBuffer(() -> "scope live asset readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, target.width * target.height * 4)) {
            copy.copyTextureToBuffer(target.getColorTexture(), readback, 0, () -> {}, 0);
            try (var fence = copy.createFence()) {
                copy.submit();
                if (!fence.awaitCompletion(5_000_000_000L)) throw new IllegalStateException("Live scope GPU readback timeout");
            }
            RenderSystem.executePendingTasks();
            byte[] result = new byte[target.width * target.height * 4];
            try (var mapped = readback.slice().map(true, false)) { mapped.data().get(result); }
            return result;
        }
    }

    private static boolean nonzero(byte[] pixels) {
        for (byte value : pixels) if (value != 0) return true;
        return false;
    }

    private static String signature(byte[] pixels) {
        int changed = 0;
        for (byte value : pixels) if (value != 0) changed++;
        return "nonzeroBytes:" + changed + ",hash:" + Arrays.hashCode(pixels);
    }

    private static boolean sameAlpha(byte[] a, byte[] b) {
        for (int i = 3; i < a.length; i += 4) if (a[i] != b[i]) return false;
        return true;
    }
}
