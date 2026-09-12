package com.tacz.guns.client.particle;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.particles.BulletHoleOption;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * Author: Forked from MrCrayfish, continued by Timeless devs
 */
public class BulletHoleParticle extends SingleQuadParticle {
    private final Direction direction;
    private final BlockPos pos;
    private int uOffset;
    private int vOffset;
    private float textureDensity;

    public BulletHoleParticle(ClientLevel world, double x, double y, double z, Direction direction, BlockPos pos, String ammoId, String gunId, String gunDisplayId) {
        super(world, x, y, z, getSprite(world, pos));
        this.setSprite(this.sprite);
        this.direction = direction;
        this.pos = pos;
        this.lifetime = this.getLifetimeFromConfig(world);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = 0.05F;

        BlockState state = world.getBlockState(pos);
        if (state.is(ModBlocks.TARGET.get()) || shouldRemove()) {
            this.remove();
        }
        TimelessAPI.getGunDisplay(Identifier.parse(gunDisplayId), Identifier.parse(gunId)).ifPresent(gunIndex -> {
            float[] gunTracerColor = gunIndex.getTracerColor();
            if (gunTracerColor != null) {
                this.rCol = gunTracerColor[0];
                this.gCol = gunTracerColor[1];
                this.bCol = gunTracerColor[2];
            } else {
                TimelessAPI.getClientAmmoIndex(Identifier.parse(ammoId)).ifPresent(ammoIndex -> {
                    float[] ammoTracerColor = ammoIndex.getTracerColor();
                    this.rCol = ammoTracerColor[0];
                    this.gCol = ammoTracerColor[1];
                    this.bCol = ammoTracerColor[2];
                });
            }
        });
        this.alpha = 0.9F;
    }

    private int getLifetimeFromConfig(ClientLevel world) {
        int configLife = RenderConfig.BULLET_HOLE_PARTICLE_LIFE.get();
        if (configLife <= 1) {
            return configLife;
        }
        return configLife + world.getRandom().nextInt(configLife / 2);
    }

    @Override
    protected void setSprite(TextureAtlasSprite sprite) {
        super.setSprite(sprite);
        this.uOffset = this.random.nextInt(16);
        this.vOffset = this.random.nextInt(16);
        // 材质应该都是方形
        this.textureDensity = (sprite.getU1() - sprite.getU0()) / 16.0F;
    }

    private static TextureAtlasSprite getSprite(ClientLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        // Preserve Forge model-data dependent particle textures, as native TerrainParticle does.
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
                .particleMaterial(world.getModelDataManager().getAt(pos)).sprite();
    }

    @Override
    protected float getU0() {
        return this.sprite.getU0() + this.uOffset * this.textureDensity;
    }

    @Override
    protected float getV0() {
        return this.sprite.getV0() + this.vOffset * this.textureDensity;
    }

    @Override
    protected float getU1() {
        return this.getU0() + this.textureDensity;
    }

    @Override
    protected float getV1() {
        return this.getV0() + this.textureDensity;
    }

    @Override
    public void tick() {
        super.tick();
        if (shouldRemove()) {
            this.remove();
        }
    }

    @Override
    public void extract(QuadParticleRenderState output, Camera camera, float partialTicks) {
        Vec3 view = camera.position();
        float particleX = (float) (Mth.lerp(partialTicks, this.xo, this.x) - view.x());
        float particleY = (float) (Mth.lerp(partialTicks, this.yo, this.y) - view.y());
        float particleZ = (float) (Mth.lerp(partialTicks, this.zo, this.z) - view.z());
        BulletHoleVisual.extract(output, direction, particleX, particleY, particleZ, getQuadSize(partialTicks),
                getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, alpha, age, lifetime,
                RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD.get());
    }

    @Override
    protected Layer getLayer() { return Layer.TRANSLUCENT_TERRAIN; }

    private boolean shouldRemove() {
        final BlockState blockState = this.level.getBlockState(this.pos);
        if (blockState.isAir()) {
            return true;
        } else {
            // 阻止弹孔在与方块不构成有效附着时继续渲染
            VoxelShape shape = blockState.getCollisionShape(this.level, this.pos);
            if (shape.isEmpty()) {
                return true;
            }
            AABB baseBlockBoundingBox = shape.bounds();
            AABB blockBoundingBox = baseBlockBoundingBox.move(this.pos);
            boolean intersects = blockBoundingBox.intersects(
                    this.x - 0.1, this.y - 0.1, this.z - 0.1,
                    this.x + 0.1, this.y + 0.1, this.z + 0.1);
            return !intersects;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<BulletHoleOption> {
        public Provider() {
        }

        @Override
        public BulletHoleParticle createParticle(@NotNull BulletHoleOption option, @NotNull ClientLevel world, double x, double y, double z, double pXSpeed, double pYSpeed, double pZSpeed, RandomSource random) {
            BulletHoleParticle particle = new BulletHoleParticle(world, x, y, z, option.getDirection(), option.getPos(), option.getAmmoId(), option.getGunId(), option.getGunDisplayId());
            return particle;
        }
    }
}
