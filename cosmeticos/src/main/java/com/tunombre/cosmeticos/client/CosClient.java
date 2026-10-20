package com.tunombre.cosmeticos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tunombre.cosmeticos.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** lado cliente: guarda quien lleva que cosmetico y lo dibuja (geometria procedural animada, sin depender del renderizador de jugadores) */
public final class CosClient {
    private static final Map<UUID, Long> MASKS = new ConcurrentHashMap<>();
    private static BufferBuilder solid, add;

    private CosClient() { }

    public static void init() { MinecraftForge.EVENT_BUS.register(CosClient.class); }

    public static void set(UUID id, long mask) {
        if (mask == 0L) MASKS.remove(id); else MASKS.put(id, mask);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { MASKS.clear(); }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || MASKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick();
        boolean any = false;
        for (Player p : mc.level.players()) if (MASKS.containsKey(p.getUUID())) { any = true; break; }
        if (!any) return;
        if (solid == null) { solid = new BufferBuilder(1 << 18); add = new BufferBuilder(1 << 18); }
        solid.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        add.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Draw d = new Draw(solid, add);
        for (Player p : mc.level.players()) {
            Long mask = MASKS.get(p.getUUID());
            if (mask == null || p.isSpectator() || p.isInvisible() || p.isSleeping()) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
            if (cam.distanceToSqr(x, y, z) > 80 * 80) continue;
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            d.setPlayer((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), yaw, p.tickCount + pt,
                    (float) (p.getX() - p.xo), (float) (p.getZ() - p.zo), !p.onGround() && !p.isInWater(), p.isFallFlying(), p.isCrouching());
            for (Cosmetics.Def def : Cosmetics.ALL)
                if ((mask & (1L << def.index())) != 0L) Styles.draw(def.id(), d);
        }
        var sr = solid.end();
        var ar = add.end();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.mulPoseMatrix(e.getPoseStack().last().pose());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        BufferUploader.drawWithShader(sr);
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        BufferUploader.drawWithShader(ar);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
