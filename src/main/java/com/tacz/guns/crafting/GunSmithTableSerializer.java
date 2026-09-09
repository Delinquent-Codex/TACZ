package com.tacz.guns.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.serialize.GunSmithTableResultSerializer;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;

/** Target recipe codecs; RecipeHolder owns the ID during data loading. */
public final class GunSmithTableSerializer {
    public static final int MAX_MATERIALS = 256;
    public static final MapCodec<GunSmithTableRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            GunSmithTableResultSerializer.CODEC.fieldOf("result").forGetter(GunSmithTableRecipe::getResult),
            GunSmithTableIngredient.CODEC.listOf(0, MAX_MATERIALS).fieldOf("materials").forGetter(GunSmithTableRecipe::getInputs)
    ).apply(instance, (result, inputs) -> new GunSmithTableRecipe(null, result, inputs)));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunSmithTableRecipe> STREAM_CODEC = StreamCodec.ofMember(
            GunSmithTableSerializer::write, GunSmithTableSerializer::read);
    public static final RecipeSerializer<GunSmithTableRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private GunSmithTableSerializer() {}

    private static GunSmithTableRecipe read(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_MATERIALS) throw new DecoderException("Invalid material count: " + size);
        var ingredients = new ArrayList<GunSmithTableIngredient>(size);
        for (int index = 0; index < size; index++) {
            Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            int count = buffer.readVarInt();
            if (count < 1) throw new DecoderException("Ingredient count must be positive");
            ingredients.add(new GunSmithTableIngredient(ingredient, count));
        }
        var stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        var group = buffer.readIdentifier();
        return new GunSmithTableRecipe(null, new GunSmithTableResult(stack, group), ingredients);
    }

    private static void write(GunSmithTableRecipe recipe, RegistryFriendlyByteBuf buffer) {
        if (recipe.getInputs().size() > MAX_MATERIALS) throw new IllegalArgumentException("Too many materials");
        buffer.writeVarInt(recipe.getInputs().size());
        for (var ingredient : recipe.getInputs()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.getIngredient());
            buffer.writeVarInt(ingredient.getCount());
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.getOutput());
        buffer.writeIdentifier(recipe.getTab());
    }
}
