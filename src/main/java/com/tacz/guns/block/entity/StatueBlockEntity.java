package com.tacz.guns.block.entity;

import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.api.item.nbt.StoredItemData;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import static com.tacz.guns.block.StatueBlock.FACING;

public class StatueBlockEntity extends BlockEntity {
    public static final BlockEntityType<StatueBlockEntity> TYPE = new BlockEntityType<>(StatueBlockEntity::new, java.util.Set.of(ModBlocks.STATUE.get()));
    private static final String ITEM_TAG = "Item";
    private ItemStack gunItem = ItemStack.EMPTY;
    private Tag unresolvedItem;
    private boolean recoveryDropped;

    public StatueBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(TYPE, pPos, pBlockState);
    }

    public static void clientTick(Level level, BlockPos blockPos, BlockState state, StatueBlockEntity statueBlockEntity) {
        if (level.getGameTime() % 100 == 0 && !statueBlockEntity.gunItem.isEmpty()) {
            Direction direction = state.getValue(FACING);

            double x = blockPos.getX() + direction.getStepX() * 0.75 + 0.5;
            double z = blockPos.getZ() + direction.getStepZ() * 0.75 + 0.5;

            double dx = -0.02 + level.getRandom().nextDouble() * 0.04;
            double dz = -0.02 + level.getRandom().nextDouble() * 0.04;
            double dy = -0.02 + level.getRandom().nextDouble() * 0.04;

            level.addParticle(ParticleTypes.END_ROD, x, blockPos.getY() + 2.25, z, dx, dy, dz);
        }
    }

    public ItemStack getGunItem() {
        return gunItem;
    }

    public boolean hasUnresolvedItem() { return unresolvedItem != null; }

    public void setGun(ItemStack stack) {
        if (unresolvedItem != null) throw new IllegalStateException("Statue has unconverted saved item data; recover it before replacing the gun");
        this.dropItem();
        this.gunItem = stack.copyWithCount(1);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
        this.setChanged();
    }

    public void dropItem() {
        if (!gunItem.isEmpty() && level != null && !level.isClientSide()) {
            Direction direction = getBlockState().getValue(FACING);
            Block.popResource(level, worldPosition.relative(direction).above(), gunItem);
            this.gunItem = ItemStack.EMPTY;
            if (level != null) {
                BlockState state = level.getBlockState(worldPosition);
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            }
            this.setChanged();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        if (unresolvedItem != null) {
            if (!recoveryDropped) {
                // Keep the original compound on a placeable item, even when its gun cannot yet be decoded.
                CompoundTag saved = saveWithoutMetadata(level.registryAccess());
                Block.popResource(level, pos, StoredItemData.recoveryBlockItem(new ItemStack(ModBlocks.STATUE.get()), TYPE, saved));
                recoveryDropped = true;
            }
        } else dropItem();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.gunItem = ItemStack.EMPTY;
        this.unresolvedItem = null;
        this.recoveryDropped = false;
        input.read(ITEM_TAG, StoredItemData.RAW_TAG_CODEC).ifPresent(saved -> {
            var decoded = saved instanceof CompoundTag compound ? StoredItemData.decode(compound, input.lookup())
                    : com.mojang.serialization.DataResult.<ItemStack>error(() -> "Stored Item is not a compound");
            if (decoded.result().isPresent()) this.gunItem = decoded.result().get();
            else {
                this.unresolvedItem = saved.copy();
                com.tacz.guns.GunMod.LOGGER.error("Preserving unreadable statue item at {}: {}", worldPosition,
                        decoded.error().orElseThrow().message());
            }
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (unresolvedItem != null) output.store(ITEM_TAG, StoredItemData.RAW_TAG_CODEC, unresolvedItem);
        else output.store(ITEM_TAG, ItemStack.OPTIONAL_CODEC, gunItem);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(-2, 0, -2)), net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(2, 2, 2)));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
