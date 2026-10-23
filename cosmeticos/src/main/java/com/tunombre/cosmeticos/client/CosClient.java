package com.tunombre.cosmeticos.client;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** lado cliente: quien lleva que cosmetico; el dibujo lo hacen las capas (vanilla y Epic Fight) pegadas a los huesos */
public final class CosClient {
    private static final Map<UUID, Long> MASKS = new ConcurrentHashMap<>();

    private CosClient() { }

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CosClient::onLayers);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CosClient::onRenderers);
        MinecraftForge.EVENT_BUS.register(CarryClient.class);
        MinecraftForge.EVENT_BUS.register(CosClient.class);
        if (ModList.get().isLoaded("epicfight")) EfHook.register();
    }

    public static void set(UUID id, long mask) {
        if (mask == 0L) MASKS.remove(id); else MASKS.put(id, mask);
    }

    public static long mask(UUID id) {
        Long m = MASKS.get(id);
        return m == null ? 0L : m;
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(com.tunombre.cosmeticos.Cosmeticos.HOOK.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    private static void onLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            PlayerRenderer r = e.getSkin(skin);
            if (r != null) r.addLayer(new CosLayer(r));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        MASKS.clear();
        CosRender.clear();
    }
}
