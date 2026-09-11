package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.tacz.guns.client.renderer.RenderSubmission;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.Optional;

public class EntityBulletRenderer extends EntityRenderer<EntityKineticBullet, EntityBulletRenderer.State> {
    public EntityBulletRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.DEFAULT_BULLET_MODEL);
    }

    public static class State extends EntityRenderState {
        public @Nullable BedrockAmmoModel ammoModel;
        public @Nullable Identifier ammoTexture;
        public @Nullable BedrockModel tracerModel;
        public @Nullable TracerGeometry tracer;
        public float yaw, pitch, red, green, blue;
        public boolean firstPerson;
        public float cameraYaw, cameraPitch, offsetX, offsetY, offsetZ;
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(EntityKineticBullet bullet, State state, float partialTicks) {
        super.extractRenderState(bullet, state, partialTicks);
        state.ammoModel = null;
        state.ammoTexture = null;
        state.tracerModel = null;
        state.tracer = null;
        state.firstPerson = false;
        var display = TimelessAPI.getGunDisplay(bullet.getGunDisplayId(), bullet.getGunId()).orElse(null);
        if (display == null) return;
        var ammoIndex = TimelessAPI.getClientAmmoIndex(bullet.getAmmoId()).orElse(null);
        if (ammoIndex == null) return;
        state.yaw = Mth.lerp(partialTicks, bullet.yRotO, bullet.getYRot()) - 180;
        state.pitch = Mth.lerp(partialTicks, bullet.xRotO, bullet.getXRot());
        state.ammoModel = ammoIndex.getAmmoEntityModel();
        state.ammoTexture = ammoIndex.getAmmoEntityTextureLocation();
        if (!bullet.isTracerAmmo()) return;
        Entity shooter = bullet.getOwner();
        if (shooter == null) return;
        state.firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson() && shooter instanceof LocalPlayer;
        if (state.firstPerson && !RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get()) return;
        state.tracerModel = getModel().orElse(null);
        if (state.tracerModel == null) return;
        float[] color = Objects.requireNonNullElse(bullet.getTracerColorOverride().orElse(display.getTracerColor()), ammoIndex.getTracerColor());
        state.red = color[0]; state.green = color[1]; state.blue = color[2];
        Vec3 position = bullet.getPosition(partialTicks);
        double distance = position.distanceTo(shooter.getEyePosition(partialTicks));
        state.tracer = TracerGeometry.calculate(bullet.getDeltaMovement().length(), distance,
                position.distanceTo(shooter.getEyePosition()), bullet.tickCount, bullet.getTracerSizeOverride());
        if (state.firstPerson) {
            Vector3f offset = bullet.getFirstPersonRenderOffset();
            if (offset == null) {
                Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
                offset = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                bullet.setCameraXRot(camera.xRot());
                bullet.setCameraYRot(camera.yRot());
                bullet.setFirstPersonRenderOffset(offset);
            }
            state.offsetX = offset.x; state.offsetY = offset.y; state.offsetZ = offset.z;
            state.cameraPitch = bullet.getCameraXRot();
            state.cameraYaw = bullet.getCameraYRot();
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        try (var scope = RenderSubmission.enter(collector)) {
            if (state.ammoModel != null && state.ammoTexture != null) {
                // Keep the baseline's outer model rotation, also inherited by its tracer path.
                rotate(state, pose);
                pose.pushPose();
                try {
                    pose.translate(0, 1.5, 0);
                    pose.scale(-1, -1, 1);
                    state.ammoModel.render(pose, ItemDisplayContext.GROUND, TaczRenderTypes.entityTranslucentCull(state.ammoTexture),
                            state.lightCoords, OverlayTexture.NO_OVERLAY);
                } finally {
                    pose.popPose();
                }
            }
            if (state.tracer != null && state.tracer.visible() && state.tracerModel != null) submitTracer(state, pose);
        } finally {
            pose.popPose();
        }
    }

    private static void rotate(State state, PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
        pose.mulPose(Axis.XP.rotationDegrees(state.pitch));
    }

    private void submitTracer(State state, PoseStack pose) {
        TracerGeometry tracer = state.tracer;
        if (state.firstPerson) {
            pose.mulPose(Axis.YN.rotationDegrees(state.cameraYaw + 180));
            pose.mulPose(Axis.XN.rotationDegrees(state.cameraPitch));
            pose.translate(state.offsetX * tracer.offsetScale(), state.offsetY * tracer.offsetScale(), state.offsetZ * tracer.offsetScale());
            pose.mulPose(Axis.XP.rotationDegrees(state.cameraPitch));
            pose.mulPose(Axis.YP.rotationDegrees(state.cameraYaw + 180));
        }
        rotate(state, pose);
        pose.translate(0, state.firstPerson ? 0 : -0.2, tracer.length() / 2);
        pose.scale(tracer.width(), tracer.width(), (float) tracer.length());
        state.tracerModel.render(pose, ItemDisplayContext.NONE, RenderTypes.energySwirl(InternalAssetLoader.DEFAULT_BULLET_TEXTURE, 15, 15),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.red, state.green, state.blue, 1);
    }

    @Override
    protected int getBlockLightLevel(@NotNull EntityKineticBullet entityBullet, @NotNull BlockPos blockPos) {
        return 15;
    }

    @Override
    public boolean shouldRender(EntityKineticBullet bullet, Frustum camera, double pCamX, double pCamY, double pCamZ) {
        AABB aabb = bullet.getBoundingBoxForCulling().inflate(0.5);
        if (aabb.hasNaN() || aabb.getSize() == 0) {
            aabb = new AABB(bullet.getX() - 2.0, bullet.getY() - 2.0, bullet.getZ() - 2.0, bullet.getX() + 2.0, bullet.getY() + 2.0, bullet.getZ() + 2.0);
        }
        return camera.isVisible(aabb);
    }

}
