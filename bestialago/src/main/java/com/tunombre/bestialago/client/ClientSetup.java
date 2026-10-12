package com.tunombre.bestialago.client;

import com.tunombre.bestialago.BestiaLago;
import com.tunombre.bestialago.BestiaNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** Solo cliente: modelo, renderer, quick time event (HUD + tecla) y temblor de camara. */
public final class ClientSetup {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(BestiaLago.MODID, "bestia"), "main");

    private ClientSetup() { }

    public static void init(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(LAYER, BestiaModel::createBodyLayer));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(BestiaLago.BESTIA.get(), BestiaRenderer::new));
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("qte", (gui, g, pt, w, h) -> QteHud.render(g, pt)));
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onKey);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onMouse);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone e) -> { BestiaLago.clientHeld = false; });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) -> { BestiaLago.clientHeld = false; });
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onCamera);
    }

    private static int raw;
    private static boolean wasDown;

    /** el cliente cree estar en la boca de la bestia (no depende del paquete del QTE) */
    private static boolean held(Minecraft mc) {
        return mc.player != null && mc.screen == null && mc.player.getVehicle() instanceof com.tunombre.bestialago.Bestia;
    }

    private static void press() {
        BestiaNet.CH.sendToServer(new BestiaNet.Struggle());
        QteHud.pressed();
    }

    private static void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (!held(mc)) return;
        if (mc.options.keyJump.matches(e.getKey(), e.getScanCode())) { raw++; press(); }
    }

    /** tambien vale el click del raton */
    private static void onMouse(InputEvent.MouseButton.Pre e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        if (held(Minecraft.getInstance())) { raw++; press(); }
    }

    /** red de seguridad: si por lo que sea no llego el evento de teclado, se detecta por estado de la tecla cada tick */
    private static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean down = false;
        if (held(mc)) {
            com.mojang.blaze3d.platform.InputConstants.Key k = mc.options.keyJump.getKey();
            if (k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM)
                down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), k.getValue());
            if (down && !wasDown && raw == 0) press();
        }
        wasDown = down;
        raw = 0;
    }

    private static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        float k = QteHud.shakeIntensity();
        if (k <= 0) return;
        double t = e.getPartialTick() + (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.tickCount : 0);
        e.setRoll(e.getRoll() + (float) Math.sin(t * 1.9) * 4F * k);
        e.setPitch(e.getPitch() + (float) Math.sin(t * 2.7 + 1) * 1.5F * k);
        e.setYaw(e.getYaw() + (float) Math.sin(t * 2.3 + 2) * 1.5F * k);
    }
}
