package com.tacz.guns.compat.oculus;

import com.tacz.guns.compat.oculus.legacy.OculusCompatLegacy;
import com.tacz.guns.compat.oculus.newly.OculusCompatNewly;
import com.tacz.guns.init.CompatRegistry;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.function.Supplier;

public final class OculusCompat {
    private static final DefaultArtifactVersion VERSION = new DefaultArtifactVersion("1.7.0");
    private static Supplier<Boolean> IS_RENDER_SHADOW_SUPPER;

    public static void initCompat() {
        ModList.getModContainerById(CompatRegistry.OCULUS).ifPresent(mod -> {
            if (mod.getModInfo().getVersion().compareTo(VERSION) >= 0) {
                IS_RENDER_SHADOW_SUPPER = OculusCompatNewly::isRenderShadow;
            } else {
                IS_RENDER_SHADOW_SUPPER = OculusCompatLegacy::isRenderShadow;
            }
        });
    }

    public static boolean isRenderShadow() {
        if (ModList.isLoaded(CompatRegistry.OCULUS)) {
            return IS_RENDER_SHADOW_SUPPER.get();
        }
        return false;
    }

    public static boolean isUsingRenderPack() {
        if (ModList.isLoaded(CompatRegistry.OCULUS)) {
            return IrisApi.getInstance().isShaderPackInUse();
        }
        return false;
    }

    // The old endBatch(BufferSource) API flushed Oculus buffers between immediate GL stencil
    // stages. 26.2 has no BufferSource. Optics now submit an ordered ScopeCapture job through
    // ScopeFeatureRenderer, which owns its native draw passes; there is no per-part flush here.
}
