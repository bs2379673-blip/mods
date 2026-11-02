package com.starlevi.starfest.client;

import com.starlevi.starfest.Registry;
import com.starlevi.starfest.block.ShowBlock;
import com.starlevi.starfest.client.render.BoothRenderer;
import com.starlevi.starfest.client.render.FloorRenderer;
import com.starlevi.starfest.client.render.LightRenderer;
import com.starlevi.starfest.net.Net;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Cliente de StarFest: musica, ritmo, dibujo de las luces y fuegos artificiales. */
public final class StarFestClient {
    private StarFestClient() {
    }

    public static void init(IEventBus modBus) {
        Net.client = new Net.ClientSide() {
            @Override
            public void play(Net.Play p) {
                Show.play(p);
            }

            @Override
            public void stop(Net.Stop p) {
                Show.stop(p);
            }

            @Override
            public void chunk(Net.Chunk p) {
                Show.chunk(p);
            }

            @Override
            public void fx(Net.Fx p) {
                if ("fuegos".equals(p.kind())) {
                    Fireworks.volley(p);
                }
            }
        };
        ShowBlock.ClientTick.launcher = Fireworks::launcherTick;
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(Registry.typeFor(ShowBlock.Kind.FOCO), ctx -> new LightRenderer(ctx, false));
            e.registerBlockEntityRenderer(Registry.typeFor(ShowBlock.Kind.REFLECTOR), ctx -> new LightRenderer(ctx, true));
            e.registerBlockEntityRenderer(Registry.typeFor(ShowBlock.Kind.PISO), FloorRenderer::new);
            e.registerBlockEntityRenderer(Registry.typeFor(ShowBlock.Kind.CABINA), BoothRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Show.tick();
            Fireworks.tick();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> Show.frame());
        NeoForge.EVENT_BUS.addListener(JukeboxWatcher::onPlay);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> Show.clear());
    }
}
