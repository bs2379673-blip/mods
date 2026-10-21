package com.tunombre.jefes.client;

import com.tunombre.jefes.Jefes;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Solo cliente: renderer, teclas, camaras, HUD y screamer. */
public final class ClientSetup {
    private ClientSetup() { }

    public static void init(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(Jefes.JEFE.get(), JefeRenderer::new));
        bus.addListener((RegisterKeyMappingsEvent e) -> Control.register(e));
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("hud", (gui, g, pt, w, h) -> {
            Control.hud(g, w, h);
            ScreamFx.draw(g, w, h);
        }));
        var bus2 = MinecraftForge.EVENT_BUS;
        for (var ph : new TickEvent.Phase[]{TickEvent.Phase.START, TickEvent.Phase.END})
            bus2.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false, TickEvent.ClientTickEvent.class, e -> {
                if (e.phase == ph && (Control.active() || Cinematic.active())) Control.suppressOthers(!Control.active() && Cinematic.active());
            });
        bus2.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false, net.minecraftforge.client.event.InputEvent.MouseScrollingEvent.class, e -> { if (Control.active() || Cinematic.active()) e.setCanceled(true); });
        bus2.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) return;
            Control.tick();
            Cinematic.tick();
        });
        bus2.addListener((TickEvent.RenderTickEvent e) -> {
            if (e.phase != TickEvent.Phase.START) return;
            if (Cinematic.active()) Cinematic.camera(e.renderTickTime);
            else if (Control.active()) Control.camera(e.renderTickTime);
            else CamHolder.release();
        });
        bus2.addListener((MovementInputUpdateEvent e) -> {
            if (!Control.active() && !Cinematic.active()) return;
            var in = e.getInput();
            in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
            in.forwardImpulse = 0F;
            in.leftImpulse = 0F;
        });
        bus2.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            if (Cinematic.active()) { e.setCanceled(true); e.setSwingHand(false); return; }
            if (!Control.active()) return;
            e.setCanceled(true);
            e.setSwingHand(false);
            if (e.isAttack()) Control.onAttackUse(true);
            else if (e.isUseItem()) Control.onAttackUse(false);
        });
        bus2.addListener((RenderHandEvent e) -> { if (Control.active() || Cinematic.active()) e.setCanceled(true); });
        bus2.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> { Control.set(-1); Cinematic.set(-1); ClientBosses.clear(); com.tunombre.jefes.Bosses.CLIENT.clear(); });
        bus2.addListener((ClientPlayerNetworkEvent.Clone e) -> { Cinematic.set(-1); });
    }
}
