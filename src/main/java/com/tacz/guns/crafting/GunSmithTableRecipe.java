package com.tacz.guns.crafting;

import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

public class GunSmithTableRecipe implements Recipe<GunSmithTableInput> {
    private Identifier id;
    private final GunSmithTableResult result;
    private final List<GunSmithTableIngredient> inputs;

    public GunSmithTableRecipe(Identifier id, GunSmithTableResult result, List<GunSmithTableIngredient> inputs) {
        this.id = id;
        this.result = result;
        this.inputs = List.copyOf(inputs);
    }

    public GunSmithTableRecipe(Identifier id, TableRecipe tableRecipe) {
        this(id, tableRecipe.getResult(), tableRecipe.getMaterials());
    }

    @Override
    public boolean matches(GunSmithTableInput input, Level level) {
        return IngredientAllocation.plan(inputs, input).isPresent();
    }

    @Override
    public ItemStack assemble(GunSmithTableInput input) {
        return result.getResult().copy();
    }

    @Override
    public boolean isSpecial() { return true; }

    @Override
    public boolean showNotification() { return false; }

    @Override
    public String group() { return getTab().toString(); }

    @Override
    public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        // TACZ consumes inventory materials through its own menu, with no recipe-book grid.
        return net.minecraft.world.item.crafting.PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
        return net.minecraft.world.item.crafting.RecipeBookCategories.CRAFTING_MISC;
    }

    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return this.result.getResult().copy();
    }

    public Identifier getId() {
        return java.util.Objects.requireNonNull(this.id, "Recipe has not been bound to its holder ID");
    }

    public void bindId(Identifier id) {
        if (this.id != null && !this.id.equals(id)) throw new IllegalStateException("Recipe already has ID " + this.id);
        this.id = id;
    }

    @Override
    public RecipeSerializer<GunSmithTableRecipe> getSerializer() {
        return ModRecipe.GUN_SMITH_TABLE_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<GunSmithTableRecipe> getType() {
        return ModRecipe.GUN_SMITH_TABLE_CRAFTING.get();
    }

    public ItemStack getOutput() {
        return result.getResult();
    }

    public List<GunSmithTableIngredient> getInputs() {
        return inputs;
    }

    public GunSmithTableResult getResult() {
        return result;
    }

    public void init() {
        result.init();
    }

    public Identifier getTab() {
        return result.getGroup();
    }
}
