package com.tacz.guns.init;

import com.tacz.guns.entity.sync.core.DataHolderCapabilityProvider;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber
public class CapabilityRegistry {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // Registration is now driven by @AutoRegisterCapability on DataHolder.
        if (!DataHolderCapabilityProvider.CAPABILITY.isRegistered()) {
            throw new IllegalStateException("TACZ's entity-data capability was not registered by Forge");
        }
    }
}
