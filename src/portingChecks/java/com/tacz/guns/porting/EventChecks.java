package com.tacz.guns.porting;

import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.event.GunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises real TACZ event classes and Forge buses in an isolated JVM. */
public final class EventChecks {
    private static int assertions;
    private static final List<String> calls = new ArrayList<>();
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message + ": " + calls);
    }

    public static final class Subscribers {
        @SubscribeEvent(priority = Priority.HIGH)
        public static void before(GunShootEvent event) { calls.add("high"); }

        @SubscribeEvent
        public static boolean cancel(GunShootEvent event) {
            calls.add("cancel");
            return event.getLogicalSide().isServer();
        }

        @SubscribeEvent(priority = Priority.LOW)
        public static void skipped(GunShootEvent event) { calls.add("low"); }

        @SubscribeEvent(priority = Priority.MONITOR)
        public static void monitor(GunShootEvent event, boolean cancelled) {
            calls.add("monitor:" + cancelled);
        }
    }

    public static void main(String[] args) {
        // No entity methods are used by this event contract test.
        GunShootEvent shot = new GunShootEvent(null, ItemStack.EMPTY, LogicalSide.SERVER);
        check(shot.getLogicalSide() == LogicalSide.SERVER && shot.getGunItemStack() == ItemStack.EMPTY,
                "payload identity and logical side");
        var subscribers = BusGroup.DEFAULT.register(MethodHandles.lookup(), Subscribers.class);
        check(GunShootEvent.BUS.post(shot), "native listener cancellation reaches publisher");
        check(calls.equals(List.of("high", "cancel", "monitor:true")), "priority, stop propagation and monitor");
        calls.clear();
        check(!GunShootEvent.BUS.post(new GunShootEvent(null, ItemStack.EMPTY, LogicalSide.CLIENT)), "conditional listener permits an event");
        check(calls.equals(List.of("high", "cancel", "low", "monitor:false")), "uncancelled event reaches remaining listeners");
        BusGroup.DEFAULT.unregister(subscribers);
        calls.clear();
        check(!GunShootEvent.BUS.post(new GunShootEvent(null, ItemStack.EMPTY, LogicalSide.CLIENT)),
                "unregistered listeners do not cancel subsequent events");

        var parent = RenderLevelBobEvent.BUS.addListener((RenderLevelBobEvent event) -> { calls.add("parent"); });
        var child = RenderLevelBobEvent.BobView.BUS.addListener((RenderLevelBobEvent.BobView event) -> { calls.add("view"); });
        check(!RenderLevelBobEvent.BobView.BUS.post(new RenderLevelBobEvent.BobView()), "uncancelled child");
        check(calls.size() == 2 && calls.containsAll(List.of("parent", "view")), "parent and concrete listeners receive child once");
        calls.clear();
        RenderLevelBobEvent.BobHurt.BUS.post(new RenderLevelBobEvent.BobHurt());
        check(calls.equals(List.of("parent")), "sibling event does not reach view listener");
        RenderLevelBobEvent.BUS.removeListener(parent);
        RenderLevelBobEvent.BobView.BUS.removeListener(child);

        AtomicBoolean scriptShouldCancel = new AtomicBoolean(true);
        GunEvent.installScriptDispatcher((event, scope) -> {
            calls.add("script:" + scope);
            if (scriptShouldCancel.get()) event.cancelFromScript();
        });
        calls.clear();
        var nativeListener = GunShootEvent.BUS.addListener((GunShootEvent event) -> { calls.add("native"); });
        var monitor = GunShootEvent.BUS.addListener((GunShootEvent event, boolean cancelled) -> calls.add("observed:" + cancelled));
        check(GunShootEvent.BUS.post(new GunShootEvent(null, ItemStack.EMPTY, LogicalSide.SERVER)), "script cancellation reaches publisher");
        check(calls.equals(List.of("script:COMMON", "observed:true")), "script executes before native listeners and monitor observes cancellation");
        calls.clear();
        scriptShouldCancel.set(false);
        check(!GunShootEvent.BUS.post(new GunShootEvent(null, ItemStack.EMPTY, LogicalSide.CLIENT)), "script cancellation isolated per event");
        check(calls.equals(List.of("script:COMMON", "native", "observed:false")), "uncancelled script/native dispatch order");
        calls.clear();
        SwapItemWithOffHand swap = new SwapItemWithOffHand();
        check(calls.equals(List.of("script:CLIENT")), "client script routing");
        boolean rejected = false;
        try { swap.cancelFromScript(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "noncancellable events reject cancellation");
        GunShootEvent.BUS.removeListener(nativeListener);
        GunShootEvent.BUS.removeListener(monitor);
        System.out.println("PASS: " + assertions + " event assertions against Forge EventBus 7.0.5; no KubeJS runtime or gameplay claimed.");
    }
}
