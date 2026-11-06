package com.starlevi.starframes.client;

import com.starlevi.starframes.StarFrames;
import com.starlevi.starframes.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Arranque del cliente: dibuja las pantallas, reproduce los canales y abre los menus. */
public final class StarFramesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // el cliente arranca en el hilo que dibuja (WaterMedia sube los cuadros ahi)
        ClientChannels.renderThread = Thread.currentThread();
        BlockEntityRenderers.register(StarFrames.SCREEN_BE, ctx -> new ScreenRenderer());
        ClientPlayNetworking.registerGlobalReceiver(Net.ChannelState.TYPE, (p, ctx) -> ClientChannels.receive(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.ChannelGone.TYPE, (p, ctx) -> ClientChannels.gone(p.id()));
        ClientPlayNetworking.registerGlobalReceiver(Net.OpenScreen.TYPE, (p, ctx) -> Minecraft.getInstance().setScreen(new ScreenMenu(p.pos())));
        ClientPlayNetworking.registerGlobalReceiver(Net.OpenRemote.TYPE, (p, ctx) -> Minecraft.getInstance().setScreen(new RemoteMenu(p.channel())));
        ClientTickEvents.END_CLIENT_TICK.register(ClientChannels::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(ClientChannels::disconnect));
    }
}
