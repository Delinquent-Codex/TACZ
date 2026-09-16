package com.tacz.guns.client.event;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.resource.ClientIndexManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class InventoryEvent {
    private static final int HOTBAR_WARM_UP_INTERVAL_TICKS = 7;
    private static final int BACKPACK_WARM_UP_INTERVAL_TICKS = 41;

    // 用于切枪逻辑
    private static int oldHotbarSelected = -1;
    private static ItemStack oldHotbarSelectItem = ItemStack.EMPTY;

    // The baseline handles both phases; retain its scheduling frequency.
    @SubscribeEvent
    public static void onPlayerChangeSelect(TickEvent.ClientTickEvent.Pre event) {
        onPlayerChangeSelect((TickEvent.ClientTickEvent) event);
    }

    @SubscribeEvent
    public static void onPlayerChangeSelect(TickEvent.ClientTickEvent.Post event) {
        onPlayerChangeSelect((TickEvent.ClientTickEvent) event);
    }

    public static void onPlayerChangeSelect(TickEvent.ClientTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        // 玩家切换选中框的情况
        if (oldHotbarSelected != inventory.getSelectedSlot()) {
            ClientIndexManager.warmUpItem(inventory.getItem(inventory.getSelectedSlot()));
            if (oldHotbarSelected == -1) {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(ItemStack.EMPTY);
            } else {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(inventory.getItem(oldHotbarSelected));
            }
            oldHotbarSelected = inventory.getSelectedSlot();
            oldHotbarSelectItem = inventory.getItem(inventory.getSelectedSlot()).copy();
            return;
        }
        // 玩家选中的物品改变的情况
        ItemStack currentItem = inventory.getItem(inventory.getSelectedSlot());
        if (currentItem.getItem() instanceof IAnimationItem item ) {
            if (!item.isSame(oldHotbarSelectItem, currentItem)) {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(oldHotbarSelectItem);
            }
        } else {
            if (!ItemStack.matches(oldHotbarSelectItem, currentItem)) {
                IClientPlayerGunOperator.fromLocalPlayer(player).draw(oldHotbarSelectItem);
            }
        }

        if (!ItemStack.matches(oldHotbarSelectItem, currentItem)) {
            oldHotbarSelectItem = currentItem.copy();
        }
        if (event instanceof TickEvent.ClientTickEvent.Post) {
            if (player.tickCount % HOTBAR_WARM_UP_INTERVAL_TICKS == 0) {
                ClientIndexManager.warmUpEquippedAndHotbarModels();
            }
            if (player.tickCount % BACKPACK_WARM_UP_INTERVAL_TICKS == 0) {
                ClientIndexManager.warmUpBackpackModels();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerSwapMainHand(SwapItemWithOffHand event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        IClientPlayerGunOperator.fromLocalPlayer(player).draw(player.getMainHandItem());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 离开游戏时重置客户端 draw 状态
        oldHotbarSelected = -1;
        oldHotbarSelectItem = ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        // Forge fires this once for physical client shutdown, not on world disconnect.
        // Keep the scheduler reusable between worlds, but do not hold up JVM exit.
        LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE.shutdownNow();
    }

    private static boolean isSame(ItemStack i, ItemStack j) {
        IGun iGun1 = IGun.getIGunOrNull(i);
        IGun iGun2 = IGun.getIGunOrNull(j);
        if (iGun1 != null && iGun2 != null) {
            return iGun1.getGunId(i).equals(iGun2.getGunId(j));
        }
        if (i.isEmpty() || j.isEmpty()) {
            return i.isEmpty() && j.isEmpty();
        }
        return ItemStack.matches(i, j);
    }
}
