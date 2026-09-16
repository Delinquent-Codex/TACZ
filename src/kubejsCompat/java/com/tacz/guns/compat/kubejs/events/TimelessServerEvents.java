package com.tacz.guns.compat.kubejs.events;

import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypePredicate;
import com.tacz.guns.api.event.GunEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TimelessServerEvents implements TimelessKubeJSEventRegister{
    public static final TimelessServerEvents INSTANCE = new TimelessServerEvents();
    public static final Map<Class<? extends GunEvent>, Consumer<GunEvent>> EVENT_HANDLERS = new HashMap<>();
    public static final EventHandler ATTACHMENT_PROPERTY = INSTANCE.registerTimelessEvent(
            "attachmentProperty",
            GunKubeJSEvents.AttachmentPropertyEventJS.class,
            AttachmentPropertyEvent.class,
            GunKubeJSEvents.AttachmentPropertyEventJS::new
    );
    public static final EventHandler AMMO_HIT_BLOCK = INSTANCE.registerTimelessEvent(
            "ammoHitBlock",
            GunKubeJSEvents.AmmoHitBlockEventJS.class,
            AmmoHitBlockEvent.class,
            GunKubeJSEvents.AmmoHitBlockEventJS::new,
            true
    );

    private TimelessServerEvents() {}

    @Override
    public Map<Class<? extends GunEvent>, Consumer<GunEvent>> getEventHandlers() {
        return EVENT_HANDLERS;
    }

    @Override
    public <E extends GunEvent> void registerEventHandler(Class<E> eventClass, Consumer<GunEvent> eventPoster) {
        EVENT_HANDLERS.put(eventClass, eventPoster);
    }

    @Override
    public ScriptTypePredicate getScriptType() {
        return ScriptType.SERVER;
    }
}
