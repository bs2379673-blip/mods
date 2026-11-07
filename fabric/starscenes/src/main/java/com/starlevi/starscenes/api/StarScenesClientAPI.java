package com.starlevi.starscenes.api;

import com.starlevi.starscenes.client.NpcRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Para el cliente de otros mods: dibujar sus entidades con el motor de StarScenes. */
public final class StarScenesClientAPI {
    private StarScenesClientAPI() {
    }

    /** La entidad (que implementa SceneActor) se dibuja y se anima con su modelo de Blockbench. */
    public static <T extends Entity & SceneActor> void register(EntityType<T> type) {
        EntityRendererRegistry.register(type, ctx -> new NpcRenderer<T>(ctx));
    }
}
