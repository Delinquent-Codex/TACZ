package com.tacz.guns.compat.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.client.event.handler.ComputePlayerAimStateEventHandler;
import com.tacz.guns.api.item.IGun;

public class ShoulderSurfingPlugin implements IShoulderSurfingPlugin {
	@Override
	public void register(IEventBus eventBus) {
		// The source Predicate callback checks both hands, independently of TACZ ADS.
		eventBus.register((ComputePlayerAimStateEventHandler) event -> {
			if (event.getEntity().getMainHandItem().getItem() instanceof IGun
					|| event.getEntity().getOffhandItem().getItem() instanceof IGun) {
				event.setResult(true);
			}
		});
	}
}
