package com.tacz.guns.util.block;

import com.google.common.collect.Sets;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.util.HitboxHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

public class ProjectileExplosion extends ServerExplosion {
    private static final ExplosionDamageCalculator DEFAULT_CONTEXT = new ExplosionDamageCalculator();
    private final ServerLevel level;
    private final double x;
    private final double y;
    private final double z;
    private final float power;
    private final float radius;
    private final boolean knockback;
    private final Entity owner;
    private final Entity exploder;
    private final ExplosionDamageCalculator damageCalculator;
    private final List<BlockPos> toBlow = new ArrayList<>();
    private final boolean fire;

    public ProjectileExplosion(Level level, Entity owner, Entity exploder, @Nullable DamageSource source, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float power, float radius, boolean knockback, Explosion.BlockInteraction mode) {
        super(requireServer(level), exploder, source, damageCalculator, new Vec3(x, y, z), radius, AmmoConfig.EXPLOSIVE_AMMO_FIRE.get(), mode);
        this.level = requireServer(level);
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
        this.radius = radius;
        this.owner = owner;
        this.exploder = exploder;
        this.damageCalculator = damageCalculator == null ? DEFAULT_CONTEXT : damageCalculator;
        this.knockback = knockback;
        this.fire = AmmoConfig.EXPLOSIVE_AMMO_FIRE.get();
    }

    private static ServerLevel requireServer(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        throw new IllegalArgumentException("Projectile explosions must execute on the server");
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }

    public void clearToBlow() {
        this.toBlow.clear();
    }

    /** Client sound/particles are delivered by ExplodeUtil's target explosion packet. */
    public void finalizeExplosion(boolean spawnParticles) {
        if (this.getBlockInteraction() != BlockInteraction.KEEP) {
            this.interactWithBlocks(this.toBlow);
        }
        if (this.fire) {
            this.createFire(this.toBlow);
        }
    }

    @Override
    public int explode() {
        if (!(this.radius > 0) || !Float.isFinite(this.radius) || !Float.isFinite(this.power)) {
            return 0;
        }
        this.level.gameEvent(this.exploder, GameEvent.EXPLODE, BlockPos.containing(this.x, this.y, this.z));
        Set<BlockPos> set = Sets.newHashSet();
        int i = 16;

        for (int x = 0; x < i; ++x) {
            for (int y = 0; y < i; ++y) {
                for (int z = 0; z < i; ++z) {
                    if (x == 0 || x == i - 1 || y == 0 || y == i - 1 || z == 0 || z == i - 1) {
                        double d0 = ((float) x / (i - 1) * 2.0F - 1.0F);
                        double d1 = ((float) y / (i - 1) * 2.0F - 1.0F);
                        double d2 = ((float) z / (i - 1) * 2.0F - 1.0F);
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = this.radius * (0.7F + this.level.getRandom().nextFloat() * 0.6F);
                        double blockX = this.x;
                        double blockY = this.y;
                        double blockZ = this.z;

                        for (float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                            BlockPos pos = BlockPos.containing(blockX, blockY, blockZ);
                            BlockState blockState = this.level.getBlockState(pos);
                            FluidState fluidState = this.level.getFluidState(pos);
                            if (!this.level.isInWorldBounds(pos)) {
                                break;
                            }

                            Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, pos, blockState, fluidState);
                            if (optional.isPresent()) {
                                f -= (optional.get() + f1) * f1;
                            }

                            if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, pos, blockState, f)) {
                                set.add(pos);
                            }

                            blockX += d0 * (double) f1;
                            blockY += d1 * (double) f1;
                            blockZ += d2 * (double) f1;
                        }
                    }
                }
            }
        }

        this.getToBlow().addAll(set);
        float radius = this.radius;
        int minX = Mth.floor(this.x - (double) radius - 1.0D);
        int maxX = Mth.floor(this.x + (double) radius + 1.0D);
        int minY = Mth.floor(this.y - (double) radius - 1.0D);
        int maxY = Mth.floor(this.y + (double) radius + 1.0D);
        int minZ = Mth.floor(this.z - (double) radius - 1.0D);
        int maxZ = Mth.floor(this.z + (double) radius + 1.0D);
        radius *= 2;
        List<Entity> entities = this.level.getEntities(this.exploder, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate(this.level, this, this.toBlow, entities, radius);
        Vec3 explosionPos = new Vec3(this.x, this.y, this.z);

        for (Entity entity : entities) {
            if (entity.ignoreExplosion(this)) {
                continue;
            }

            AABB boundingBox = HitboxHelper.getFixedBoundingBox(entity, this.owner);
            double strength;
            double deltaX;
            double deltaY;
            double deltaZ;
            if (!(entity instanceof LivingEntity)) {
                strength = ExplosionExposure.distanceStrength(Math.sqrt(entity.distanceToSqr(explosionPos)), this.radius);
                deltaX = entity.getX() - this.x;
                deltaY = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                deltaZ = entity.getZ() - this.z;
            } else {
                deltaX = (boundingBox.maxX + boundingBox.minX) / 2;
                deltaY = (boundingBox.maxY + boundingBox.minY) / 2;
                deltaZ = (boundingBox.maxZ + boundingBox.minZ) / 2;
                strength = ExplosionExposure.livingStrength(explosionPos, boundingBox, this.radius, point ->
                        BlockRayTrace.rayTraceBlocks(this.level, new ClipContext(explosionPos, point,
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != BlockHitResult.Type.BLOCK);
                deltaX -= this.x;
                deltaY -= this.y;
                deltaZ -= this.z;
            }

            if (strength > 1.0D) {
                continue;
            }

            double distanceToExplosion = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            if (distanceToExplosion != 0.0D) {
                deltaX /= distanceToExplosion;
                deltaY /= distanceToExplosion;
                deltaZ /= distanceToExplosion;
            }

            double damage = 1.0D - strength;
            entity.hurtServer(this.level, this.getDamageSource(), (float) damage * this.power);

            if (entity instanceof LivingEntity living) {
                damage = (float) (damage * (1.0 - living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)));
            }

            float multiplier = this.power * radius / 500;
            // 启用击退效果
            if (AmmoConfig.EXPLOSIVE_AMMO_KNOCK_BACK.get() && this.knockback) {
                entity.setDeltaMovement(entity.getDeltaMovement().add(deltaX * damage * multiplier, deltaY * damage * multiplier, deltaZ * damage * multiplier));
                if (entity instanceof Player player) {
                    if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                        this.getHitPlayers().put(player, new Vec3(deltaX * damage * multiplier, deltaY * damage * multiplier, deltaZ * damage * multiplier));
                    }
                }
            }
        }
        return this.toBlow.size();
    }
}
