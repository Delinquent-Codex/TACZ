package net.minecraftforge.registries;

import net.minecraft.resources.Identifier;
import net.minecraftforge.common.crafting.ingredients.CompoundIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;

/** Initializes the real Forge ingredient registry without loading ForgeMod or TACZ. */
public final class PortingRegistryFixture {
    private PortingRegistryFixture() {}

    public static void ingredients() {
        var registry = new RegistryBuilder<IIngredientSerializer<?>>().setName(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS.identifier())
                .disableSaving().disableSync().create();
        registry.register(Identifier.parse("forge:compound"), CompoundIngredient.SERIALIZER);
    }
}
