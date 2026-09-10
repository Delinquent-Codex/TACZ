package com.tacz.guns.porting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.client.renderer.item.TaczItemModel;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ItemModelChecks {
    private static int assertions;

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) throws Exception {
        RegistryFixture.bootstrap();
        ItemModels.bootstrap();
        ItemModels.ID_MAPPER.put(Identifier.parse("tacz:item"), TaczItemModel.Unbaked.CODEC);
        Path assets = Path.of("src/main/resources/assets/tacz");
        for (String name : List.of("modern_kinetic_gun", "ammo", "attachment", "gun_smith_table", "workbench_a", "workbench_b", "workbench_c")) {
            var data = JsonParser.parseString(Files.readString(assets.resolve("items/" + name + ".json"))).getAsJsonObject().get("model");
            var decoded = ItemModels.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow();
            check(decoded instanceof TaczItemModel.Unbaked model && model.base().equals(Identifier.parse("tacz:item/" + name)),
                    "actual target model dispatch resolves " + name);
            var model = JsonParser.parseString(Files.readString(assets.resolve("models/item/" + name + ".json"))).getAsJsonObject();
            check(!model.has("parent") || !model.get("parent").getAsString().equals("builtin/entity"), "removed unavailable parent for " + name);
        }
        for (String name : List.of("statue", "target", "target_minecart")) {
            var data = JsonParser.parseString(Files.readString(assets.resolve("items/" + name + ".json"))).getAsJsonObject().get("model");
            check(ItemModels.CODEC.parse(JsonOps.INSTANCE, data).error().isEmpty(), "vanilla item-model codec accepts " + name);
        }
        var data = JsonParser.parseString(Files.readString(assets.resolve("items/ammo_box.json"))).getAsJsonObject().getAsJsonObject("model");
        var entries = data.getAsJsonArray("entries");
        var original = JsonParser.parseString(Files.readString(assets.resolve("models/item/ammo_box.json"))).getAsJsonObject().getAsJsonArray("overrides");
        check(entries.size() == 9, "all nine ammo-box appearances retained");
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i).getAsJsonObject();
            var old = original.get(i).getAsJsonObject();
            var model = entry.getAsJsonObject("model");
            check(entry.get("threshold").getAsInt() == old.getAsJsonObject("predicate").get("tacz:ammo_statue").getAsInt()
                    && model.get("model").equals(old.get("model")), "source ammo-box threshold and model " + i);
            check(Files.isRegularFile(assets.resolve("models/" + model.get("model").getAsString().substring(5) + ".json")),
                    "referenced ammo-box cuboid exists " + i);
        }
        var tint = entries.get(0).getAsJsonObject().getAsJsonObject("model").getAsJsonArray("tints").get(0);
        var dye = Dye.MAP_CODEC.codec().parse(JsonOps.INSTANCE, tint).getOrThrow();
        var item = new ItemStack(Items.STICK);
        check((dye.calculate(item, null, null) & 0xffffff) == 0x727d6b, "target tint preserves baseline default color");
        item.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
        check((dye.calculate(item, null, null) & 0xffffff) == 0x123456, "target tint reads component-backed dye");
        System.out.println("Item model checks passed: " + assertions + " assertions (codecs/resources/tint; no model bake or rendering).");
    }
}
