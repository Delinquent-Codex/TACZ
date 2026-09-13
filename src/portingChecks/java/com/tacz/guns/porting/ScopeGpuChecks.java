package com.tacz.guns.porting;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.renderer.scope.ScopeFeatureRenderer;
import com.tacz.guns.client.renderer.scope.ScopeMaskState;
import com.tacz.guns.client.renderer.scope.ScopeRenderPlan;
import com.tacz.guns.client.renderer.scope.ScopeAperture;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.util.List;

import static com.tacz.guns.client.renderer.scope.ScopeMaskState.Comparison.*;

/** Real native GPU draw/readback fixtures. This does not launch Minecraft, FML or a gun model. */
public final class ScopeGpuChecks {
    private static int assertions;
    private static final Identifier VERTEX = Identifier.parse("tacz:scope_gpu_fixture_vertex");
    private static final Identifier FRAGMENT = Identifier.parse("tacz:scope_gpu_fixture_fragment");
    private static boolean reloadBlue;
    private static final String VERTEX_SOURCE = """
            #version 330 core
            in vec3 Position;
            in vec4 Color;
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
                mat4 TextureMat;
            };
            out vec4 vertexColor;
            void main() {
                gl_Position = ModelViewMat * vec4(Position, 1.0);
                vertexColor = Color * ColorModulator;
            }
            """;
    private static final ShaderSource SHADERS = (id, type) -> {
        if (id.equals(VERTEX) && type == ShaderType.VERTEX) return VERTEX_SOURCE;
        if (id.equals(FRAGMENT) && type == ShaderType.FRAGMENT) return """
                #version 330 core
                in vec4 vertexColor;
                out vec4 fragColor;
                void main() {
                    if (vertexColor.a < 0.1) discard;
                    fragColor = %s;
                }
                """.formatted(reloadBlue ? "vec4(0.0, 0.0, 1.0, 1.0)" : "vertexColor");
        return null;
    };

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        com.mojang.blaze3d.platform.NativeLibrariesBootstrap.loadLibraries();
        GpuBackend backend = List.of(args).contains("--vulkan") ? new VulkanBackend() : new GlBackend();
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err);
        GLFW.glfwSetErrorCallback(callback);
        long window = 0;
        boolean initialized = false;
        try {
            if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW initialization failed");
            GLFW.glfwDefaultWindowHints();
            backend.setWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            window = GLFW.glfwCreateWindow(64, 32, "TACZ scope offscreen validation", 0, 0);
            if (window == 0) throw new IllegalStateException("Could not create hidden " + backend.getName() + " window");
            RenderSystem.initRenderThread();
            var device = backend.createDevice(window, SHADERS, new GpuDebugOptions(3, true, true, false), () -> {});
            RenderSystem.initRenderer(device);
            initialized = true;
            System.out.println("Scope GPU device: " + device.getDeviceInfo());
            try (Fixture fixture = new Fixture()) {
                fixture.target.resize(64, 32);
                fixture.render(false);
                checkPixel(fixture, 8, 16, 0x00ff00ff, "sight reticle passes with depth disabled");
                checkPixel(fixture, 40, 16, 0xffffffff, "scope aperture exposes white division");
                checkPixel(fixture, 56, 16, 0x000000ff, "scope blackout remains outside aperture");
                fixture.render(true);
                checkPixel(fixture, 8, 16, 0xff0000ff, "gun renders over sight mask");
                checkPixel(fixture, 40, 16, 0xffffffff, "gun is clipped by inverted scope mask");
                checkPixel(fixture, 56, 16, 0xff0000ff, "gun outside scope aperture is retained");
                fixture.alphaAndDepth();
                checkPixel(fixture, 8, 16, 0x0000ffff, "transparent ocular preserves zero mask");
                checkPixel(fixture, 40, 16, 0x0000ffff, "depth-rejected ocular preserves zero mask");
                fixture.apertureFan();
                checkPixel(fixture, 32, 16, 0xffffffff, "production 90-segment aperture exposes its center");
                checkPixel(fixture, 60, 16, 0x000000ff, "production aperture leaves peripheral blackout");
                fixture.separateOutputs(false);
                checkPixel(fixture, 8, 16, 0x0000ffff, "main scope mask is independent of a preceding smaller unmasked output");
                checkPixel(fixture.alternate, 8, 8, 0xff0000ff, "unmasked native draw retains its smaller output target");
                fixture.separateOutputs(true);
                checkPixel(fixture.alternate, 8, 16, 0x00ff00ff, "matching pixel coordinates share the mask across distinct native output targets");
                checkPixel(fixture.alternate, 56, 16, 0xff0000ff, "masked alternate output preserves its own depth rejection");

                // Reuse the renderer and pipeline descriptions after resizing and clearing the
                // same native cache that ShaderManager clears during resource reload.
                fixture.target.resize(16, 16);
                fixture.render(false);
                checkPixel(fixture, 2, 8, 0x00ff00ff, "new target dimensions allocate a fresh mask");
                checkPixel(fixture, 10, 8, 0xffffffff, "aperture remains pixel-aligned after resize");
                device.clearPipelineCache();
                reloadBlue = true;
                fixture.render(false);
                checkPixel(fixture, 2, 8, 0x0000ffff, "pipeline recompilation uses replacement fragment source");
                reloadBlue = false;
                device.clearPipelineCache();
                fixture.target.resize(64, 32);
                fixture.render(false);
                checkPixel(fixture, 40, 16, 0xffffffff, "reload and second resize restore current shader output");
            }
            ScopeShaderChecks.run(device);
            System.out.println("Scope GPU checks passed: " + assertions + " assertions (native " + backend.getName() + " fixtures; no Minecraft/FML/gun visuals)");
        } finally {
            if (initialized) RenderSystem.shutdownRenderer();
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
            GLFW.glfwSetErrorCallback(null);
            callback.free();
        }
    }

    private static void checkPixel(Fixture fixture, int x, int y, int expected, String message) {
        checkPixel(fixture.target, x, y, expected, message);
    }

    private static void checkPixel(TextureTarget target, int x, int y, int expected, String message) {
        int value = Fixture.pixel(target, x, y);
        assertions++;
        if (value != expected) throw new AssertionError(message + ": expected " + Integer.toHexString(expected) + " got " + Integer.toHexString(value));
    }

    private static final class Fixture implements AutoCloseable {
        private final TextureTarget target = new TextureTarget("scope fixture", 64, 32, true, GpuFormat.RGBA8_UNORM);
        private final OutputTarget output = new OutputTarget("scope fixture", () -> target);
        private final TextureTarget alternate = new TextureTarget("scope alternate fixture", 16, 16, true, GpuFormat.RGBA8_UNORM);
        private final OutputTarget alternateOutput = new OutputTarget("scope alternate fixture", () -> alternate);
        private final RenderPipeline pipeline = RenderPipeline.builder().withLocation(Identifier.parse("tacz:scope_gpu_fixture"))
                .withVertexShader(VERTEX).withFragmentShader(FRAGMENT).withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT).withCull(false).build();
        private final RenderType type = RenderType.create("scope fixture", RenderSetup.builder(pipeline).setOutputTarget(output).createRenderSetup());
        private final RenderType alternateType = RenderType.create("scope alternate fixture", RenderSetup.builder(pipeline).setOutputTarget(alternateOutput).createRenderSetup());
        private final RenderPipeline fanPipeline = RenderPipeline.builder().withLocation(Identifier.parse("tacz:scope_gpu_fan"))
                .withVertexShader(VERTEX).withFragmentShader(FRAGMENT).withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withDepthStencilState(DepthStencilState.DEFAULT).withCull(false).build();
        private final RenderType fanType = RenderType.create("scope fan fixture", RenderSetup.builder(fanPipeline).setOutputTarget(output).createRenderSetup());
        private final StagedVertexBuffer staged = new StagedVertexBuffer(() -> "scope fixture", 65536);
        private final FeatureFrameContext context = new FeatureFrameContext(null, null, null, null, null, null, null, staged);
        private final ScopeFeatureRenderer renderer = new ScopeFeatureRenderer(SHADERS, renderType ->
                new PreparedRenderType(renderType.pipeline(), renderType.outputTarget(), RenderSystem.getDynamicUniforms().writeTransform(
                        renderType == fanType ? new Matrix4f().scaling(.01F, .01F, -.01F) : new Matrix4f()),
                        new ScissorState(), List.of()));

        void render(boolean gun) {
            var builder = new ScopeRenderPlan.Builder();
            rect(builder, -1, 1, .3F, 0xff0000ff, ScopeMaskState.UNMASKED);
            rect(builder, 0, 1, .5F, -1, ScopeMaskState.ocular(2));
            rect(builder, -1, .5F, .5F, -1, ScopeMaskState.ocular(1));
            rect(builder, 0, .5F, .5F, -1, ScopeMaskState.aperture(2));
            rect(builder, -1, 1, .5F, 0xff000000, ScopeMaskState.color(EQUAL, 2, true));
            rect(builder, -1, 1, .7F, -1, ScopeMaskState.color(EQUAL, 253, true));
            rect(builder, -1, 1, .1F, 0xff00ff00, ScopeMaskState.color(EQUAL, 1, false));
            if (gun) rect(builder, -1, 1, .8F, 0xffff0000, ScopeMaskState.color(GREATER, 127, true));
            execute(builder.build());
        }

        void alphaAndDepth() {
            var builder = new ScopeRenderPlan.Builder();
            rect(builder, -1, 1, .3F, 0xffff0000, ScopeMaskState.UNMASKED);
            rect(builder, -1, 0, .5F, 0x00000000, ScopeMaskState.ocular(1));
            rect(builder, 0, 1, .1F, -1, ScopeMaskState.ocular(2));
            rect(builder, -1, 1, .5F, 0xff0000ff, ScopeMaskState.color(EQUAL, 0, true));
            execute(builder.build());
        }

        void apertureFan() {
            var builder = new ScopeRenderPlan.Builder();
            rect(builder, -1, 1, .5F, -1, ScopeMaskState.ocular(1));
            builder.draw(fanType, ScopeAperture.capture(0, 0, 1, 1), ScopeMaskState.aperture(1));
            rect(builder, -1, 1, .5F, 0xff000000, ScopeMaskState.color(EQUAL, 1, true));
            rect(builder, -1, 1, .5F, -1, ScopeMaskState.color(EQUAL, 254, true));
            execute(builder.build());
        }

        void rect(ScopeRenderPlan.Builder builder, float left, float right, float z, int argb, ScopeMaskState mask) {
            rect(builder, type, left, right, z, argb, mask);
        }

        void separateOutputs(boolean masked) {
            var builder = new ScopeRenderPlan.Builder();
            if (masked) alternate.resize(64, 32);
            rect(builder, alternateType, -1, 1, .5F, 0xffff0000, ScopeMaskState.UNMASKED);
            rect(builder, -1, 1, .5F, -1, ScopeMaskState.ocular(1));
            rect(builder, -1, 1, .6F, 0xff0000ff, ScopeMaskState.color(EQUAL, 1, true));
            if (masked) {
                rect(builder, alternateType, -1, 0, .6F, 0xff00ff00, ScopeMaskState.color(EQUAL, 1, true));
                rect(builder, alternateType, 0, 1, .3F, 0xff00ff00, ScopeMaskState.color(EQUAL, 1, true));
            }
            execute(builder.build());
        }

        void rect(ScopeRenderPlan.Builder builder, RenderType drawType, float left, float right, float z, int argb, ScopeMaskState mask) {
            var capture = new VertexCapture();
            capture.addVertex(left, -1, z).setColor(argb);
            capture.addVertex(right, -1, z).setColor(argb);
            capture.addVertex(right, 1, z).setColor(argb);
            capture.addVertex(left, 1, z).setColor(argb);
            // Exercise the actual target CustomFeatureRenderer geometry builder through the
            // production native-feature bridge, with the access-transformed target class.
            builder.nativeDraw(new net.minecraft.client.renderer.feature.CustomFeatureRenderer.Submit(
                    new com.mojang.blaze3d.vertex.PoseStack().last().copy(), drawType, capture.drain()), mask);
        }

        void execute(ScopeRenderPlan plan) {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(), target.getDepthTexture(), 0.25);
            encoder.clearColorAndDepthTextures(alternate.getColorTexture(), new Vector4f(), alternate.getDepthTexture(), 0.25);
            var submits = List.of(new ScopeFeatureRenderer.Submit(plan));
            renderer.prepareGroup(context, submits, false);
            staged.upload();
            try { renderer.executeGroup(context, 0, submits, false); }
            finally { renderer.finishExecute(context); staged.endDraw(); }
            try (var fence = encoder.createFence()) {
                encoder.submit();
                if (!fence.awaitCompletion(5_000_000_000L)) throw new IllegalStateException("Scope GPU timeout");
            }
            RenderSystem.executePendingTasks();
            RenderSystem.getDynamicUniforms().reset();
        }

        static int pixel(TextureTarget target, int x, int y) {
            int width = target.width, height = target.height;
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (var readback = RenderSystem.getDevice().createBuffer(() -> "scope fixture readback",
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, width * height * 4)) {
                encoder.copyTextureToBuffer(target.getColorTexture(), readback, 0, () -> {}, 0);
                try (var fence = encoder.createFence()) {
                    encoder.submit();
                    if (!fence.awaitCompletion(5_000_000_000L)) throw new IllegalStateException("Scope readback timeout");
                }
                RenderSystem.executePendingTasks();
                try (var view = readback.slice().map(true, false)) {
                    int offset = (y * width + x) * 4;
                    return (view.data().get(offset) & 255) << 24 | (view.data().get(offset + 1) & 255) << 16
                            | (view.data().get(offset + 2) & 255) << 8 | view.data().get(offset + 3) & 255;
                }
            }
        }

        @Override public void close() { renderer.close(); staged.close(); target.destroyBuffers(); alternate.destroyBuffers(); }
    }
}
