package com.tunombre.lootmaster.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tunombre.lootmaster.LootMaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dibuja la zona marcada con la varita (solo mientras la llevas en la mano). */
@Mod.EventBusSubscriber(modid = LootMaster.MODID, value = Dist.CLIENT)
public final class WandRender {
    private static int[] a, b;
    private static java.util.List<int[]> zones = new java.util.ArrayList<>();

    private static int[] plan = new int[0];

    static void setPlan(String json) {
        try {
            JsonArray a = JsonParser.parseString(json).getAsJsonArray();
            int[] r = new int[a.size()];
            for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt();
            plan = r;
        } catch (RuntimeException e) { plan = new int[0]; }
    }

    static void setZones(JsonArray zs) {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        for (var e : zs) { JsonObject z = e.getAsJsonObject(); l.add(new int[]{z.get("x1").getAsInt(), z.get("z1").getAsInt(), z.get("x2").getAsInt(), z.get("z2").getAsInt(), z.get("y1").getAsInt(), z.get("y2").getAsInt(), z.get("ignoreY").getAsBoolean() ? 1 : 0, z.get("enabled").getAsBoolean() ? 1 : 0}); }
        zones = l;
    }

    private WandRender() { }

    static void set(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            a = read(o, "a");
            b = read(o, "b");
        } catch (RuntimeException e) { a = null; b = null; }
    }

    private static int[] read(JsonObject o, String k) {
        if (!o.has(k)) return null;
        JsonArray x = o.getAsJsonArray(k);
        return new int[]{x.get(0).getAsInt(), x.get(1).getAsInt(), x.get(2).getAsInt()};
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean holding = mc.player.getMainHandItem().getItem() == LootMaster.WAND.get();
        if (!holding && plan.length == 0) return;
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        for (int i = 0; i + 3 < plan.length; i += 4) {
            int cat = plan[i + 3];
            float r = cat == 5 ? 1F : cat == 4 ? 0.3F : cat == 2 ? 1F : cat == 3 ? 1F : 0.2F;
            float g = cat == 5 ? 1F : cat == 4 ? 0.6F : cat == 2 ? 0.25F : cat == 3 ? 0.85F : 1F;
            float bl = cat == 5 ? 0.92F : cat == 4 ? 1F : cat == 2 ? 0.25F : cat == 3 ? 0.2F : 0.4F;
            LevelRenderer.renderLineBox(ps, vc, plan[i] + 0.15, plan[i + 1] + 0.05, plan[i + 2] + 0.15, plan[i] + 0.85, plan[i + 1] + 0.95, plan[i + 2] + 0.85, r, g, bl, 1F);
        }
        if (!holding) { ps.popPose(); buf.endBatch(RenderType.lines()); return; }
        for (int[] z : zones) {
            float c = z[7] == 1 ? 1F : 0.4F;
            LevelRenderer.renderLineBox(ps, vc, z[0], z[6] == 1 ? mc.level.getMinBuildHeight() : z[4], z[1], z[2] + 1, z[6] == 1 ? mc.level.getMaxBuildHeight() : z[5] + 1, z[3] + 1, c, c, 0.1F, 1F);
        }
        if (a != null || b != null) {
        int[] p1 = a != null ? a : b, p2 = b != null ? b : a;
        int y1 = Math.min(p1[1], p2[1]), y2 = Math.max(p1[1], p2[1]) + 1;
        if (a != null && b != null) { y1 = mc.level.getMinBuildHeight(); y2 = mc.level.getMaxBuildHeight(); }
        LevelRenderer.renderLineBox(ps, vc, Math.min(p1[0], p2[0]), y1, Math.min(p1[2], p2[2]), Math.max(p1[0], p2[0]) + 1, y2, Math.max(p1[2], p2[2]) + 1, 1F, 0.2F, 0.25F, 1F);
        if (a != null) LevelRenderer.renderLineBox(ps, vc, a[0], a[1], a[2], a[0] + 1, a[1] + 1, a[2] + 1, 0.3F, 1F, 0.4F, 1F);
        if (b != null) LevelRenderer.renderLineBox(ps, vc, b[0], b[1], b[2], b[0] + 1, b[1] + 1, b[2] + 1, 0.3F, 0.6F, 1F, 1F);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
    }
}
