package com.tacz.guns.client.event;

import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class RenderHeadShotAABB {
    @SubscribeEvent
    public static void onRenderEntity(RenderLivingEvent.Post<?, ?, ?> event) {
        boolean canRender = Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
        if (!canRender) {
            return;
        }
        if (!RenderConfig.HEAD_SHOT_DEBUG_HITBOX.get()) {
            return;
        }
        var state = event.getState();
        Identifier entityId = ForgeRegistries.ENTITY_TYPES.getKey(state.entityType);
        if (entityId == null) {
            return;
        }
        AABB aabb = HeadShotAABBConfigRead.getAABB(entityId);
        if (aabb == null) {
            float width = state.boundingBoxWidth;
            float eyeHeight = state.eyeHeight;
            // 扩张 0.01，避免和原版显示重合
            aabb = new AABB(-width / 2, eyeHeight - 0.25, -width / 2, width / 2, eyeHeight + 0.25, width / 2).inflate(0.01);
        }
        event.getNodeCollector().submitShapeOutline(event.getPoseStack(), Shapes.create(aabb), RenderTypes.lines(), 0xffffff00, 2, false);
    }
}
