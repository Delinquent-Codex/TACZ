package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.item.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.function.Function;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GunMod.MOD_ID);

    public static RegistryObject<ModernKineticGunItem> MODERN_KINETIC_GUN = register("modern_kinetic_gun", ModernKineticGunItem::new);

//    public static RegistryObject<ThrowableItem> M67 = ITEMS.register("m67", ThrowableItem::new);

    public static RegistryObject<Item> AMMO = register("ammo", AmmoItem::new);
    public static RegistryObject<AttachmentItem> ATTACHMENT = register("attachment", AttachmentItem::new);

    public static RegistryObject<GunSmithTableItem> GUN_SMITH_TABLE = register("gun_smith_table", properties -> new DefaultTableItem(ModBlocks.GUN_SMITH_TABLE.get(), properties.useBlockDescriptionPrefix()));
    public static RegistryObject<GunSmithTableItem> WORKBENCH_111 = register("workbench_a", properties -> new GunSmithTableItem(ModBlocks.WORKBENCH_111.get(), properties.useBlockDescriptionPrefix()));
    public static RegistryObject<GunSmithTableItem> WORKBENCH_211 = register("workbench_b", properties -> new GunSmithTableItem(ModBlocks.WORKBENCH_211.get(), properties.useBlockDescriptionPrefix()));
    public static RegistryObject<GunSmithTableItem> WORKBENCH_121 = register("workbench_c", properties -> new GunSmithTableItem(ModBlocks.WORKBENCH_121.get(), properties.useBlockDescriptionPrefix()));


    public static RegistryObject<Item> TARGET = register("target", properties -> new BlockItem(ModBlocks.TARGET.get(), properties.useBlockDescriptionPrefix()));
    public static RegistryObject<Item> STATUE = register("statue", properties -> new BlockItem(ModBlocks.STATUE.get(), properties.useBlockDescriptionPrefix()));
    public static RegistryObject<Item> AMMO_BOX = register("ammo_box", AmmoBoxItem::new);
    public static RegistryObject<Item> TARGET_MINECART = register("target_minecart", TargetMinecartItem::new);

    private static <T extends Item> RegistryObject<T> register(String name, Function<Item.Properties, T> factory) {
        var id = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name));
        return ITEMS.register(name, () -> factory.apply(new Item.Properties().setId(id)));
    }

    @SubscribeEvent
    public static void onItemRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.ITEMS.getRegistryKey())) {
            GunItemManager.registerGunItem(ModernKineticGunItem.TYPE_NAME, MODERN_KINETIC_GUN);
        }
    }
}