package com.starlevi.starscenes.client;

import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.entity.NpcEntity;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Arranque del cliente: recibe modelos y skins, dibuja los NPCs y abre su menu. */
public final class StarScenesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(StarScenes.NPC, NpcRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Net.Index.TYPE, (p, ctx) -> ClientScenes.receive(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.Chunk.TYPE, (p, ctx) -> ClientScenes.chunk(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.Play.TYPE, (p, ctx) -> {
            if (Minecraft.getInstance().level != null) {
                Entity e = Minecraft.getInstance().level.getEntity(p.entity());
                if (e instanceof NpcEntity npc) {
                    NpcRenderer.trigger(npc, p.anim());
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.OpenMenu.TYPE, (p, ctx) -> Minecraft.getInstance().setScreen(new NpcScreen(p.entity())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientScenes.disconnect());
    }
}
