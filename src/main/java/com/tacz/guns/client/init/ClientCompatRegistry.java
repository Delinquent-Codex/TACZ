package com.tacz.guns.client.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.compat.ClothConfigScreen;
import com.tacz.guns.compat.cloth.MenuIntegration;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.init.CompatRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

/** Client integrations are only resolved after Forge selects the client side. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public final class ClientCompatRegistry {
    @SubscribeEvent
    public static void onEnqueue(InterModEnqueueEvent event) {
        event.enqueueWork(() -> CompatRegistry.checkModLoad(CompatRegistry.CLOTH_CONFIG, MenuIntegration::registerModsPage));
        event.enqueueWork(ClothConfigScreen::registerNoClothConfigPage);
        event.enqueueWork(() -> CompatRegistry.checkModLoad(CompatRegistry.OCULUS, OculusCompat::initCompat));
    }
}
