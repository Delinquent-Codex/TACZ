package com.tacz.guns.resource;

import com.google.gson.JsonParser;
import com.tacz.guns.resource.serialize.LegacyPackCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Set;

/** Exposes old gun-pack recipe paths without modifying the user's folder or ZIP. */
public final class LegacyRecipePackResources implements PackResources {
    private final PackResources delegate;

    public LegacyRecipePackResources(PackResources delegate) { this.delegate = delegate; }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        var current = delegate.getResource(type, id);
        if (current != null || type != PackType.SERVER_DATA || !id.getPath().startsWith("recipe/")) return current;
        return convert(delegate.getResource(type, id.withPath("recipes/" + id.getPath().substring("recipe/".length()))));
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        var resources = new LinkedHashMap<Identifier, IoSupplier<InputStream>>();
        if (type == PackType.SERVER_DATA && (directory.equals("recipe") || directory.startsWith("recipe/"))) {
            delegate.listResources(type, namespace, "recipes" + directory.substring("recipe".length()), (id, supplier) ->
                    resources.put(id.withPath("recipe/" + id.getPath().substring("recipes/".length())), convert(supplier)));
        }
        // A target-format definition explicitly replaces an old definition with the same ID.
        delegate.listResources(type, namespace, directory, resources::put);
        resources.forEach(output);
    }

    private static IoSupplier<InputStream> convert(IoSupplier<InputStream> input) {
        if (input == null) return null;
        return () -> {
            try (var reader = new InputStreamReader(input.get(), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";
                if (type.equals("minecraft:crafting_shaped")) {
                    var keys = json.getAsJsonObject("key");
                    for (String key : Set.copyOf(keys.keySet())) keys.add(key, LegacyPackCodecs.upgradeIngredient(keys.get(key)));
                } else if (type.equals("minecraft:crafting_shapeless")) {
                    var ingredients = json.getAsJsonArray("ingredients");
                    for (int index = 0; index < ingredients.size(); index++) {
                        ingredients.set(index, LegacyPackCodecs.upgradeIngredient(ingredients.get(index)));
                    }
                }
                if (type.equals("minecraft:crafting_shaped") || type.equals("minecraft:crafting_shapeless")) {
                    json.add("result", LegacyPackCodecs.upgradeItemStack(json.get("result")));
                }
                return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                throw new IOException("Cannot convert legacy gun-pack recipe", exception);
            }
        };
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) { return delegate.getRootResource(paths); }

    @Override
    public Set<String> getNamespaces(PackType type) { return delegate.getNamespaces(type); }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException { return delegate.getMetadataSection(type); }

    @Override
    public PackLocationInfo location() { return delegate.location(); }

    @Override
    public void close() { delegate.close(); }
}
