package com.tacz.guns.api.event.common;

import com.tacz.guns.api.event.GunEvent;

public interface KubeJSGunEventPoster<E extends GunEvent> {
    default void postEventToKubeJS(E event) {
        GunEvent.postScriptEvent(event, GunEvent.ScriptScope.COMMON);
    }

    //客户端事件应调用此方法
    default void postClientEventToKubeJS(E event) {
        GunEvent.postScriptEvent(event, GunEvent.ScriptScope.CLIENT);
    }

    //服务端事件应调用此方法
    default void postServerEventToKubeJS(E event) {
        GunEvent.postScriptEvent(event, GunEvent.ScriptScope.SERVER);
    }
}
