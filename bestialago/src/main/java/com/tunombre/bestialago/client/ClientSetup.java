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
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onCamera);
    }

    private static void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS || !QteHud.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (mc.options.keyJump.matches(e.getKey(), e.getScanCode())) {
            BestiaNet.CH.sendToServer(new BestiaNet.Struggle());
            QteHud.pressed();
        }
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
