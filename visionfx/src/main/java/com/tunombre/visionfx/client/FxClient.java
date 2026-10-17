package com.tunombre.visionfx.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tunombre.visionfx.Params;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

/** lado cliente: guarda lo que manda el servidor y dibuja el filtro como un pase de pantalla completa (un solo shader + copia de vuelta) */
public final class FxClient {
    public static final KeyMapping TOGGLE = new KeyMapping("key.visionfx.toggle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.visionfx");

    /** lo ultimo que mando el servidor (null = sin servidor con el mod: apagado) */
    private static volatile Params server;
    /** interruptor local para comparar (no cambia nada en el servidor ni en los demas) */
    private static boolean local = true;
    /** intensidad actual 0..1, suavizada para que no haya saltos */
    private static float cur;
    private static float darkSmooth = 1F;

    private static RenderTarget swap;
    private static PostPass fx, blit;
    private static int w, h;

    private FxClient() { }

    public static void init(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent e) -> e.register(TOGGLE));
        MinecraftForge.EVENT_BUS.register(FxClient.class);
    }

    public static void setServer(Params p) { server = p; }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { server = null; cur = 0F; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            local = !local;
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("VisionFX (solo para vos): " + (local ? "ENCENDIDO" : "APAGADO")), true);
        }
        Params p = server;
        float target = 0F;
        if (p != null && p.enabled && local && mc.player != null && mc.level != null) {
            target = p.strength;
            if (p.dark) {
                float light = mc.level.getMaxLocalRawBrightness(mc.player.blockPosition()) / 15F;
                darkSmooth += ((1F - light) - darkSmooth) * 0.05F;
                target *= darkSmooth;
            }
        }
        cur += Mth_clamp(target - cur, -0.04F, 0.04F);
    }

    private static float Mth_clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Params p = server;
        if (p == null || cur < 0.003F) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            ensure(mc, main);
        } catch (IOException ex) {
            server = null;
            return;
        }
        float k = cur;
        var u = fx.getEffect();
        u.safeGetUniform("Contrast").set(p.contrast * k);
        u.safeGetUniform("Desaturate").set(Math.min(1F, p.desaturate * k));
        u.safeGetUniform("Brightness").set(1F + (p.brightness - 1F) * k);
        u.safeGetUniform("Chromatic").set(p.chromatic * k);
        u.safeGetUniform("Vignette").set(p.vignette * k);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        float pt = e.getPartialTick();
        fx.process(pt);
        blit.process(pt);
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }

    private static void ensure(Minecraft mc, RenderTarget main) throws IOException {
        if (swap != null && w == main.width && h == main.height && fx != null) return;
        free();
        w = main.width; h = main.height;
        swap = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        swap.setClearColor(0F, 0F, 0F, 0F);
        Matrix4f ortho = new Matrix4f().setOrtho(0F, w, 0F, h, 0.1F, 1000F);
        fx = new PostPass(mc.getResourceManager(), "visionfx", main, swap);
        fx.setOrthoMatrix(ortho);
        blit = new PostPass(mc.getResourceManager(), "blit", swap, main);
        blit.setOrthoMatrix(ortho);
    }

    private static void free() {
        if (fx != null) fx.close();
        if (blit != null) blit.close();
        if (swap != null) swap.destroyBuffers();
        fx = null; blit = null; swap = null;
    }
}
