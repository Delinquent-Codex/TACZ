package com.tacz.guns.api.client.event;

import com.tacz.guns.api.event.common.KubeJSGunEventPoster;
import com.tacz.guns.api.event.GunEvent;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;

/**
 * 当第一人称视角触发摇晃时，玩家手部的摇晃
 */
public class RenderItemInHandBobEvent extends GunEvent implements Cancellable, KubeJSGunEventPoster<RenderItemInHandBobEvent> {
    public static final CancellableEventBus<RenderItemInHandBobEvent> BUS = cancellableBus(RenderItemInHandBobEvent.class);


    public static class BobHurt extends RenderItemInHandBobEvent {
        public static final CancellableEventBus<BobHurt> BUS = cancellableBus(BobHurt.class);

        public BobHurt() {
            postClientEventToKubeJS(this);
        }
    }

    public static class BobView extends RenderItemInHandBobEvent {
        public static final CancellableEventBus<BobView> BUS = cancellableBus(BobView.class);

        public BobView() {
            postClientEventToKubeJS(this);
        }
    }
}
