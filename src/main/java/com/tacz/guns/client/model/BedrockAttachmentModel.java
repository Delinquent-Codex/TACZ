package com.tacz.guns.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.model.functional.TextShowRender;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.client.renderer.RenderSubmission;
import com.tacz.guns.client.renderer.TaczRenderTypes;
import com.tacz.guns.client.renderer.VertexCapture;
import com.tacz.guns.client.renderer.scope.ScopeAperture;
import com.tacz.guns.client.renderer.scope.ScopeCapture;
import com.tacz.guns.client.renderer.scope.ScopeSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BedrockAttachmentModel extends BedrockAnimatedModel {
    private static final String SCOPE_VIEW_NODE = "scope_view";
    private static final String SCOPE_BODY_NODE = "scope_body";
    private static final String OCULAR_RING_NODE = "ocular_ring";
    private static final String DIVISION_NODE = "division";
    private static final String OCULAR_NODE = "ocular";
    private static final String OCULAR_SIGHT_NODE = "ocular_sight";
    private static final String OCULAR_SCOPE_NODE = "ocular_scope";
    private static final Pattern LASER_BEAM_PATTERN = Pattern.compile("^laser_beam(_(\\d+))?$");

    protected List<List<BedrockPart>> scopeViewPaths;
    protected @Nullable List<BedrockPart> scopeBodyPath;
    protected @Nullable List<BedrockPart> ocularRingPath;
    protected List<List<BedrockPart>> ocularNodePaths;
    protected List<Boolean> isScopeOcular;
    protected List<List<BedrockPart>> divisionNodePaths;
    protected @Nullable List<List<BedrockPart>> laserBeamPaths;

    private @Nullable ItemStack currentGunItem;
    private @Nullable ItemStack attachmentItem;

    private boolean isScope = false;
    private boolean isSight = false;
    private float scopeViewRadiusModifier = 1;

    public BedrockAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        scopeViewPaths = new ArrayList<>();
        ocularNodePaths = new ArrayList<>();
        isScopeOcular = new ArrayList<>();
        divisionNodePaths = new ArrayList<>();
        laserBeamPaths = new ArrayList<>();
        // 初始化 view 的 node path
        List<BedrockPart> path = getPath(modelMap.get(SCOPE_VIEW_NODE));
        int i = 2;
        while (path != null) {
            scopeViewPaths.add(path);
            path = getPath(modelMap.get(SCOPE_VIEW_NODE + '_' + i++));
        }
        // 初始化 ocular 的 node path
        String ocularRegex = "^(" + OCULAR_NODE + "|" + OCULAR_SIGHT_NODE + "|" + OCULAR_SCOPE_NODE + ")(_(\\d+))?$";
        Pattern ocularPattern = Pattern.compile(ocularRegex);
        TreeMap<Integer, OcularWrapper> map = new TreeMap<>();
        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            Matcher matcher = ocularPattern.matcher(entry.getKey());
            if (matcher.matches()) {
                int num = 1;
                String numStr = matcher.group(3);
                if (numStr != null) {
                    num = Integer.parseInt(numStr);
                }
                String type = matcher.group(1);
                boolean isScope = OCULAR_SCOPE_NODE.equals(type);
                map.put(num, new OcularWrapper(entry.getValue(), isScope));
            }
            if (LASER_BEAM_PATTERN.matcher(entry.getKey()).find()) {
                laserBeamPaths.add(getPath(entry.getValue()));
            }
        }
        for (OcularWrapper wrapper : map.values()) {
            ocularNodePaths.add(getPath(wrapper.renderer));
            isScopeOcular.add(wrapper.isScope);
        }
        // 初始化 division 的 node path
        ModelRendererWrapper divisionModel = modelMap.get(DIVISION_NODE);
        path = getPath(modelMap.get(DIVISION_NODE));
        i = 2;
        while (path != null) {
            divisionNodePaths.add(path);
            divisionModel.setHidden(true);
            divisionModel = modelMap.get(DIVISION_NODE + '_' + i++);
            path = getPath(divisionModel);
        }

        scopeBodyPath = getPath(modelMap.get(SCOPE_BODY_NODE));
        ocularRingPath = getPath(modelMap.get(OCULAR_RING_NODE));
    }

    @Nullable
    public List<BedrockPart> getScopeViewPath(int viewSwitchCount) {
        if (scopeViewPaths.isEmpty()) {
            return null;
        }
        if (viewSwitchCount >= scopeViewPaths.size()) {
            return scopeViewPaths.get(0);
        }
        return scopeViewPaths.get(viewSwitchCount);
    }

    public void setIsScope(boolean isScope) {
        this.isScope = isScope;
    }

    public void setIsSight(boolean isSight) {
        this.isSight = isSight;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    public void setScopeViewRadiusModifier(float scopeViewRadiusModifier) {
        this.scopeViewRadiusModifier = scopeViewRadiusModifier;
    }

    /**
     * 添加枪械自定义的文本显示
     */
    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach((name, textShow) -> this.setFunctionalRenderer(name,
                bedrockPart -> new TextShowRender(this, textShow, currentGunItem)));
    }

    public void render(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        if (transformType.firstPerson() && (isScope || isSight)) {
            ScopeCapture.submit(RenderSubmission.collector(), 0, () ->
                    renderContents(attachmentItem, currentGunItem, matrixStack, transformType, renderType, light, overlay));
        } else {
            renderContents(attachmentItem, currentGunItem, matrixStack, transformType, renderType, light, overlay);
        }
    }

    private void renderContents(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack matrixStack,
                                ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        this.currentGunItem = currentGunItem;
        this.attachmentItem = attachmentItem;
        if (transformType.firstPerson()) {
            if (isScope && isSight) {
                renderBoth(matrixStack, transformType, renderType, light, overlay);
            } else if (isScope) {
                renderScope(matrixStack, transformType, renderType, light, overlay);
            } else if (isSight) {
                renderSight(matrixStack, transformType, renderType, light, overlay);
            }
        } else {
            if (scopeBodyPath != null) {
                renderCapturedPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
            }
            if (ocularRingPath != null) {
                renderCapturedPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
            }
        }
        if (!isScope && !isSight && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry);
            }
        }
        super.render(matrixStack, transformType, renderType, light, overlay);
        if ((isScope || isSight) && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry);
            }
        }
    }

    private void renderCapturedPart(PoseStack poseStack, ItemDisplayContext context, RenderType type,
                                    int light, int overlay, List<BedrockPart> path) {
        BedrockPart part = path.getLast();
        boolean visible = part.visible;
        poseStack.pushPose();
        try {
            for (int i = 0; i < path.size() - 1; i++) path.get(i).translateAndRotateAndScale(poseStack);
            part.visible = true;
            VertexCapture vertices = new VertexCapture();
            part.render(poseStack, context, vertices, light, overlay);
            RenderSubmission.submit(type, vertices.drain());
        } finally {
            part.visible = visible;
            poseStack.popPose();
        }
    }

    private void renderNativeOptic(ScopeSequence.Mode mode, PoseStack pose, ItemDisplayContext context, RenderType type, int light, int overlay) {
        ScopeSequence.render(mode, new ScopeSequence.Parts() {
            @Override public int ocularCount() { return ocularNodePaths.size(); }
            @Override public int divisionCount() { return divisionNodePaths.size(); }
            @Override public boolean scopeOcular(int index) { return isScopeOcular.get(index); }
            @Override public void ring() { if (ocularRingPath != null) renderCapturedPart(pose, context, type, light, overlay, ocularRingPath); }
            @Override public void body() { if (scopeBodyPath != null) renderCapturedPart(pose, context, type, light, overlay, scopeBodyPath); }
            @Override public void ocular(int index) { renderCapturedPart(pose, context, type, light, overlay, ocularNodePaths.get(index)); }
            @Override public void division(int index) { renderCapturedPart(pose, context, type, light, overlay, divisionNodePaths.get(index)); }
            @Override public void aperture(int index) {
                Vector3f center = getBedrockPartCenter(pose, ocularNodePaths.get(index));
                LocalPlayer player = Minecraft.getInstance().player;
                float progress = player == null ? 1 : IClientPlayerGunOperator.fromLocalPlayer(player)
                        .getClientAimingProgress(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
                RenderSubmission.submit(TaczRenderTypes.scopeAperture(), ScopeAperture.capture(center.x(), center.y(), scopeViewRadiusModifier, progress));
            }
            @Override public void remaining() { BedrockAttachmentModel.super.render(pose, context, type, light, overlay); }
        });
    }

    private Vector3f getBedrockPartCenter(PoseStack poseStack, @Nonnull List<BedrockPart> path) {
        poseStack.pushPose();
        try {
            for (BedrockPart part : path) part.translateAndRotateAndScale(poseStack);
            return new Vector3f(poseStack.last().pose().m30(), poseStack.last().pose().m31(), poseStack.last().pose().m32());
        } finally {
            poseStack.popPose();
        }
    }

    private void renderBoth(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        renderNativeOptic(ScopeSequence.Mode.MIXED, matrixStack, transformType, renderType, light, overlay);
    }

    private void renderSight(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        renderNativeOptic(ScopeSequence.Mode.SIGHT, matrixStack, transformType, renderType, light, overlay);
    }

    private void renderScope(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        renderNativeOptic(ScopeSequence.Mode.SCOPE, matrixStack, transformType, renderType, light, overlay);
    }

    /**
     * Source-compatible entry point for the mixed optic subpass. Target rendering uses a native
     * ordered mask job; the old AR layer callbacks and immediate GL stencil state cannot own it.
     * This does not establish compatibility with an unavailable Forge 26.2 AR companion.
     */
    @Deprecated
    public void renderBothAccelerated(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        ScopeCapture.submit(RenderSubmission.collector(), 0, () ->
                renderNativeOptic(ScopeSequence.Mode.MIXED, matrixStack, transformType, renderType, light, overlay));
    }

    private static class OcularWrapper{
        public ModelRendererWrapper renderer;
        public boolean isScope;

        public OcularWrapper (ModelRendererWrapper renderer, boolean isScope){
            this.renderer = renderer;
            this.isScope = isScope;
        }
    }
}