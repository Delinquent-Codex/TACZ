package com.tacz.guns.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import com.tacz.guns.api.util.LegacyProfileData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.fml.LogicalSide;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TargetMinecart extends AbstractMinecart implements ITargetEntity {
    public static EntityType<TargetMinecart> TYPE = EntityType.Builder.<TargetMinecart>of(TargetMinecart::new, MobCategory.MISC)
            .sized(0.75F, 2.4F)
            .clientTrackingRange(8)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse("tacz:target_minecart")));

    private static final ResolvableProfile EMPTY_PROFILE = ResolvableProfile.createUnresolved(net.minecraft.util.Util.NIL_UUID);
    private static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.defineId(TargetMinecart.class, EntityDataSerializers.RESOLVABLE_PROFILE);

    public TargetMinecart(EntityType<TargetMinecart> type, Level world) {
        super(type, world);
    }

    public TargetMinecart(Level level, double x, double y, double z) {
        super(TYPE, level, x, y, z);
    }

    @Override
    public void onProjectileHit(Entity entity, EntityHitResult result, DamageSource source, float damage) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (source.isDirect()) {
            return;
        }
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof Player player) {
            this.setHurtDir(-1);
            this.setHurtTime(10);
            this.markHurt();
            this.setDamage(10);
            double dis = this.position().distanceTo(sourceEntity.position());
            player.sendOverlayMessage(Component.translatable("message.tacz.target_minecart.hit", String.format("%.1f", damage), String.format("%.2f", dis)));
            // 原版的声音传播距离由 volume 决定
            // 当声音大于 1 时，距离为 = 16 * volume
            float volume = OtherConfig.TARGET_SOUND_DISTANCE.get() / 16.0f;
            volume = Math.max(volume, 0);
            level().playSound(null, this, ModSounds.TARGET_HIT.get(), SoundSource.BLOCKS, volume, this.level().getRandom().nextFloat() * 0.1F + 0.9F);

            if (entity instanceof EntityKineticBullet projectile) {
                boolean isHeadshot = false;
                float headshotMultiplier = 1;
                EntityHurtByGunEvent.Post.BUS.post(new EntityHurtByGunEvent.Post(projectile, this, player, projectile.getGunId(), projectile.getGunDisplayId(), damage, Pair.of(source, source), isHeadshot, headshotMultiplier, LogicalSide.SERVER));
                NetworkHandler.sendToDimension(new ServerMessageGunHurt(projectile.getId(), this.getId(), player.getId(), projectile.getGunId(), projectile.getGunDisplayId(), damage, isHeadshot, headshotMultiplier), this);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return !source.is(DamageTypeTags.IS_EXPLOSION) && super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isRideable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = this.getBoundingBox().getSize();
        if (Double.isNaN(size)) {
            size = 1.0;
        }
        size *= RenderConfig.TARGET_RENDER_DISTANCE.get() * getViewScale();
        return distance < size * size;
    }

    @Override
    public void destroy(ServerLevel level, DamageSource source) {
        this.remove(Entity.RemovalReason.KILLED);
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack itemStack = new ItemStack(ModItems.TARGET_MINECART.get());
            if (this.hasCustomName()) {
                itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            }
            this.spawnAtLocation(level, itemStack);
        }
    }

    @Override
    protected Item getDropItem() {
        return ModItems.TARGET_MINECART.get();
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack itemStack = new ItemStack(ModItems.TARGET_MINECART.get());
        if (this.hasCustomName()) {
            itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        return itemStack;
    }

    @Nullable
    public GameProfile getGameProfile() {
        return validProfileName() ? entityData.get(DATA_PROFILE).partialProfile() : null;
    }

    public @Nullable ResolvableProfile getOwnerProfile() {
        return validProfileName() ? entityData.get(DATA_PROFILE) : null;
    }

    private boolean validProfileName() {
        return getCustomName() != null && net.minecraft.util.StringUtil.isValidPlayerName(getCustomName().getString());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(DATA_PROFILE, EMPTY_PROFILE);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (level() instanceof ServerLevel serverLevel) {
            var requested = validProfileName() ? ResolvableProfile.createUnresolved(name.getString()) : EMPTY_PROFILE;
            entityData.set(DATA_PROFILE, requested);
            if (requested != EMPTY_PROFILE) {
                var server = serverLevel.getServer();
                requested.resolveProfile(server.services().profileResolver()).thenAcceptAsync(profile -> {
                    if (!isRemoved() && entityData.get(DATA_PROFILE) == requested) {
                        entityData.set(DATA_PROFILE, LegacyProfileData.withResolvedProfile(requested, profile));
                    }
                }, server).exceptionally(error -> {
                    com.tacz.guns.GunMod.LOGGER.warn("Could not resolve target minecart profile {}", getUUID(), error);
                    return null;
                });
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("TaczProfile", ResolvableProfile.CODEC).filter(profile -> validProfileName()
                && profile.name().filter(getCustomName().getString()::equals).isPresent())
                .ifPresent(profile -> entityData.set(DATA_PROFILE, profile));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (validProfileName()) output.store("TaczProfile", ResolvableProfile.CODEC, entityData.get(DATA_PROFILE));
    }

    @Override
    @NotNull
    public BlockState getDefaultDisplayBlockState() {
        return ModBlocks.TARGET.get().defaultBlockState();
    }

    @Override
    public float getMaxCartSpeedOnRail() {
        return 0.2F;
    }
}
