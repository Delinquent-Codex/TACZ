package com.tacz.guns.api.event.common;

import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.event.GunEvent;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
import net.minecraftforge.fml.LogicalSide;

/**
 * 生物结束更换枪械弹药时触发的事件。
 */
public class GunFinishReloadEvent extends GunEvent implements Cancellable, KubeJSGunEventPoster<GunFinishReloadEvent>{
    public static final CancellableEventBus<GunFinishReloadEvent> BUS = cancellableBus(GunFinishReloadEvent.class);

    private final ItemStack gunItemStack;
    private final LogicalSide logicalSide;

    public GunFinishReloadEvent(ItemStack gunItemStack, LogicalSide side) {
        this.gunItemStack = gunItemStack;
        this.logicalSide = side;
        postEventToKubeJS(this);
    }


    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public LogicalSide getLogicalSide() {
        return logicalSide;
    }
}
