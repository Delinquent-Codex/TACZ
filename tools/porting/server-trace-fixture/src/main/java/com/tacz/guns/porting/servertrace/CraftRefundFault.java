package com.tacz.guns.porting.servertrace;

import com.tacz.guns.api.item.IAmmo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.Map;

/** One-shot, test-only fault: reject the second physical stack of the first 22wmr craft. */
final class CraftRefundFault {
    private static int seen;

    private CraftRefundFault() {}

    static void install() {
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item))
                return false;
            var stack = item.getItem();
            if (!(stack.getItem() instanceof IAmmo ammo)
                    || !"tacz:22wmr".equals(ammo.getAmmoId(stack).toString()))
                return false;
            int index = ++seen;
            boolean cancel = index == 2;
            ServerTrace.recordCraftFault(Map.of(
                    "kind", "craft_output_join", "index", index,
                    "count", stack.getCount(), "cancelled", cancel));
            return cancel;
        });
    }
}
