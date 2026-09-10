package com.tacz.guns.porting;

import com.tacz.guns.init.ModDataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.registries.RegistryManager;

/** Real registries in an isolated JVM, without FML lifecycle or TACZ item registration. */
final class RegistryFixture {
    private RegistryFixture() {}
    static void bootstrap() {
        bootstrap(() -> {});
    }

    static void bootstrap(Runnable registerFixtureItems) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var componentRegistry = RegistryManager.ACTIVE.getRegistry(Registries.DATA_COMPONENT_TYPE);
        componentRegistry.unfreeze();
        componentRegistry.register(Identifier.parse("tacz:attachments"), ModDataComponents.ATTACHMENT_DATA);
        componentRegistry.freeze();
        registerFixtureItems.run();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
    }
}
