package com.tunombre.obscurenpc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tunombre.obscurenpc.Actor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Nombre y burbuja de dialogo sobre la cabeza del NPC. Se dibuja en el mundo (no dentro del renderer del mob), asi funciona
 * igual con Epic Fight y con huesos normales.
 */
public final class BubbleRender {
    private BubbleRender() { }

    @SubscribeEvent
    public static void onLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float pt = e.getPartialTick();
        boolean any = false;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof Actor a) || a.isInvisible() || !a.isAlive()) continue;
            double dist = a.distanceToSqr(mc.player);
            if (dist > 32 * 32) continue;
            String bubble = a.bubble();
            boolean name = a.getEntityData().get(Actor.SHOW_NAME) && !a.getEntityData().get(Actor.NAME).isEmpty();
            if (!name && bubble.isEmpty()) continue;
            draw(mc, e.getPoseStack(), buf, a, cam, pt, name, dist <= 24 * 24 ? bubble : "");
            any = true;
        }
        if (any) buf.endBatch();
    }

    private static List<String> wrap(Font font, String text, int maxPx) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            String t = cur.length() == 0 ? w : cur + " " + w;
            if (font.width(t) > maxPx && cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(w); }
            else { cur = new StringBuilder(t); }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private static void draw(Minecraft mc, PoseStack ps, MultiBufferSource buf, Actor a, Vec3 cam, float pt, boolean showName, String bubble) {
        double x = Mth.lerp(pt, a.xo, a.getX()) - cam.x;
        double y = Mth.lerp(pt, a.yo, a.getY()) - cam.y + a.getBbHeight() + 0.55;
        double z = Mth.lerp(pt, a.zo, a.getZ()) - cam.z;
        if (a.stance() == 1) y -= 0.45 * a.scale();
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
        ps.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = ps.last().pose();
        Font font = mc.font;
        int light = LightTexture.FULL_BRIGHT;
        if (showName) {
            String n = a.getEntityData().get(Actor.NAME);
            int col = 0xFF000000 | a.getEntityData().get(Actor.NAME_COLOR);
            float w = font.width(n) / 2F;
            font.drawInBatch(n, -w, 0, col, false, m, buf, Font.DisplayMode.NORMAL, 0x55000000, light);
        }
        if (!bubble.isEmpty()) {
            List<String> lines = wrap(font, bubble, 150);
            int n = lines.size();
            int base = showName ? -14 : -4;
            for (int i = 0; i < n; i++) {
                String l = lines.get(i);
                float w = font.width(l) / 2F;
                int yy = base - (n - i) * 10;
                font.drawInBatch(l, -w, yy, 0xFFFFFFFF, false, m, buf, Font.DisplayMode.NORMAL, 0xB0101018, light);
            }
        }
        ps.popPose();
    }
}
