package com.tacz.guns.porting;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.ar.ARCompatImpl;

/** Type-boundary regression against the real old AR interface; does not load that mod in FML. */
public final class ARConsumerChecks {
    private static int assertions;
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) throws Exception {
        com.mojang.blaze3d.platform.NativeLibrariesBootstrap.loadLibraries();
        boolean previous = ARCompat.LOADED;
        try (var memory = new ByteBufferBuilder(256)) {
            var captured = new VertexCapture();
            var nativeBuffer = new BufferBuilder(memory, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            if (java.util.List.of(args).contains("--absent")) {
                try {
                    Class.forName("com.github.argon4w.acceleratedrendering.core.CoreFeature", false, ARConsumerChecks.class.getClassLoader());
                    throw new AssertionError("Absent-companion test accidentally includes the AR artifact");
                } catch (ClassNotFoundException expected) { assertions++; }
                check(!ARCompat.LOADED, "default optional state is absent");
                check(!ARCompat.shouldAccelerate(), "missing artifact cannot activate acceleration");
                check(!ARCompat.isAccelerated(captured) && !ARCompat.isAccelerated(nativeBuffer), "missing artifact does not resolve implementation for either native consumer");
                ARCompat.setRenderingLevel(); ARCompat.resetRenderingLevel();
                ARCompat.setRenderLayer(2); ARCompat.resetRenderLayer();
                ARCompat.setRenderBeforeFunction(() -> { throw new AssertionError("absent callback ran"); });
                ARCompat.resetRenderBeforeFunction();
                ARCompat.setRenderAfterFunction(() -> { throw new AssertionError("absent callback ran"); });
                ARCompat.resetRenderAfterFunction();
                ARCompat.disableAcceleration(); ARCompat.resetAcceleration();
                ARCompat.renderLaser(captured, 1, 1, false, new com.mojang.blaze3d.vertex.PoseStack(), -1);
                check(captured.drain().vertices().isEmpty(), "absent optional operations neither link companion classes nor emit geometry");
                System.out.println("AR absent-artifact checks passed: " + assertions + " assertions (no companion JAR on classpath; no FML)");
                return;
            }
            ARCompat.LOADED = false;
            check(!ARCompat.isAccelerated(captured), "absent AR accepts a TACZ capture consumer without loading AR implementation");
            check(!ARCompat.isAccelerated(nativeBuffer), "absent AR accepts the native builder");
            // Presence alone never establishes that a consumer implements the companion API.
            // This deliberately toggles only TACZ's flag; it is not a simulated working AR mod.
            ARCompat.LOADED = true;
            check(!ARCompat.isAccelerated(captured), "present flag cannot hard-cast a TACZ capture consumer");
            check(!ARCompat.isAccelerated(nativeBuffer), "present flag cannot hard-cast a native target builder");
            check(!ARCompatImpl.isAccelerated(captured), "implementation checks the real companion interface");
            captured.addVertex(1, 2, 3).setColor(0xff123456);
            var snapshot = captured.drain();
            check(snapshot.vertices().size() == 1 && snapshot.vertices().getFirst().color() == 0xff123456,
                    "capability inspection leaves captured geometry intact");
        } finally {
            ARCompat.LOADED = previous;
        }
        System.out.println("AR consumer boundary checks passed: " + assertions + " assertions (legacy interface only; no companion runtime)");
    }
}
