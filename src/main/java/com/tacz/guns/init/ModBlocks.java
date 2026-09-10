package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.block.*;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.block.entity.TargetBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.function.Function;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GunMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TILE_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GunMod.MOD_ID);

    // 旧方块就让他独占一个了
    public static RegistryObject<Block> GUN_SMITH_TABLE = register("gun_smith_table", GunSmithTableBlockB::new, SoundType.WOOD);
    public static RegistryObject<Block> WORKBENCH_111 = register("workbench_a", GunSmithTableBlockA::new, SoundType.WOOD);
    public static RegistryObject<Block> WORKBENCH_211 = register("workbench_b", GunSmithTableBlockB::new, SoundType.WOOD);
    public static RegistryObject<Block> WORKBENCH_121 = register("workbench_c", GunSmithTableBlockC::new, SoundType.WOOD);

    public static RegistryObject<Block> TARGET = register("target", TargetBlock::new, SoundType.WOOD);
    public static RegistryObject<Block> STATUE = register("statue", StatueBlock::new, SoundType.STONE);

    private static RegistryObject<Block> register(String name, Function<BlockBehaviour.Properties, Block> factory, SoundType sound) {
        var id = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name));
        return BLOCKS.register(name, () -> factory.apply(BlockBehaviour.Properties.of().setId(id)
                .sound(sound).strength(2.0F, 3.0F).noOcclusion()));
    }

    public static RegistryObject<BlockEntityType<GunSmithTableBlockEntity>> GUN_SMITH_TABLE_BE = TILE_ENTITIES.register("gun_smith_table", () -> GunSmithTableBlockEntity.TYPE);
    public static RegistryObject<BlockEntityType<TargetBlockEntity>> TARGET_BE = TILE_ENTITIES.register("target", () -> TargetBlockEntity.TYPE);
    public static RegistryObject<BlockEntityType<StatueBlockEntity>> STATUE_BE = TILE_ENTITIES.register("statue", () -> StatueBlockEntity.TYPE);
    public static final TagKey<Block> BULLET_IGNORE_BLOCKS = BlockTags.create(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "bullet_ignore"));
}
