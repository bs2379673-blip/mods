package com.starlevi.starscenes.api;

import com.starlevi.starscenes.client.NpcRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Para el cliente de otros mods: dibujar sus entidades con el motor de StarScenes. */
public final class StarScenesClientAPI {
    private StarScenesClientAPI() {
    }

    /**
     * Algo extra que se dibuja pegado a la cabeza de los actores (por ejemplo, el pelo con fisicas de
     * StarBody). headPose esta en el espacio de la cabeza de un modelo de jugador de Minecraft, en
     * pixeles (el cuello en 0,0,0; arriba es -y; adelante es -z).
     */
    @FunctionalInterface
    public interface HeadLayer {
        void submit(Entity actor, float partial, com.mojang.blaze3d.vertex.PoseStack headPose,
                    net.minecraft.client.renderer.SubmitNodeCollector collector, int light);
    }

    public static final java.util.List<HeadLayer> HEAD_LAYERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addHeadLayer(HeadLayer layer) {
        HEAD_LAYERS.add(layer);
    }

    /** La entidad (que implementa SceneActor) se dibuja y se anima con su modelo de Blockbench. */
    public static <T extends Entity & SceneActor> void register(EntityType<T> type) {
        EntityRendererRegistry.register(type, ctx -> new NpcRenderer<T>(ctx));
    }
}
