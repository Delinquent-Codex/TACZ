/*
 * Derived from SimpleBedrockModel 2.2.2 FirstPersonRenderHandler by its contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Ported for TACZ Forge 26.2 on 2026-09-09; see META-INF/licenses/SimpleBedrockModel-NOTICE.txt.
 */
package com.tacz.guns.client.event;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.IFPAnimationInstance;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.client.renderer.IFPGeoItemRenderer;
import com.tacz.guns.api.client.renderer.TaczClientItemExtensions;
import com.tacz.guns.client.animation.FirstPersonItemState;
import com.tacz.guns.client.renderer.RenderSubmission;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public final class FirstPersonRenderHandler {
    private static final FirstPersonItemState STATE = new FirstPersonItemState(
            stack -> renderer(stack).map(r -> r.createAnimationInstance(stack, Minecraft.getInstance().getCameraEntity())).orElse(null),
            stack -> renderer(stack).isPresent(),
            stack -> renderer(stack).map(r -> r.getPutAwayDuration(stack)).orElse(0L),
            FirstPersonRenderHandler::sameItems, System::currentTimeMillis);

    private static Optional<IFPGeoItemRenderer> renderer(ItemStack stack) {
        return !stack.isEmpty() && TaczClientItemExtensions.getRenderer(stack) instanceof IFPGeoItemRenderer renderer
                ? Optional.of(renderer) : Optional.empty();
    }

    private static boolean sameItems(ItemStack oldItem, ItemStack newItem) {
        if (oldItem == newItem) return true;
        if (oldItem.isEmpty() || newItem.isEmpty()) return oldItem.isEmpty() && newItem.isEmpty();
        return renderer(oldItem).map(r -> r.isSameItem(oldItem, newItem)).orElseGet(() -> ItemStack.isSameItem(oldItem, newItem));
    }

    public static void reset() { STATE.reset(); }
    public static boolean shouldLockVanilla() { return STATE.shouldLockVanilla(); }
    public static float getTargetHeight() { return STATE.targetHeight(); }
    public static IFPAnimationInstance getActiveAnimationInstance() { return STATE.activeInstance(); }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    @SubscribeEvent
    public static void onSwap(SwapItemWithOffHand event) { STATE.forceHandSwap(); }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player != null) STATE.tick(player.getInventory().getSelectedSlot(), player.getMainHandItem());
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent.Pre event) { STATE.frame(event.timer().getGameTimeDeltaPartialTick(false)); }

    @SubscribeEvent
    public static boolean onRenderHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        IFPAnimationInstance instance = STATE.activeInstance();
        if (player == null || instance == null || instance.currentItem().isEmpty()) return false;
        ItemStack stack = instance.currentItem();
        var renderer = renderer(stack).orElse(null);
        if (renderer == null) return false;
        if (event.getHand() == InteractionHand.OFF_HAND) return renderer.blockOffhandRender();
        try (var submission = RenderSubmission.enter(event.getNodeCollector())) {
            renderer.renderFirstPerson(player, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    event.getPoseStack(), event.getNodeCollector(), event.getPackedLight(), event.getPartialTick());
        }
        return true;
    }
}
