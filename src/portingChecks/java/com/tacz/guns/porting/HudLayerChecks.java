package com.tacz.guns.porting;

import com.tacz.guns.client.gui.overlay.GunHudLayers;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.*;

public final class HudLayerChecks {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        var hidden = new AtomicBoolean();
        var holding = new AtomicBoolean();
        var calls = new ArrayList<String>();
        var pre = new ForgeLayeredDraw(PRE_SLEEP_STACK)
                .add(CAMERA_OVERLAY, record(calls, "camera"))
                .add(CROSSHAIR, record(calls, "vanilla_crosshair"))
                .add(CHANGE_STRATUM, record(calls, "stratum"))
                .add(HOTBAR_AND_DECOS, record(calls, "hotbar"));
        var root = new ForgeLayeredDraw(VANILLA_ROOT)
                .add(PRE_SLEEP_STACK, pre, () -> !hidden.get())
                .add(SLEEP_OVERLAY, record(calls, "sleep"))
                .add(POST_SLEEP_STACK, new ForgeLayeredDraw(POST_SLEEP_STACK).add(CHAT_OVERLAY, record(calls, "chat")), () -> !hidden.get());
        GunHudLayers.register(root, hidden::get, holding::get, record(calls, "gun_crosshair"), record(calls, "interact"),
                record(calls, "gun"), record(calls, "heat"), record(calls, "kills"));
        root.resolveLayers(); // Test an isolated stack with the real target bake/extraction implementation.
        root.extract(null, null);
        check(calls.equals(List.of("camera", "vanilla_crosshair", "gun_crosshair", "interact", "stratum", "hotbar", "sleep", "chat", "gun", "heat", "kills")), "source ordering at crosshair and above all overlays");
        calls.clear(); holding.set(true); root.extract(null, null);
        check(!calls.contains("vanilla_crosshair") && calls.contains("gun_crosshair"), "holding-gun condition suppresses only native crosshair after layer bake");
        calls.clear(); hidden.set(true); root.extract(null, null);
        check(calls.equals(List.of("gun_crosshair", "interact", "sleep", "gun", "heat", "kills")), "source custom overlays still extract with F1 and no duplicate crosshair callback");
        calls.clear(); hidden.set(false); holding.set(false); root.extract(null, null);
        check(calls.indexOf("vanilla_crosshair") < calls.indexOf("gun_crosshair"), "conditions remain live after toggling F1 and gun state");

        var category = com.tacz.guns.client.input.GunKeyMappings.CATEGORY;
        check(category.id().toLanguageKey("key.category").equals("key.category.tacz.guns"), "native key category has stable namespace");
        try (var files = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/assets/tacz/lang"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
                check(json.get("key.category.tacz").equals(json.get("key.category.tacz.guns")), "source category label retained: " + file.getFileName());
            }
        }
        checkPreview();
        ClassNode hud = new ClassNode();
        try (var input = HudLayerChecks.class.getClassLoader().getResourceAsStream("net/minecraft/client/gui/Hud.class")) {
            new ClassReader(input).accept(hud, 0);
        }
        long hookCount = hud.methods.stream().filter(m -> m.name.equals("extractRenderState"))
                .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                .filter(n -> n instanceof MethodInsnNode call && call.owner.equals("net/minecraftforge/client/gui/overlay/ForgeLayeredDraw")
                        && call.name.equals("extractRenderState") && call.desc.equals("(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")).count();
        check(hookCount == 1, "exact target HUD suppression hook occurs once");
        System.out.println("HUD layer checks passed: " + assertions + " assertions (native layer registry/bytecode; no GUI, font, textures or FML)");
    }

    private static void checkPreview() {
        var item = new net.minecraft.client.renderer.item.ItemStackRenderState();
        var guiPose = new org.joml.Matrix3x2f().translate(12, 15);
        var clip = new net.minecraft.client.gui.navigation.ScreenRectangle(110, 55, 110, 80);
        var state = new com.tacz.guns.client.gui.GunPreviewRenderState(item, 100, 40, 70, 2000, guiPose, clip);
        check(state.x0() == 103 && state.y0() == 56 && state.x1() == 231 && state.y1() == 155, "source 128 by 99 preview viewport");
        check(state.bounds().equals(new net.minecraft.client.gui.navigation.ScreenRectangle(110, 56, 110, 79)), "preview inherits parent clip");
        check(state.rotation() == 90, "source eight-second rotation period");
        guiPose.identity();
        check(state.pose().m20() == 12 && state.pose().m21() == 15, "preview owns GUI transform after extraction");
        for (float scale : new float[] {10, 70, 200}) {
            var sample = new com.tacz.guns.client.gui.GunPreviewRenderState(item, 100, 40, scale, 2000, new org.joml.Matrix3x2f(), null);
            var target = new com.mojang.blaze3d.vertex.PoseStack();
            // Native PIP centers the viewport and scales (+,+,-). Its inverted-Y
            // orthographic projection and flipped blit UVs retain GUI coordinates.
            target.translate(sample.x0() + 64, sample.y0() + 49.5F, 0);
            target.scale(scale, scale, -scale);
            sample.applyModelPose(target);
            var expected = new org.joml.Matrix4f().translate(168, 98, 0).scale(scale, -scale, scale)
                    .rotateX((float)Math.toRadians(15)).rotateY((float)Math.toRadians(90));
            check(target.last().pose().equals(expected, .0001F), "source preview model origin/rotation at scale " + scale);
        }
        var wrapped = new com.tacz.guns.client.gui.GunPreviewRenderState(item, 0, 0, 70, 8000, new org.joml.Matrix3x2f(), null);
        check(wrapped.rotation() == 0, "rotation wraps without accumulating frame drift");
    }

    private static ForgeLayer record(List<String> calls, String name) { return (graphics, delta) -> calls.add(name); }
}
