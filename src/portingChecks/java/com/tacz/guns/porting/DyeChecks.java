package com.tacz.guns.porting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.api.item.nbt.ItemDataAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.DyeRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.RegistryManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Tests the shipped recipe with real target crafting, using a plain registry item for its TACZ ID. */
public final class DyeChecks {
    private static int assertions;
    private static Item fixtureBox;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        RegistryFixture.bootstrap(() -> {
            var items = RegistryManager.ACTIVE.getRegistry(Registries.ITEM);
            items.unfreeze();
            ((net.minecraft.core.MappedRegistry<Item>) BuiltInRegistries.ITEM).unfreeze();
            var id = Identifier.parse("tacz:ammo_box");
            fixtureBox = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1));
            items.register(id, fixtureBox);
            items.freeze();
            BuiltInRegistries.ITEM.freeze();
        });
        net.minecraftforge.registries.PortingRegistryFixture.ingredients();
        var dyeTag = TagKey.create(Registries.ITEM, Identifier.parse("minecraft:dyes"));
        List<Holder<Item>> dyes = BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item).has(DataComponents.DYE))
                .map(BuiltInRegistries.ITEM::wrapAsHolder).toList();
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, Map.of(dyeTag, dyes))).apply();
        check(dyes.size() == 16, "fixture binds the 16 actual vanilla dyes");
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        var definition = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/tacz/recipe/ammo_box_dyed.json")));
        Recipe<?> decoded = Recipe.CODEC.parse(ops, definition).getOrThrow();
        check(decoded instanceof DyeRecipe, "shipped definition uses the target dye serializer");
        var recipe = (DyeRecipe) decoded;
        var box = new ItemStack(fixtureBox);
        ItemDataAccessor.update(box, data -> { data.putString("AmmoId", "tacz:9mm"); data.putInt("AmmoCount", 127); });
        var original = box.copy();
        var input = CraftingInput.of(2, 1, List.of(box, new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED))));
        check(recipe.matches(input, null), "box and dye match");
        var result = recipe.assemble(input);
        check(result.is(fixtureBox) && result.getCount() == 1 && result.has(DataComponents.DYED_COLOR), "dye produces one colored ammo box");
        check(ItemDataAccessor.get(result).equals(ItemDataAccessor.get(original)), "dye preserves stored ammo and other custom data");
        check(ItemStack.isSameItemSameComponents(box, original), "crafting does not mutate its input");
        check(!recipe.matches(CraftingInput.of(2, 1, List.of(box, box.copy())), null), "two boxes cannot be combined as dye");
        check(!recipe.matches(CraftingInput.of(2, 1, List.of(box, new ItemStack(Items.STONE))), null), "non-dye ingredient rejected");
        var mixed = recipe.assemble(CraftingInput.of(3, 1, List.of(result, new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.BLUE)), new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.WHITE)))));
        check(mixed.has(DataComponents.DYED_COLOR) && ItemDataAccessor.get(mixed).equals(ItemDataAccessor.get(original)), "recoloring with multiple dyes preserves ammo");
        System.out.println("Dye checks passed: " + assertions + " assertions (plain fixture item; no TACZ item class or live crafting).");
    }
}
