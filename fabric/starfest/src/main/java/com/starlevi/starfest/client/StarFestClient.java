package com.starlevi.starfest.client;

import com.starlevi.starfest.Registry;
import com.starlevi.starfest.block.ShowBlock;
import com.starlevi.starfest.client.render.BoothRenderer;
import com.starlevi.starfest.client.render.FloorRenderer;
import com.starlevi.starfest.client.render.LightRenderer;
import com.starlevi.starfest.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Cliente de StarFest: musica, ritmo, dibujo de las luces y fuegos artificiales. */
public final class StarFestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Net.Play.TYPE, (p, ctx) -> Show.play(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.Stop.TYPE, (p, ctx) -> Show.stop(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.Chunk.TYPE, (p, ctx) -> Show.chunk(p));
        ClientPlayNetworking.registerGlobalReceiver(Net.Fx.TYPE, (p, ctx) -> {
            if ("fuegos".equals(p.kind())) {
                Fireworks.volley(p);
            }
        });
        ShowBlock.ClientTick.launcher = Fireworks::launcherTick;
        BlockEntityRenderers.register(Registry.typeFor(ShowBlock.Kind.FOCO), ctx -> new LightRenderer(false));
        BlockEntityRenderers.register(Registry.typeFor(ShowBlock.Kind.REFLECTOR), ctx -> new LightRenderer(true));
        BlockEntityRenderers.register(Registry.typeFor(ShowBlock.Kind.PISO), ctx -> new FloorRenderer());
        BlockEntityRenderers.register(Registry.typeFor(ShowBlock.Kind.CABINA), ctx -> new BoothRenderer());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Show.tick();
            Fireworks.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(Show::clear));
    }
}
