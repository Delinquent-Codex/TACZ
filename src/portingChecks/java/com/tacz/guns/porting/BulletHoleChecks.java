package com.tacz.guns.porting;

import com.tacz.guns.client.particle.BulletHoleVisual;
import com.tacz.guns.client.renderer.VertexCapture;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

import java.util.List;

public final class BulletHoleChecks {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        RegistryFixture.bootstrap();
        Vector3f[] source = {new Vector3f(-1, .01F, -1), new Vector3f(-1, .01F, 1),
                new Vector3f(1, .01F, 1), new Vector3f(1, .01F, -1)};
        float[][] uv = {{.4F, .8F}, {.4F, .6F}, {.2F, .6F}, {.2F, .8F}};
        for (Direction direction : Direction.values()) {
            var quads = new QuadParticleRenderState();
            BulletHoleVisual.extract(quads, direction, 10, 20, 30, .05F, .2F, .4F, .6F, .8F,
                    1, .5F, .25F, .9F, 0, 100, .98);
            check(quads.layers().equals(java.util.Set.of(SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN)), "terrain atlas and transparency retained");
            VertexCapture output = new VertexCapture();
            quads.buildLayer(SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN, output);
            var vertices = output.drain().vertices();
            check(vertices.size() == 4, "one complete native quad for " + direction);
            for (int i = 0; i < 4; i++) {
                Vector3f expected = new Vector3f(source[i]).rotate(direction.getRotation()).mul(.05F).add(10, 20, 30);
                var actual = vertices.get(i);
                check(expected.distance(new Vector3f(actual.x(), actual.y(), actual.z())) < .00001F
                                && actual.u() == uv[i][0] && actual.v() == uv[i][1],
                        "baseline face position, z-fight offset, winding and UV " + direction + "/" + i);
            }
            check(vertices.stream().allMatch(v -> v.light() == LightCoordsUtil.FULL_BRIGHT
                    && v.color() == ARGB.color(229, 255, 127, 63)), "initial light and tint " + direction);
            quads.clear();
            check(quads.isEmpty(), "native particle state clears between frames");
        }
        check(BulletHoleVisual.fade(97, 100, .98) == 1 && BulletHoleVisual.fade(98, 100, .98) == 1,
                "default fade begins after threshold");
        check(BulletHoleVisual.fade(99, 100, .98) == .5F && BulletHoleVisual.fade(100, 100, .98) == 0, "default final fade and expiry");
        check(BulletHoleVisual.fade(50, 100, 0) == .5F, "zero threshold fades across whole lifetime");
        check(BulletHoleVisual.fade(99, 100, 1) == 1 && BulletHoleVisual.fade(100, 100, 1) == 0,
                "threshold one avoids NaN and stays opaque until expiry");
        check(BulletHoleVisual.fade(0, 0, 1) == 0 && BulletHoleVisual.fade(0, -1, 0) == 0, "disabled lifetime emits transparent values");
        check(sample(2).getFirst().light() == LightCoordsUtil.pack(14, 14), "brightness decays every two ticks");
        check(ARGB.red(sample(29).getFirst().color()) == 17, "last glowing step uses one fifteenth tint");
        check(sample(30).getFirst().light() == 0 && (sample(30).getFirst().color() & 0xFFFFFF) == 0,
                "cooled mark is black after thirty ticks");
        check(ARGB.alpha(sample(99).getFirst().color()) == 114, "color packing retains source alpha truncation");
        System.out.println("Bullet-hole checks passed: " + assertions + " assertions (real target quad extraction; no particle engine, world or GPU).");
    }

    private static List<VertexCapture.Vertex> sample(int age) {
        var quads = new QuadParticleRenderState();
        BulletHoleVisual.extract(quads, Direction.UP, 0, 0, 0, .05F, 0, 1, 0, 1, 1, .5F, .25F, .9F, age, 100, .98);
        VertexCapture output = new VertexCapture();
        quads.buildLayer(SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN, output);
        return output.drain().vertices();
    }
}
