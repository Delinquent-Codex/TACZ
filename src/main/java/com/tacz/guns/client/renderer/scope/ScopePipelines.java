package com.tacz.guns.client.renderer.scope;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** Copies native pipeline descriptions, including arbitrary string shader defines and vertex bindings. */
public final class ScopePipelines {
    private ScopePipelines() {}
    private static final BindGroupLayout MASK = BindGroupLayout.builder().withSampler("TaczScopeMask")
            .withUniform("TaczScopeControl", UniformType.UNIFORM_BUFFER).build();

    public static RenderPipeline create(RenderPipeline original, boolean writesMask, boolean depthTest) {
        var colors = original.getColorTargetStates().clone();
        // Scope geometry currently has one fragment color output. Reject an incompatible extension
        // explicitly instead of losing its additional attachments or changing its blend behavior.
        for (int i = 1; i < colors.length; i++) {
            if (colors[i] != null) throw new IllegalArgumentException("Scope pipeline has multiple color outputs: " + original.getLocation());
        }
        if (writesMask) colors[0] = ColorTargetState.DEFAULT;
        DepthStencilState depth = original.getDepthStencilState();
        if (!depthTest) depth = null;
        else if (depth != null && writesMask) {
            depth = new DepthStencilState(depth.depthTest(), false, depth.depthBiasScaleFactor(), depth.depthBiasConstant());
        }
        var snippet = new RenderPipeline.Snippet(Optional.of(original.getVertexShader()), Optional.of(original.getFragmentShader()),
                Optional.of(original.getShaderDefines()), Optional.of(original.getBindGroupLayouts()), colors, 1,
                Optional.ofNullable(depth), Optional.of(original.getPolygonMode()), Optional.of(original.isCull()),
                original.getVertexFormatBindings().clone(), Optional.of(original.getPrimitiveTopology()));
        Identifier location = original.getLocation();
        return RenderPipeline.builder(snippet).withBindGroupLayout(MASK)
                .withLocation(Identifier.fromNamespaceAndPath("tacz", "scope/" + location.getNamespace() + "/" + location.getPath()
                        + (writesMask ? "/mask" : "/color") + (depthTest ? "/depth" : "/no_depth")))
                .withFragmentShader(Identifier.fromNamespaceAndPath("tacz", "scope/" + original.getFragmentShader().getNamespace()
                        + "/" + original.getFragmentShader().getPath())).build();
    }
}
