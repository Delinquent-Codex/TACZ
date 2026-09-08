package com.tacz.guns.api.event;

import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.InheritableEvent;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
import net.minecraftforge.eventbus.api.listener.Priority;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Base for TACZ's extensible Forge events. Native listeners cancel by returning
 * true. The separate script flag transfers constructor-time script cancellation
 * into the native bus before ordinary listeners run.
 */
public abstract class GunEvent extends MutableEvent implements InheritableEvent {
    public enum ScriptScope { COMMON, CLIENT, SERVER }

    private static volatile BiConsumer<GunEvent, ScriptScope> scriptDispatcher;
    private boolean cancelledByScript;

    /** Installed only by the optional KubeJS integration during mod construction. */
    @ApiStatus.Internal
    public static void installScriptDispatcher(BiConsumer<GunEvent, ScriptScope> dispatcher) {
        scriptDispatcher = Objects.requireNonNull(dispatcher);
    }

    @ApiStatus.Internal
    public static void postScriptEvent(GunEvent event, ScriptScope scope) {
        var dispatcher = scriptDispatcher;
        if (dispatcher != null) {
            dispatcher.accept(event, scope);
        }
    }

    /** Called by the script wrapper; native Forge listeners return a boolean. */
    @ApiStatus.Internal
    public final void cancelFromScript() {
        if (!(this instanceof Cancellable)) {
            throw new IllegalStateException("This event cannot be cancelled: " + getClass().getName());
        }
        cancelledByScript = true;
    }

    protected static <E extends GunEvent & Cancellable> CancellableEventBus<E> cancellableBus(Class<E> type) {
        CancellableEventBus<E> bus = CancellableEventBus.create(type);
        bus.addListener(Priority.HIGHEST, (E event) -> ((GunEvent) event).cancelledByScript);
        return bus;
    }
}
