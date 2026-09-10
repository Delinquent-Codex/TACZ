package com.tacz.guns.client.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.renderer.item.AmmoBoxModelProperty;
import com.tacz.guns.client.renderer.item.TaczItemModel;
import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public final class ClientItemModels {
    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        // This synchronous constructor event precedes the first resource preparation. Client setup is concurrent with it.
        ItemModels.ID_MAPPER.put(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "item"), TaczItemModel.Unbaked.CODEC);
        RangeSelectItemModelProperties.ID_MAPPER.put(AmmoBoxItem.PROPERTY_NAME, AmmoBoxModelProperty.CODEC);
    }
}
