package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.client.renderer.RenderSubmission;
import net.minecraft.util.Util;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class StatueRenderer implements BlockEntityRenderer<StatueBlockEntity, StatueRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public static class State extends BlockEntityRenderState {
        public @Nullable BedrockModel model;
        public @Nullable RenderType renderType;
        public float rotation;
        public double itemOffset;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    public StatueRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.STATUE_MODEL_LOCATION);
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(StatueBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.model = null;
        state.renderType = null;
        state.item.clear();
        Level level = blockEntity.getLevel();
        if (level == null) return;
        state.model = getModel().orElse(null);
        Direction facing = blockEntity.getBlockState().getValue(TargetBlock.FACING);
        state.rotation = (facing.get2DDataValue() + 2) % 4 * 90;
        state.itemOffset = Math.sin(Util.getMillis() / 500.0) * 0.1;
        state.renderType = RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get() ?
                RenderTypes.entityTranslucent(getTextureLocation()) : RenderTypes.entityCutout(getTextureLocation());
        itemModelResolver.updateForTopItem(state.item, blockEntity.getGunItem(), ItemDisplayContext.FIXED, level, null, 0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.model == null || state.renderType == null) return;
        poseStack.pushPose();
        try (var scope = RenderSubmission.enter(collector)) {
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.YN.rotationDegrees(state.rotation));
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            state.model.render(poseStack, ItemDisplayContext.NONE, state.renderType, state.lightCoords, OverlayTexture.NO_OVERLAY);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            poseStack.translate(0, -0.875, -1.2);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            poseStack.translate(0, state.itemOffset, 0);
            state.item.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        } finally {
            poseStack.popPose();
        }
    }

    public static Identifier getTextureLocation() {
        return InternalAssetLoader.STATUE_TEXTURE_LOCATION;
    }

    @Override
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public boolean shouldRender(StatueBlockEntity pBlockEntity, Vec3 pCameraPos) {
        return Vec3.atCenterOf(pBlockEntity.getBlockPos().above()).closerThan(pCameraPos, this.getViewDistance());
    }
}
