package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GunMod.MOD_ID);

    public static final RegistryObject<SoundEvent> GUN = SOUNDS.register("gun", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun")));
    public static final RegistryObject<SoundEvent> TARGET_HIT = SOUNDS.register("target_block_hit", () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "target_block_hit")));
}
