package com.tacz.guns.client.renderer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

public final class TaczRenderTypes {
    private TaczRenderTypes() {}

    public static final Identifier LASER_BEAM_TEXTURE = Identifier.fromNamespaceAndPath("tacz", "textures/entity/beam.png");
    private static final RenderPipeline LASER_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("tacz", "pipeline/laser"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION).withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader("core/position_tex_color").withFragmentShader(Identifier.fromNamespaceAndPath("tacz", "core/laser"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT).withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
            .withCull(false).build();
    private static final RenderPipeline LASER_ENTITY_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("tacz", "pipeline/laser_entity"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1).withShaderDefine("ALPHA_CUTOUT", .1F)
            .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY)).withCull(false).build();
    private static final RenderType LASER = RenderType.create("tacz_laser", laserSetup(LASER_PIPELINE).createRenderSetup());
    private static final RenderType LASER_ENTITY = RenderType.create("tacz_laser_entity", laserSetup(LASER_ENTITY_PIPELINE)
            .useLightmap().useOverlay().createRenderSetup());
    private static final RenderPipeline SCOPE_APERTURE_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("tacz", "pipeline/scope_aperture"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT).withCull(false).build();
    private static final RenderType SCOPE_APERTURE = RenderType.create("tacz_scope_aperture", RenderSetup.builder(SCOPE_APERTURE_PIPELINE).createRenderSetup());
    private static final RenderPipeline TEXTURED_BLIT_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("tacz", "pipeline/textured_blit"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION).withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT).withCull(false).build();
    private static final Function<Identifier, RenderType> TEXTURED_BLIT = Util.memoize(texture ->
            RenderType.create("tacz_textured_blit", RenderSetup.builder(TEXTURED_BLIT_PIPELINE)
                    .withTexture("Sampler0", texture).createRenderSetup()));

    private static RenderSetup.RenderSetupBuilder laserSetup(RenderPipeline pipeline) {
        return RenderSetup.builder(pipeline).withTexture("Sampler0", LASER_BEAM_TEXTURE).affectsCrumbling().sortOnUpload()
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET);
    }

    /** Called before initial shader preparation; the same pipelines are recompiled on every resource reload. */
    public static void registerPipelines() {
        RenderPipelines.PIPELINES_BY_LOCATION.put(LASER_PIPELINE.getLocation(), LASER_PIPELINE);
        RenderPipelines.PIPELINES_BY_LOCATION.put(LASER_ENTITY_PIPELINE.getLocation(), LASER_ENTITY_PIPELINE);
        RenderPipelines.PIPELINES_BY_LOCATION.put(SCOPE_APERTURE_PIPELINE.getLocation(), SCOPE_APERTURE_PIPELINE);
        RenderPipelines.PIPELINES_BY_LOCATION.put(TEXTURED_BLIT_PIPELINE.getLocation(), TEXTURED_BLIT_PIPELINE);
    }

    public static RenderType laserBeam() { return LASER; }
    public static RenderType laserBeamEntity() { return LASER_ENTITY; }
    public static RenderType scopeAperture() { return SCOPE_APERTURE; }
    public static RenderType texturedBlit(Identifier texture) { return TEXTURED_BLIT.apply(texture); }

    // The target's built-in culled variant selects ITEM_ENTITY_TARGET. World bullets/heads
    // retain the baseline main target, with the target's native culled translucent pipeline.
    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(texture ->
            RenderType.create("tacz_entity_translucent_cull", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                    .withTexture("Sampler0", texture).useLightmap().useOverlay().affectsCrumbling().sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));

    public static RenderType entityTranslucentCull(Identifier texture) { return ENTITY_TRANSLUCENT_CULL.apply(texture); }
}
