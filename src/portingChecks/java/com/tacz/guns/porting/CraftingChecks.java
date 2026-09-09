package com.tacz.guns.porting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableInput;
import com.tacz.guns.crafting.IngredientAllocation;
import com.tacz.guns.resource.serialize.LegacyPackCodecs;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public final class CraftingChecks {
    private static int assertions;

    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }

    private static GunSmithTableIngredient need(Ingredient ingredient, int count) {
        return new GunSmithTableIngredient(ingredient, count);
    }

    public static void main(String[] args) {
        RegistryFixture.bootstrap();
        net.minecraftforge.registries.PortingRegistryFixture.ingredients();
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
        var legacy = JsonParser.parseString("{\"item\":\"minecraft:diamond_sword\",\"count\":1,\"nbt\":{\"Damage\":7,\"display\":{\"Name\":\"{\\\"text\\\":\\\"Port fixture\\\"}\"},\"Extension\":{\"keep\":42}}}");
        var copy = legacy.deepCopy();
        ItemStack sword = LegacyPackCodecs.ITEM_STACK.parse(ops, legacy).getOrThrow();
        check(sword.is(Items.DIAMOND_SWORD) && sword.getDamageValue() == 7, "legacy damage migrated to component");
        check(sword.get(DataComponents.CUSTOM_NAME).getString().equals("Port fixture"), "legacy name remains a name component");
        check(sword.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("Extension").getIntOr("keep", 0) == 42, "unknown extension preserved");
        check(copy.equals(legacy), "source JSON unchanged by migration");
        var target = LegacyPackCodecs.ITEM_STACK.encodeStart(ops, sword).getOrThrow();
        check(target.getAsJsonObject().has("components") && !target.getAsJsonObject().has("nbt"), "writes component syntax");
        check(ItemStack.isSameItemSameComponents(sword, LegacyPackCodecs.ITEM_STACK.parse(ops, target).getOrThrow()), "target round trip");
        var originalPainting = JsonParser.parseString("{\"item\":\"minecraft:painting\",\"nbt\":{\"EntityTag\":{\"variant\":\"minecraft:kebab\"}}}");
        var painting = LegacyPackCodecs.ITEM_STACK.parse(net.minecraft.data.registries.VanillaRegistries.createLookup().createSerializationContext(JsonOps.INSTANCE), originalPainting).getOrThrow();
        check(painting.has(DataComponents.PAINTING_VARIANT), "legacy painting variant becomes typed component");
        for (String malformed : new String[]{"{\"item\":\"missing:unknown\"}", "{\"item\":\"minecraft:stone\",\"count\":0}",
                "{\"item\":\"minecraft:stone\",\"count\":100}", "{\"item\":\"minecraft:stone\",\"nbt\":\"{bad\"}",
                "{\"item\":\"minecraft:stone\",\"ForgeCaps\":{}}"}) {
            check(LegacyPackCodecs.ITEM_STACK.parse(ops, JsonParser.parseString(malformed)).error().isPresent(), "malformed item rejected: " + malformed);
        }
        var planks = LegacyPackCodecs.INGREDIENT.parse(ops, JsonParser.parseString("[{\"item\":\"minecraft:oak_planks\"},{\"item\":\"minecraft:birch_planks\"}]")).getOrThrow();
        check(planks.test(new ItemStack(Items.OAK_PLANKS)) && planks.test(new ItemStack(Items.BIRCH_PLANKS)) && !planks.test(new ItemStack(Items.STONE)), "legacy alternatives preserved");
        check(LegacyPackCodecs.INGREDIENT.parse(ops, JsonParser.parseString("{\"item\":\"minecraft:stone\",\"tag\":\"minecraft:planks\"}")).error().isPresent(), "ambiguous ingredient rejected");

        var oak = Ingredient.of(Items.OAK_PLANKS);
        var onlyOak = new GunSmithTableInput(List.of(new ItemStack(Items.OAK_PLANKS, 3)));
        check(IngredientAllocation.plan(List.of(need(oak, 2), need(oak, 2)), onlyOak).isEmpty(), "one stack cannot pay two overlapping requirements");
        var inventory = new GunSmithTableInput(List.of(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.BIRCH_PLANKS, 2)));
        var allocation = IngredientAllocation.plan(List.of(need(planks, 2), need(oak, 2)), inventory).orElseThrow();
        check(allocation[0] == 2 && allocation[1] == 2, "flexible requirement leaves oak for strict requirement");
        check(inventory.getItem(0).getCount() == 2 && inventory.getItem(1).getCount() == 2, "planning never mutates inventory");
        check(IngredientAllocation.plan(List.of(need(oak, Integer.MAX_VALUE), need(oak, Integer.MAX_VALUE)), onlyOak).isEmpty(), "total requirement does not overflow");
        ItemStack hundred = new ItemStack(Items.ARROW, 100);
        var output = com.tacz.guns.crafting.CraftingOutputs.split(hundred);
        check(output.stream().mapToInt(ItemStack::getCount).sum() == 100, "100 output items preserved");
        check(output.stream().allMatch(stack -> stack.getCount() <= stack.getMaxStackSize() && stack.getCount() <= 99), "output stacks respect target bounds");
        output.getFirst().shrink(1);
        check(hundred.getCount() == 100, "output stacks do not mutate recipe template");
        // Independent arithmetic feasibility oracle for one strict and one alternative material.
        for (int oakCount = 0; oakCount <= 4; oakCount++) {
            for (int birchCount = 0; birchCount <= 4; birchCount++) {
                for (int strict = 1; strict <= 4; strict++) {
                    for (int flexible = 1; flexible <= 4; flexible++) {
                        var input = new GunSmithTableInput(List.of(new ItemStack(Items.OAK_PLANKS, oakCount), new ItemStack(Items.BIRCH_PLANKS, birchCount)));
                        boolean possible = oakCount >= strict && oakCount + birchCount >= strict + flexible;
                        check(IngredientAllocation.plan(List.of(need(planks, flexible), need(oak, strict)), input).isPresent() == possible, "allocation agrees with arithmetic oracle");
                    }
                }
            }
        }
        System.out.println("Crafting checks passed: " + assertions + " assertions (no live menu or recipe reload).");
    }
}
