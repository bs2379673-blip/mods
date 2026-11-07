package com.starlevi.starvillagers.client;

import com.starlevi.starscenes.client.NpcRenderer;
import com.starlevi.starvillagers.StarVillagers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;

/** Los aldeanos se dibujan como personas con el motor de StarScenes. */
public final class StarVillagersClient implements ClientModInitializer {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        StarVillagers.texture = VillagerTextures::texture;
        // (los aldeanos implementan SceneActor gracias al mixin)
        EntityRendererProvider provider = ctx -> new NpcRenderer(ctx);
        EntityRendererRegistry.register(EntityType.VILLAGER, provider);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(VillagerTextures::clear));
    }
}
