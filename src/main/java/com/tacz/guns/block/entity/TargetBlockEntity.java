package com.tacz.guns.block.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import com.tacz.guns.api.util.LegacyProfileData;
import com.tacz.guns.api.util.LegacyComponentData;
import com.tacz.guns.api.item.nbt.StoredItemData;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

import static com.tacz.guns.block.TargetBlock.OUTPUT_POWER;
import static com.tacz.guns.block.TargetBlock.STAND;

public class TargetBlockEntity extends BlockEntity implements Nameable {
    public static final BlockEntityType<TargetBlockEntity> TYPE = new BlockEntityType<>(TargetBlockEntity::new, java.util.Set.of(ModBlocks.TARGET.get()));
    /**
     * 标靶复位时间，暂定为 5 秒
     */
    private static final int RESET_TIME = 5 * 20;
    private static final String OWNER_TAG = "Owner";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    public float rot = 0;
    public float oRot = 0;
    private @Nullable ResolvableProfile owner;
    private @Nullable Tag unresolvedOwner;
    private @Nullable Tag unresolvedName;
    private int loadedDataVersion;
    private static final String DATA_VERSION_TAG = "tacz:target_data_version";
    private @Nullable Component name;

    public TargetBlockEntity(BlockPos pos, BlockState blockState) {
        super(TYPE, pos, blockState);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TargetBlockEntity pBlockEntity) {
        pBlockEntity.oRot = pBlockEntity.rot;
        if (state.getValue(STAND)) {
            pBlockEntity.rot = Math.max(pBlockEntity.rot - 18, 0);
        } else {
            pBlockEntity.rot = Math.min(pBlockEntity.rot + 45, 90);
        }
    }

    @Nullable
    public GameProfile getOwner() {
        return owner == null ? null : owner.partialProfile();
    }

    public @Nullable ResolvableProfile getOwnerProfile() { return owner; }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = owner == null ? null : !owner.properties().isEmpty() ? ResolvableProfile.createResolved(owner)
                : owner.name().isBlank() ? ResolvableProfile.createUnresolved(owner.id()) : ResolvableProfile.createUnresolved(owner.name());
        this.unresolvedOwner = null;
        resolveOwner();
        refresh();
    }

    public void setOwnerName(String name) {
        this.owner = net.minecraft.util.StringUtil.isValidPlayerName(name) ? ResolvableProfile.createUnresolved(name) : null;
        this.unresolvedOwner = null;
        resolveOwner();
        refresh();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        resolveOwner();
    }

    private void resolveOwner() {
        if (owner instanceof ResolvableProfile.Dynamic requested && level instanceof ServerLevel serverLevel) {
            var server = serverLevel.getServer();
            requested.resolveProfile(server.services().profileResolver()).thenAcceptAsync(profile -> {
                if (owner == requested && !isRemoved()) {
                    owner = LegacyProfileData.withResolvedProfile(requested, profile);
                    refresh();
                }
            }, server).exceptionally(error -> {
                com.tacz.guns.GunMod.LOGGER.warn("Could not resolve target profile at {}", worldPosition, error);
                return null;
            });
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = null;
        unresolvedOwner = null;
        unresolvedName = null;
        input.read(OWNER_TAG, StoredItemData.RAW_TAG_CODEC).ifPresent(saved -> {
            var decoded = LegacyProfileData.decode(saved);
            if (decoded.result().isPresent()) owner = decoded.result().get();
            else {
                unresolvedOwner = saved.copy();
                com.tacz.guns.GunMod.LOGGER.error("Preserving unreadable target owner at {}: {}", worldPosition, decoded.error().orElseThrow().message());
            }
        });
        name = null;
        loadedDataVersion = input.getIntOr(DATA_VERSION_TAG, 0);
        input.read(CUSTOM_NAME_TAG, StoredItemData.RAW_TAG_CODEC).ifPresent(saved -> {
            var decoded = loadedDataVersion == 0 && saved instanceof StringTag text
                    ? LegacyComponentData.decode(text.value(), input.lookup())
                    : loadedDataVersion == 1 ? ComponentSerialization.CODEC.parse(input.lookup().createSerializationContext(NbtOps.INSTANCE), saved)
                    : com.mojang.serialization.DataResult.<Component>error(() -> "Unknown target name format/version: " + loadedDataVersion);
            if (decoded.result().isPresent()) name = decoded.result().get();
            else unresolvedName = saved.copy();
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (unresolvedOwner != null) output.store(OWNER_TAG, StoredItemData.RAW_TAG_CODEC, unresolvedOwner);
        else output.storeNullable(OWNER_TAG, ResolvableProfile.CODEC, owner);
        if (unresolvedName != null) {
            output.store(CUSTOM_NAME_TAG, StoredItemData.RAW_TAG_CODEC, unresolvedName);
            output.putInt(DATA_VERSION_TAG, loadedDataVersion);
        } else {
            output.storeNullable(CUSTOM_NAME_TAG, ComponentSerialization.CODEC, name);
            output.putInt(DATA_VERSION_TAG, 1);
        }
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : Component.empty();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    public void setCustomName(Component name) {
        this.name = name;
        this.unresolvedName = null;
        setChanged();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public void refresh() {
        this.setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(-2, 0, -2)), net.minecraft.world.phys.Vec3.atLowerCornerOf(worldPosition.offset(2, 2, 2)));
    }

    public void hit(Level level, BlockState state, BlockHitResult hit, boolean isUpperBlock) {
        if (this.level != null && state.getValue(STAND)) {
            BlockPos blockPos = hit.getBlockPos();
            // 如果是击中上方，把状态移动到下方处理
            if (isUpperBlock) {
                blockPos = blockPos.below();
                state = level.getBlockState(blockPos);
            }
            int redstoneStrength = TargetBlock.getRedstoneStrength(hit, isUpperBlock);
            level.setBlock(blockPos, state.setValue(STAND, false).setValue(OUTPUT_POWER, redstoneStrength), Block.UPDATE_ALL);
            level.scheduleTick(blockPos, state.getBlock(), RESET_TIME);
            // 原版的声音传播距离由 volume 决定
            // 当声音大于 1 时，距离为 = 16 * volume
            float volume = OtherConfig.TARGET_SOUND_DISTANCE.get() / 16.0f;
            volume = Math.max(volume, 0);
            level.playSound(null, blockPos, ModSounds.TARGET_HIT.get(), SoundSource.BLOCKS, volume, this.level.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }
}
