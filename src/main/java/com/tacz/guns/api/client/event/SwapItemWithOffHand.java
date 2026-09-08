package com.tacz.guns.api.client.event;

import com.tacz.guns.api.event.common.KubeJSGunEventPoster;
import com.tacz.guns.api.event.GunEvent;
import net.minecraftforge.eventbus.api.bus.EventBus;

/**
 * 玩家交换主副手物品时触发该事件
 */
public class SwapItemWithOffHand extends GunEvent implements KubeJSGunEventPoster<SwapItemWithOffHand> {
    public static final EventBus<SwapItemWithOffHand> BUS = EventBus.create(SwapItemWithOffHand.class);

    public SwapItemWithOffHand() {
        postClientEventToKubeJS(this);
    }
}
