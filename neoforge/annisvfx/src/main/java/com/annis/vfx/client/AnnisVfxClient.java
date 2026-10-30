package com.annis.vfx.client;

import com.annis.vfx.net.VfxPayload;
import com.annis.vfx.compat.ClientModInitializer;
import com.annis.vfx.compat.ClientPlayConnectionEvents;
import com.annis.vfx.compat.ClientPlayNetworking;
import com.annis.vfx.compat.HudRenderCallback;
import com.annis.vfx.compat.WorldRenderEvents;

public class AnnisVfxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(VfxPayload.ID, (p, ctx) -> VfxManager.play(p));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VfxManager::render);
        HudRenderCallback.EVENT.register(VfxManager::hud);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> VfxManager.clear());
    }
}
