package com.tacz.guns.porting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.resource.LegacyRecipePackResources;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PackChecks {
    private static int assertions;
    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }

    private static String read(PackResources resources, PackType type, String id) throws Exception {
        try (var stream = resources.getResource(type, Identifier.parse(id)).get()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] args) throws Exception {
        RegistryFixture.bootstrap();
        var location = new PackLocationInfo("fixture", Component.literal("fixture"), PackSource.BUILT_IN, Optional.empty());
        Path build = Path.of("build/porting-checks").toAbsolutePath().normalize();
        Files.createDirectories(build);
        Path scratch = Files.createTempDirectory(build, "packs-");
        Map<String, String> files = Map.of(
                "data/addon/recipes/sub/old.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"I\"],\"key\":{\"I\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"item\":\"minecraft:iron_nugget\",\"count\":9}}",
                "data/addon/recipes/replaced.json", "{\"old\":true}",
                "data/addon/recipe/replaced.json", "{\"new\":true}",
                "assets/addon/recipes/keep.json", "{\"client\":true}");
        Path folder = scratch.resolve("folder");
        Path zip = scratch.resolve("pack.zip");
        try {
            for (var entry : files.entrySet()) {
                Path file = folder.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, entry.getValue());
            }
            try (var archive = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (var entry : files.entrySet()) {
                    archive.putNextEntry(new ZipEntry(entry.getKey()));
                    archive.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    archive.closeEntry();
                }
            }
            byte[] originalZip = Files.readAllBytes(zip);
            for (Pack.ResourcesSupplier supplier : java.util.List.of(new PathPackResources.PathResourcesSupplier(folder), new FilePackResources.FileResourcesSupplier(zip))) {
                try (var resources = new LegacyRecipePackResources(supplier.openPrimary(location))) {
                    check(resources.getNamespaces(PackType.SERVER_DATA).contains("addon"), "namespace discovery");
                    var legacy = JsonParser.parseString(read(resources, PackType.SERVER_DATA, "addon:recipe/sub/old.json")).getAsJsonObject();
                    check(legacy.getAsJsonObject("result").get("id").getAsString().equals("minecraft:iron_nugget"), "legacy result upgraded");
                    check(legacy.getAsJsonObject("result").get("count").getAsInt() == 9, "result quantity retained");
                    check(legacy.getAsJsonObject("key").get("I").getAsString().equals("minecraft:iron_ingot"), "legacy ingredient upgraded");
                    check(read(resources, PackType.SERVER_DATA, "addon:recipe/replaced.json").equals(files.get("data/addon/recipe/replaced.json")), "target path wins direct lookup");
                    var listed = new LinkedHashMap<Identifier, net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream>>();
                    resources.listResources(PackType.SERVER_DATA, "addon", "recipe", listed::put);
                    check(listed.size() == 2 && listed.keySet().stream().allMatch(id -> id.getPath().startsWith("recipe/")), "one target ID per definition");
                    try (var stream = listed.get(Identifier.parse("addon:recipe/replaced.json")).get()) {
                        check(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("new"), "target path wins listing");
                    }
                    check(read(resources, PackType.CLIENT_RESOURCES, "addon:recipes/keep.json").equals(files.get("assets/addon/recipes/keep.json")), "client resources pass through");
                    check(resources.getResource(PackType.SERVER_DATA, Identifier.parse("addon:recipe/missing.json")) == null, "missing recipe stays absent");
                }
            }
            check(java.util.Arrays.equals(originalZip, Files.readAllBytes(zip)), "ZIP bytes unchanged");
            for (var entry : files.entrySet()) check(Files.readString(folder.resolve(entry.getKey())).equals(entry.getValue()), "source folder bytes unchanged");
            var packJson = JsonParser.parseString(Files.readString(Path.of("src/main/resources/pack.mcmeta"))).getAsJsonObject().get("pack");
            for (var type : PackType.values()) {
                var metadata = net.minecraft.server.packs.metadata.pack.PackMetadataSection.forPackType(type).codec().parse(JsonOps.INSTANCE, packJson).getOrThrow();
                var version = SharedConstants.getCurrentVersion().packVersion(type);
                System.out.println(type + " target pack format: " + version);
                check(metadata.supportedFormats().isValueInRange(version), "root metadata supports " + type);
            }
        } finally {
            if (!scratch.normalize().startsWith(build)) throw new IllegalStateException("Fixture cleanup escaped build root");
            try (var paths = Files.walk(scratch)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("Pack checks passed: " + assertions + " assertions (no full gun-pack reload).");
    }
}
