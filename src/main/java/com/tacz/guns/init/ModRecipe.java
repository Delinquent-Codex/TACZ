package com.tacz.guns.init;

import com.tacz.guns.api.TaczConstants;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.GunSmithTableSerializer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber
public class ModRecipe {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TaczConstants.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, TaczConstants.MOD_ID);

    public static RegistryObject<RecipeSerializer<GunSmithTableRecipe>> GUN_SMITH_TABLE_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("gun_smith_table_crafting", () -> GunSmithTableSerializer.SERIALIZER);
    public static RegistryObject<RecipeType<GunSmithTableRecipe>> GUN_SMITH_TABLE_CRAFTING = RECIPE_TYPES.register("gun_smith_table_crafting", () -> new RecipeType<>() {
        @Override
        public String toString() {
            return TaczConstants.MOD_ID + ":gun_smith_table_crafting";
        }
    });

}
