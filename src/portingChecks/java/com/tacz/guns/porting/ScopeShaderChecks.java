package com.tacz.guns.porting;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import com.tacz.guns.client.renderer.scope.ScopePipelines;
import com.tacz.guns.client.renderer.scope.ScopeShader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FileUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;

/** Compiles actual pinned game/mod shader resources; no texture/atlas bake or game launch. */
final class ScopeShaderChecks implements ShaderSource, AutoCloseable {
    private final ZipFile assets;
    private final Path modResources = Path.of("src/main/resources");

    private ScopeShaderChecks(Path clientJar) throws IOException { assets = new ZipFile(clientJar.toFile()); }

    static void run(GpuDevice device) throws Exception {
        Path clientJar = Path.of(System.getProperty("porting.clientAssets"));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(clientJar)) {
            byte[] bytes = new byte[65536];
            for (int length; (length = input.read(bytes)) >= 0;) digest.update(bytes, 0, length);
        }
        System.out.println("Target shader assets: " + clientJar + " SHA-256=" + HexFormat.of().formatHex(digest.digest()));
        try (var source = new ScopeShaderChecks(clientJar)) {
            try (var input = source.assets.getInputStream(source.assets.getEntry("version.json"))) {
                var version = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!version.get("id").getAsString().equals("26.2")) throw new AssertionError("Shader assets are not Minecraft 26.2");
            }
            var texture = Identifier.parse("tacz:fixture/texture");
            List<RenderPipeline> pipelines = List.of(RenderPipelines.ENTITY_CUTOUT, RenderPipelines.ENTITY_TRANSLUCENT,
                    RenderPipelines.ENTITY_TRANSLUCENT_CULL, RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE,
                    RenderPipelines.TEXT, RenderPipelines.TEXT_BACKGROUND, RenderPipelines.TEXT_GRAYSCALE,
                    RenderPipelines.TEXT_SEE_THROUGH, RenderPipelines.TEXT_BACKGROUND_SEE_THROUGH,
                    RenderPipelines.TEXT_POLYGON_OFFSET, RenderPipelines.TEXT_GRAYSCALE_POLYGON_OFFSET,
                    RenderPipelines.GLINT, RenderTypes.energySwirl(texture, 1, 1).pipeline(),
                    TaczRenderTypes.laserBeam().pipeline(), TaczRenderTypes.laserBeamEntity().pipeline(),
                    TaczRenderTypes.scopeAperture().pipeline(), TaczRenderTypes.texturedBlit(texture).pipeline());
            int assertions = 1;
            for (RenderPipeline original : pipelines) {
                if (!device.precompilePipeline(original, source).isValid()) throw new AssertionError("Native shader fails: " + original.getLocation());
                assertions++;
                for (int variant = 0; variant < 3; variant++) {
                    var pipeline = ScopePipelines.create(original, variant == 0, variant != 2);
                    var compiled = device.precompilePipeline(pipeline, (id, type) ->
                            type == ShaderType.FRAGMENT && id.equals(pipeline.getFragmentShader())
                                    ? ScopeShader.wrap(source.get(original.getFragmentShader(), type)) : source.get(id, type));
                    if (!compiled.isValid()) throw new AssertionError("Wrapped native shader fails: " + pipeline.getLocation());
                    assertions++;
                }
            }
            System.out.println("Target scope shader checks passed: " + assertions + " assertions (" + device.getDeviceInfo().backendName()
                    + "; real 26.2/mod GLSL compilation, no game textures or gun visuals)");
        }
    }

    @Override public String get(Identifier id, ShaderType type) {
        Identifier location = type.idConverter().idToFile(id);
        Identifier parent = location.withPath(FileUtil::getFullResourcePath);
        // Same native preprocessor and import resolution as target ShaderManager.createPreprocessor.
        var processor = new GlslPreprocessor() {
            private final HashSet<Identifier> imported = new HashSet<>();
            @Override public String applyImport(boolean relative, String path) {
                Identifier include = relative ? parent.withPath(folder -> FileUtil.normalizeResourcePath(folder + path))
                        : Identifier.parse(path).withPrefix("shaders/include/");
                return imported.add(include) ? read(include) : null;
            }
        };
        return String.join("", processor.process(read(location)));
    }

    private String read(Identifier location) {
        String path = "assets/" + location.getNamespace() + "/" + location.getPath();
        try {
            if (Files.isRegularFile(modResources.resolve(path))) return Files.readString(modResources.resolve(path));
            var entry = assets.getEntry(path);
            if (entry == null) throw new IOException("Missing target shader resource " + path);
            try (var input = assets.getInputStream(entry)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public void close() throws IOException { assets.close(); }
}
