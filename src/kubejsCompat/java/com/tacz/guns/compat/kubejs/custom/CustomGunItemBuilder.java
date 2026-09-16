package com.tacz.guns.compat.kubejs.custom;

import com.tacz.guns.compat.kubejs.TimelessKubeJSPlugin;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CustomGunItemBuilder extends ItemBuilder {
    public String typeName;
    private final Identifier targetId;

    public CustomGunItemBuilder(Identifier i) {
        super(i);
        this.targetId = i;
        this.typeName = "kubejs_default";
    }

    public void setTypeName(String name) {
        this.typeName = name;
    }

    @Override
    public Item createObject() {
        TimelessKubeJSPlugin.registerGunType(typeName, RegistryObject.create(targetId, ForgeRegistries.ITEMS));
        return new KubeJSCustomGunItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, targetId)));
    }
}
