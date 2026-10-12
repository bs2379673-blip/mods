package com.tunombre.interacciones.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tunombre.interacciones.Act;
import com.tunombre.interacciones.Interacciones;
import com.tunombre.interacciones.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    public static final KeyMapping INTERACT = new KeyMapping("key.interacciones.interact", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.interacciones");
    public static final KeyMapping ACCEPT = new KeyMapping("key.interacciones.accept", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.interacciones");
    public static final KeyMapping DECLINE = new KeyMapping("key.interacciones.decline", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.interacciones");

    private ClientSetup() { }

    public static void init(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent e) -> { e.register(INTERACT); e.register(ACCEPT); e.register(DECLINE); });
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("prompt", (gui, g, pt, w, h) -> PromptHud.render(g, w, h)));
        bus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> MenuScreens.register(Interacciones.TRADE.get(), TradeScreen::new)));
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTick);
    }

    /** a quien se esta mirando (hasta 4.5 bloques): un jugador cercano (apuntando bien o no), o directo al
     * crosshair cualquier otra entidad viva -asi se puede abrir el menu sobre un NPC (p.ej. de Easy NPC) para
     * probar las interacciones sin otro jugador real; el servidor es quien decide si esa entidad vale. */
    static LivingEntity looked(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && le != mc.player && mc.player.distanceTo(le) < 4.5) return le;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1F);
        Player best = null;
        double bd = 4.5;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            Vec3 to = p.position().add(0, 1.0, 0).subtract(eye);
            double d = to.length();
            if (d < bd && to.normalize().dot(look) > 0.93) { bd = d; best = p; }
        }
        return best;
    }

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // el cargado mira hacia donde mira quien lo lleva (si no, el cuerpo doblado giraria con la camara)
        for (Player p : mc.level.players()) {
            if (p.getVehicle() instanceof Player v) {
                p.yBodyRot = v.yBodyRot;
                p.yBodyRotO = v.yBodyRotO;
            }
        }
        while (INTERACT.consumeClick()) {
            if (mc.screen != null) continue;
            if (mc.player.getFirstPassenger() instanceof Player) {      // cargando a alguien: soltarlo
                Net.CH.sendToServer(new Net.Request(Act.DROP, -1));
                continue;
            }
            LivingEntity t = looked(mc);
            if (t == null) mc.player.displayClientMessage(Component.literal("Mira a un jugador (o NPC) que este cerca"), true);
            else mc.setScreen(new InteractScreen(t));
        }
        while (ACCEPT.consumeClick()) if (PromptHud.active()) { Net.CH.sendToServer(new Net.Answer(true)); PromptHud.clear(); }
        while (DECLINE.consumeClick()) if (PromptHud.active()) { Net.CH.sendToServer(new Net.Answer(false)); PromptHud.clear(); }
    }

    static Entity dummy() { return null; }
}
