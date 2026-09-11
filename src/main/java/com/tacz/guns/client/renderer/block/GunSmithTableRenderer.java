package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.client.renderer.RenderSubmission;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class GunSmithTableRenderer implements BlockEntityRenderer<GunSmithTableBlockEntity, GunSmithTableRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public @Nullable BedrockModel model;
        public @Nullable RenderType renderType;
        public float rotation;
    }

    public GunSmithTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    public Optional<ClientBlockIndex> getIndex(GunSmithTableBlockEntity blockEntity) {
        Identifier id = blockEntity.getId();
        if (id==null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
            return Optional.empty();
        }
        return TimelessAPI.getClientBlockIndex(id);
    }

    public static Optional<ClientBlockIndex> getIndex(ItemStack stack) {
        if (stack.getItem() instanceof IBlock iBlock) {
            Identifier id = iBlock.getBlockId(stack);
            if (id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
                return Optional.empty();
            }
            return TimelessAPI.getClientBlockIndex(id);
        }
        return Optional.empty();
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(GunSmithTableBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.model = null;
        state.renderType = null;
        BlockState blockState = blockEntity.getBlockState();
        if (!(blockState.getBlock() instanceof AbstractGunSmithTableBlock block) || !block.isRoot(blockState)) return;
        state.rotation = block.parseRotation(blockState.getValue(AbstractGunSmithTableBlock.FACING));
        getIndex(blockEntity).ifPresent(index -> {
            state.model = index.getModel();
            Identifier texture = index.getTexture();
            state.renderType = RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get() ?
                    RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutout(texture);
        });
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.model == null || state.renderType == null) return;
        poseStack.pushPose();
        try (var scope = RenderSubmission.enter(collector)) {
            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            poseStack.mulPose(Axis.YN.rotationDegrees(state.rotation));
            state.model.render(poseStack, ItemDisplayContext.NONE, state.renderType, state.lightCoords, OverlayTexture.NO_OVERLAY);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
