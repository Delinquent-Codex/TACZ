package com.tacz.guns.client.renderer.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.pojo.display.gun.LayerGunShow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Extracts inventory and pack data while the entity is available; submission owns only render state. */
public final class HumanoidOffhandRender {
    public record Gun(ItemStackRenderState item, LayerGunTransform transform) {}
    public record State(boolean localPlayer, boolean mainHandGun, List<Gun> carriedGuns) {
        public State { carriedGuns = List.copyOf(carriedGuns); }
    }

    public static State extract(LivingEntity entity, ItemModelResolver resolver) {
        List<Gun> guns = new ArrayList<>();
        ItemStack offhand = entity.getOffhandItem();
        if (IGun.getIGunOrNull(offhand) != null) {
            TimelessAPI.getGunDisplay(offhand).ifPresent(display -> add(guns, offhand, display.getOffhandShow(), entity, resolver));
        }
        if (entity instanceof Player player) {
            var inventory = player.getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (slot == inventory.getSelectedSlot()) continue;
                ItemStack stack = inventory.getItem(slot);
                if (IGun.getIGunOrNull(stack) == null) continue;
                int index = slot;
                TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
                    var hotbar = display.getHotbarShow();
                    if (hotbar != null && hotbar.containsKey(index)) add(guns, stack, hotbar.get(index), entity, resolver);
                });
            }
        }
        return new State(entity == Minecraft.getInstance().player, IGun.mainHandHoldGun(entity), guns);
    }

    private static void add(List<Gun> guns, ItemStack stack, LayerGunShow show, LivingEntity entity, ItemModelResolver resolver) {
        if (show == null) return;
        ItemStackRenderState item = new ItemStackRenderState();
        // Baseline renderStatic used the world/entity seed without a living owner.
        resolver.updateForTopItem(item, stack.copy(), ItemDisplayContext.FIXED, entity.level(), null, entity.getId());
        guns.add(new Gun(item, LayerGunTransform.capture(show.getPos(), show.getRotate(), show.getScale())));
    }

    public static void renderGun(State state, PoseStack pose, SubmitNodeCollector collector, int light, int outline) {
        for (Gun gun : state.carriedGuns()) {
            pose.pushPose();
            try {
                gun.transform().apply(pose);
                gun.item().submit(pose, collector, light, OverlayTexture.NO_OVERLAY, outline);
            } finally {
                pose.popPose();
            }
        }
    }
}
