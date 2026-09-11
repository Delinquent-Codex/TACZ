package com.tacz.guns.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.RenderSubmission;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.entity.TargetMinecart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class TargetMinecartRenderer extends AbstractMinecartRenderer<TargetMinecart, TargetMinecartRenderer.State> {
    private static final String HEAD_NAME = "head";
    private static final String HEAD_2_NAME = "head2";
    private final PlayerSkinRenderCache skins;

    public static class State extends MinecartRenderState {
        public @Nullable BedrockModel targetModel;
        public @Nullable Identifier skin;
        public boolean renderContents;
    }

    public TargetMinecartRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.TNT_MINECART);
        skins = context.getPlayerSkinRenderCache();
        shadowRadius = 0.25F;
    }

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MINECART_MODEL_LOCATION);
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(TargetMinecart entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.targetModel = getModel().orElse(null);
        var owner = entity.getOwnerProfile();
        state.skin = owner == null ? null : skins.getOrDefault(owner).playerSkin().body().texturePath();
        var displayBlock = entity.getDisplayBlockState();
        // Target blocks formerly used ENTITYBLOCK_ANIMATED. Their new invisible block model
        // must not suppress this custom content just because the vanilla baked model is empty.
        state.renderContents = displayBlock.getBlock() instanceof TargetBlock || displayBlock.getRenderShape() != RenderShape.INVISIBLE;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        // Keep the native entity decorations that the baseline superclass also rendered.
        if (state.leashStates != null) {
            for (var leash : state.leashStates) collector.submitLeash(pose, leash);
        }
        submitNameDisplay(state, pose, collector, camera);
        // TACZ hid the vanilla hull with an empty texture. The target hardcodes that hull
        // texture, so its model submission is replaced by the TACZ body below.
        if (!state.renderContents || state.targetModel == null) return;
        pose.pushPose();
        try (var scope = RenderSubmission.enter(collector)) {
            MinecartRenderPose.apply(state, pose);
            MinecartRenderPose.contents(state, pose);
            submitTarget(state, pose);
        } finally {
            pose.popPose();
        }
    }

    private void submitTarget(State state, PoseStack pose) {
        BedrockModel model = state.targetModel;
        BedrockPart head = model.getNode(HEAD_NAME);
        BedrockPart head2 = model.getNode(HEAD_2_NAME);
        boolean oldHead = head.visible;
        boolean oldHead2 = head2.visible;
        try {
            head.visible = false;
            head2.visible = false;
            pose.translate(0.5, 1.875, 0.5);
            pose.scale(1.5F, 1.5F, 1.5F);
            pose.mulPose(Axis.ZN.rotationDegrees(180));
            pose.mulPose(Axis.YN.rotationDegrees(90));
            model.render(pose, ItemDisplayContext.NONE, RenderTypes.entityTranslucent(InternalAssetLoader.TARGET_MINECART_TEXTURE_LOCATION),
                    state.lightCoords, OverlayTexture.NO_OVERLAY);
            if (state.skin != null) {
                pose.translate(0, 1, -4.5 / 16);
                VertexCapture vertices = new VertexCapture();
                head.visible = true;
                head.render(pose, ItemDisplayContext.NONE, vertices, state.lightCoords, OverlayTexture.NO_OVERLAY);
                head2.visible = true;
                pose.translate(0, 0, 0.01);
                head2.render(pose, ItemDisplayContext.NONE, vertices, state.lightCoords, OverlayTexture.NO_OVERLAY);
                RenderSubmission.submit(TaczRenderTypes.entityTranslucentCull(state.skin), vertices.drain());
            }
        } finally {
            head.visible = oldHead;
            head2.visible = oldHead2;
        }
    }
}
