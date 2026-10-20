package com.tunombre.visionfx.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tunombre.visionfx.Params;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostPass;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.io.IOException;

/** lado cliente: guarda lo que manda el servidor y dibuja el filtro como un pase de pantalla completa (un solo shader + copia de vuelta) */
public final class FxClient {
    /** lo ultimo que mando el servidor (null = sin servidor con el mod: apagado) */
    private static volatile Params server;
    /** intensidad actual 0..1, suavizada para que no haya saltos */
    private static float cur;
    private static float darkSmooth = 1F;

    private static float ps1Cur, modeCur;
    private static int modeId;
    private static final long T0 = System.nanoTime();

    private static RenderTarget swap;
    private static PostPass fx, blit, ps1Pass, modePass;
    private static int w, h;

    private FxClient() { }

    public static void init(IEventBus bus) {
        MinecraftForge.EVENT_BUS.register(FxClient.class);
    }

    public static void setServer(Params p) { server = p; }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { server = null; cur = 0F; ps1Cur = 0F; modeCur = 0F; modeId = 0; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Params p = server;
        float target = 0F;
        if (p != null && p.enabled && mc.player != null && mc.level != null) {
            target = p.strength;
            if (p.dark) {
                float light = mc.level.getMaxLocalRawBrightness(mc.player.blockPosition()) / 15F;
                darkSmooth += ((1F - light) - darkSmooth) * 0.05F;
                target *= darkSmooth;
            }
        }
        cur += Mth_clamp(target - cur, -0.04F, 0.04F);
        float t1 = (p != null && p.ps1 && mc.player != null && mc.level != null) ? 1F : 0F;
        ps1Cur += Mth_clamp(t1 - ps1Cur, -0.2F, 0.2F);
        int wantMode = (p != null && mc.player != null && mc.level != null) ? p.mode : 0;
        if (wantMode != 0) modeId = wantMode;
        modeCur += Mth_clamp((wantMode != 0 ? 1F : 0F) - modeCur, -0.08F, 0.08F);
        if (modeCur <= 0F && wantMode == 0) modeId = 0;
    }

    private static float Mth_clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }

    /** HIGHEST: corre antes que otros mods que tambien usan AFTER_WEATHER (ej: la linterna de Survivors Arsenal). Asi el filtro se aplica al mundo y la luz de la linterna se dibuja despues, sin que le subamos el contraste. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Params p = server;
        boolean doFx = p != null && cur >= 0.003F;
        boolean doPs1 = p != null && ps1Cur >= 0.01F;
        boolean doMode = p != null && modeId != 0 && modeCur >= 0.01F;
        if (!doFx && !doPs1 && !doMode) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            ensure(mc, main);
        } catch (IOException ex) {
            server = null;
            return;
        }
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        float pt = e.getPartialTick();
        if (doFx) {
            float k = cur;
            var u = fx.getEffect();
            u.safeGetUniform("Contrast").set(p.contrast * k);
            u.safeGetUniform("Desaturate").set(Math.min(1F, p.desaturate * k));
            u.safeGetUniform("Brightness").set(1F + (p.brightness - 1F) * k);
            u.safeGetUniform("Chromatic").set(p.chromatic * k);
            u.safeGetUniform("Vignette").set(p.vignette * k);
            u.safeGetUniform("Saturation").set(1F + (p.saturation - 1F) * k);
            u.safeGetUniform("Gamma").set(1F + (p.gamma - 1F) * k);
            u.safeGetUniform("ToneAmt").set(p.toneAmt * k);
            u.safeGetUniform("ShadowCol").set(p.shR, p.shG, p.shB);
            u.safeGetUniform("HighCol").set(p.hiR, p.hiG, p.hiB);
            fx.process(pt);
            blit.process(pt);
        }
        if (doPs1) {
            var u = ps1Pass.getEffect();
            u.safeGetUniform("Res").set(Math.max(60F, p.ps1Res));
            u.safeGetUniform("Dither").set(Math.max(0F, Math.min(1F, p.ps1Dither)));
            u.safeGetUniform("Amount").set(Math.min(1F, ps1Cur));
            u.safeGetUniform("FarPlane").set(mc.gameRenderer.getDepthFar());
            u.safeGetUniform("FogRange").set(p.ps1Fog * 0.45F, p.ps1Fog);
            float[] fc = RenderSystem.getShaderFogColor();
            u.safeGetUniform("FogCol").set(fc[0], fc[1], fc[2]);
            ps1Pass.process(pt);
            blit.process(pt);
        }
        if (doMode) {
            var u = modePass.getEffect();
            u.safeGetUniform("Mode").set((float) modeId);
            u.safeGetUniform("Amount").set(Math.min(1F, modeCur));
            u.safeGetUniform("Clock").set(((System.nanoTime() - T0) / 1.0e9F) % 3600F);
            modePass.process(pt);
            blit.process(pt);
        }
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }

    private static void ensure(Minecraft mc, RenderTarget main) throws IOException {
        if (swap != null && w == main.width && h == main.height && fx != null && ps1Pass != null && modePass != null) return;
        free();
        w = main.width; h = main.height;
        swap = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        swap.setClearColor(0F, 0F, 0F, 0F);
        Matrix4f ortho = new Matrix4f().setOrtho(0F, w, 0F, h, 0.1F, 1000F);
        fx = new PostPass(mc.getResourceManager(), "visionfx", main, swap);
        fx.setOrthoMatrix(ortho);
        ps1Pass = new PostPass(mc.getResourceManager(), "visionfx_ps1", main, swap);
        ps1Pass.setOrthoMatrix(ortho);
        ps1Pass.addAuxAsset("DepthSampler", () -> mc.getMainRenderTarget().getDepthTextureId(), w, h);
        modePass = new PostPass(mc.getResourceManager(), "visionfx_modes", main, swap);
        modePass.setOrthoMatrix(ortho);
        blit = new PostPass(mc.getResourceManager(), "blit", swap, main);
        blit.setOrthoMatrix(ortho);
    }

    private static void free() {
        if (fx != null) fx.close();
        if (blit != null) blit.close();
        if (ps1Pass != null) ps1Pass.close();
        if (modePass != null) modePass.close();
        if (swap != null) swap.destroyBuffers();
        fx = null; blit = null; ps1Pass = null; modePass = null; swap = null;
    }
}
