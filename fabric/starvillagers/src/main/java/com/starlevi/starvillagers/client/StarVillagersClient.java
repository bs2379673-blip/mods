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
        com.starlevi.starscenes.client.ClientScenes.playerSources = StarVillagers.animSources;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("starbody")) {
            VillagerHair.init();
        }
        // (los aldeanos implementan SceneActor gracias al mixin)
        EntityRendererProvider provider = ctx -> new NpcRenderer(ctx);
        EntityRendererRegistry.register(EntityType.VILLAGER, provider);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.starlevi.starvillagers.family.FamilyNet.Tree.TYPE,
                (msg, ctx) -> ctx.client().setScreen(new FamilyTreeScreen(msg)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.starlevi.starvillagers.look.Wardrobe.Open.TYPE,
                (msg, ctx) -> ctx.client().setScreen(new com.starlevi.starvillagers.client.library.LibraryScreen(msg.entity(), msg.name())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(VillagerTextures::clear));
    }
}
