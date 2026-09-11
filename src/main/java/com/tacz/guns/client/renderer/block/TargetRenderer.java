package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.client.renderer.RenderSubmission;
import com.tacz.guns.client.renderer.VertexCapture;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class TargetRenderer implements BlockEntityRenderer<TargetBlockEntity, TargetRenderer.State> {
    private static final String UPPER_NAME = "target_upper";
    private static final String HEAD_NAME = "head";
    private final PlayerSkinRenderCache skins;

    public static class State extends BlockEntityRenderState {
        public @Nullable BedrockModel model;
        public @Nullable Identifier skin;
        public float rotation;
        public float hitDegrees;
    }

    public TargetRenderer(BlockEntityRendererProvider.Context context) {
        skins = context.playerSkinRenderCache();
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MODEL_LOCATION);
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(TargetBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.model = getModel().orElse(null);
        state.rotation = blockEntity.getBlockState().getValue(TargetBlock.FACING).get2DDataValue() * 90;
        state.hitDegrees = -Mth.lerp(partialTick, blockEntity.oRot, blockEntity.rot);
        var owner = blockEntity.getOwnerProfile();
        state.skin = owner == null ? null : skins.getOrDefault(owner).playerSkin().body().texturePath();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BedrockModel model = state.model;
        if (model == null) return;
        BedrockPart headModel = model.getNode(HEAD_NAME);
        BedrockPart upperModel = model.getNode(UPPER_NAME);
        float oldRotation = upperModel.xRot;
        boolean oldVisibility = headModel.visible;
        poseStack.pushPose();
        try (var scope = RenderSubmission.enter(collector)) {
            upperModel.xRot = (float) Math.toRadians(state.hitDegrees);
            headModel.visible = false;
            poseStack.translate(0.5, 0.225, 0.5);
            poseStack.mulPose(Axis.YN.rotationDegrees(state.rotation));
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            poseStack.translate(0, -1.275, 0.0125);
            RenderType renderType = RenderTypes.entityTranslucent(InternalAssetLoader.TARGET_TEXTURE_LOCATION);
            model.render(poseStack, ItemDisplayContext.NONE, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY);
            if (state.skin != null) {
                poseStack.translate(0, 1.25, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(state.hitDegrees));
                headModel.visible = true;
                VertexCapture vertices = new VertexCapture();
                headModel.render(poseStack, ItemDisplayContext.NONE, vertices, state.lightCoords, OverlayTexture.NO_OVERLAY);
                RenderSubmission.submit(RenderTypes.entityCutout(state.skin), vertices.drain());
            }
        } finally {
            upperModel.xRot = oldRotation;
            headModel.visible = oldVisibility;
            poseStack.popPose();
        }
    }

    @Override
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
