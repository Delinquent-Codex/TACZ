package com.tacz.guns.client.renderer;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

public final class TaczRenderTypes {
    private TaczRenderTypes() {}

    // The target's built-in culled variant selects ITEM_ENTITY_TARGET. World bullets/heads
    // retain the baseline main target, with the target's native culled translucent pipeline.
    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(texture ->
            RenderType.create("tacz_entity_translucent_cull", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                    .withTexture("Sampler0", texture).useLightmap().useOverlay().affectsCrumbling().sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));

    public static RenderType entityTranslucentCull(Identifier texture) { return ENTITY_TRANSLUCENT_CULL.apply(texture); }
}
