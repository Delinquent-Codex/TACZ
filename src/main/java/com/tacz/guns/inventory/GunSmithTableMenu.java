package com.tacz.guns.inventory;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableInput;
import com.tacz.guns.crafting.IngredientAllocation;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonBlockIndex;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.items.ItemHandlerHelper;
import java.util.ArrayList;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;

import javax.annotation.Nullable;
import java.util.List;

public class GunSmithTableMenu extends AbstractContainerMenu {
    public static final MenuType<GunSmithTableMenu> TYPE = IForgeMenuType.create((windowId, inv, data) -> {
        Identifier blockId = data.readIdentifier();
        return new GunSmithTableMenu(windowId, inv, blockId);
    });

    private final Identifier blockId;
    private final RecipeFilter filter;

    public GunSmithTableMenu(int id, Inventory inventory, @Nullable Identifier resourceLocation) {
        super(TYPE, id);
        this.blockId = resourceLocation;
        this.filter = TimelessAPI.getCommonBlockIndex(getBlockId()).map(CommonBlockIndex::getFilter).orElse(null);
        // The screen has no inventory controls, but its active menu must track the
        // player's slots so crafting, pickups and other changes reach the client.
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            this.addSlot(new Slot(inventory, slot, -1000, -1000) {
                @Override
                public boolean isActive() {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
    }

    @Nullable
    public Identifier getBlockId() {
        return blockId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int pIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Nullable
    private GunSmithTableRecipe getRecipe(Identifier recipeId, RecipeManager recipeManager) {
        if (!DefaultAssets.DEFAULT_BLOCK_ID.equals(getBlockId()) || SyncConfig.ENABLE_TABLE_FILTER.get()) {
            if (filter != null && !filter.contains(recipeId)) {
                return null;
            }
        }

        Recipe<?> recipe = recipeManager.byKey(ResourceKey.create(Registries.RECIPE, recipeId)).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
        if (recipe instanceof GunSmithTableRecipe gunSmithTableRecipe) {
            boolean flag = TimelessAPI.getCommonBlockIndex(getBlockId()).map(blockIndex -> {
                return blockIndex.getData().getTabs().stream().noneMatch(tab -> tab.id().equals(gunSmithTableRecipe.getTab()));
            }).orElse(true);
            if (DefaultAssets.DEFAULT_BLOCK_ID.equals(getBlockId()) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                flag = false;
            }
            if (flag) {
                return null;
            }
            return gunSmithTableRecipe;
        }
        return null;
    }

    public void doCraft(Identifier recipeId, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !stillValid(player) || player.containerMenu != this) return;
        GunSmithTableRecipe recipe = getRecipe(recipeId, serverPlayer.level().recipeAccess());
        if (recipe == null || recipe.getOutput().isEmpty()) return;
        player.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handler -> {
            var extracted = new ArrayList<ItemStack>();
            Runnable refund = () -> {
                for (ItemStack stack : extracted) {
                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                    if (!remaining.isEmpty()) player.drop(remaining, false);
                }
            };
            if (!player.isCreative()) {
                var available = new ArrayList<ItemStack>(handler.getSlots());
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    available.add(handler.extractItem(slot, stack.getCount(), true));
                }
                var plan = IngredientAllocation.plan(recipe.getInputs(), new GunSmithTableInput(available));
                if (plan.isEmpty()) return;
                int[] counts = plan.get();
                for (int slot = 0; slot < counts.length; slot++) {
                    if (counts[slot] == 0) continue;
                    ItemStack taken = handler.extractItem(slot, counts[slot], false);
                    extracted.add(taken);
                    if (taken.getCount() != counts[slot] || !ItemStack.isSameItemSameComponents(available.get(slot), taken)) {
                        refund.run();
                        broadcastFullState();
                        return;
                    }
                }
            }
            var spawned = new ArrayList<ItemEntity>();
            for (ItemStack stack : com.tacz.guns.crafting.CraftingOutputs.split(recipe.getOutput())) {
                ItemEntity output = new ItemEntity(serverPlayer.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
                output.setPickUpDelay(0);
                if (!serverPlayer.level().addFreshEntity(output)) {
                    spawned.forEach(ItemEntity::discard);
                    refund.run();
                    broadcastFullState();
                    return;
                }
                spawned.add(output);
            }
            broadcastFullState();
            NetworkHandler.sendToClientPlayer(new ServerMessageCraft(this.containerId), player);
        });
    }
}
