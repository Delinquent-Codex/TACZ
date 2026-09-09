package com.tacz.guns.crafting;

import net.minecraft.world.item.crafting.Ingredient;

public class GunSmithTableIngredient {
    public static final com.mojang.serialization.Codec<GunSmithTableIngredient> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
            com.tacz.guns.resource.serialize.LegacyPackCodecs.INGREDIENT.fieldOf("item").forGetter(GunSmithTableIngredient::getIngredient),
            com.mojang.serialization.Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(GunSmithTableIngredient::getCount)
    ).apply(instance, GunSmithTableIngredient::new));
    private final Ingredient ingredient;
    private final int count;

    public GunSmithTableIngredient(Ingredient ingredient, int count) {
        if (count < 1) throw new IllegalArgumentException("Ingredient count must be positive");
        java.util.Objects.requireNonNull(ingredient, "ingredient");
        this.ingredient = ingredient;
        this.count = count;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public int getCount() {
        return count;
    }
}
