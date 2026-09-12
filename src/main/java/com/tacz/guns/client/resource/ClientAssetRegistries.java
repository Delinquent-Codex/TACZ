package com.tacz.guns.client.resource;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ClientAssetRegistries {
    private ClientAssetRegistries() {}

    public static RegistryAccess current() {
        var connection = Minecraft.getInstance().getConnection();
        return connection == null ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) : connection.registryAccess();
    }
}
