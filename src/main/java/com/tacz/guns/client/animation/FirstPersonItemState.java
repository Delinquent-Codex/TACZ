/*
 * Derived from SimpleBedrockModel 2.2.2 FirstPersonRenderHandler by its contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Ported for TACZ Forge 26.2 on 2026-09-09; see META-INF/licenses/SimpleBedrockModel-NOTICE.txt.
 */
package com.tacz.guns.client.animation;

import com.tacz.guns.api.client.animation.IFPAnimationInstance;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** Owns draw/put-away transitions; the caller supplies renderer lookup and the frame clock. */
public final class FirstPersonItemState {
    private final Function<ItemStack, IFPAnimationInstance> createInstance;
    private final Predicate<ItemStack> custom;
    private final ToLongFunction<ItemStack> duration;
    private final BiPredicate<ItemStack, ItemStack> sameItem;
    private final LongSupplier clock;
    private int selectedSlot = -1;
    private ItemStack mainHand = ItemStack.EMPTY;
    private ItemStack pending = ItemStack.EMPTY;
    private IFPAnimationInstance active;
    private IFPAnimationInstance previous;
    private boolean transitioning;
    private boolean nextCustom;
    private boolean forceSwap;
    private long start;
    private long sheatheDuration;

    public FirstPersonItemState(Function<ItemStack, IFPAnimationInstance> createInstance, Predicate<ItemStack> custom,
                               ToLongFunction<ItemStack> duration, BiPredicate<ItemStack, ItemStack> sameItem, LongSupplier clock) {
        this.createInstance = createInstance;
        this.custom = custom;
        this.duration = duration;
        this.sameItem = sameItem;
        this.clock = clock;
    }

    public void reset() {
        selectedSlot = -1;
        mainHand = ItemStack.EMPTY;
        pending = ItemStack.EMPTY;
        active = previous = null;
        transitioning = nextCustom = forceSwap = false;
        start = sheatheDuration = 0;
    }

    public void forceHandSwap() {
        forceSwap = true;
    }

    public void tick(int slot, ItemStack item) {
        boolean slotChanged = slot != selectedSlot || forceSwap;
        boolean itemChanged = !sameItem.test(mainHand, item);
        forceSwap = false;
        selectedSlot = slot;
        mainHand = item;
        if (slotChanged || itemChanged) {
            pending = item;
            nextCustom = custom.test(item);
            if (!transitioning) {
                previous = active;
                active = createInstance.apply(item);
                if (previous != null) {
                    transitioning = true;
                    start = clock.getAsLong();
                    sheatheDuration = duration.applyAsLong(previous.currentItem());
                    previous.triggerPutAway();
                }
            }
        }
        if (active != null) {
            active.updateItem(item);
        }
        if (transitioning && (sheatheDuration <= 0 || clock.getAsLong() - start >= sheatheDuration)) {
            transitioning = false;
            active = createInstance.apply(pending);
            previous = null;
        }
    }

    public void frame(float partialTick) {
        IFPAnimationInstance current = activeInstance();
        if (current != null) {
            current.triggerDraw();
            current.tick(partialTick);
        }
    }

    public IFPAnimationInstance activeInstance() {
        return transitioning ? previous : active;
    }

    public boolean shouldLockVanilla() {
        return transitioning;
    }

    public float targetHeight() {
        return nextCustom ? 1 : 0;
    }
}
