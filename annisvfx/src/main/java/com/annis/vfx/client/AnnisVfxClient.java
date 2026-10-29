package com.annis.vfx.client;

import com.annis.vfx.net.VfxPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class AnnisVfxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(VfxPayload.ID, (p, ctx) -> VfxManager.play(p));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VfxManager::render);
        HudRenderCallback.EVENT.register(VfxManager::hud);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> VfxManager.clear());
    }
}
