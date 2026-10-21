package com.tunombre.jefes.client;

import com.tunombre.jefes.Boss;
import com.tunombre.jefes.JefeEntity;
import com.tunombre.jefes.core.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Camara de cine para el jugador agarrado: orbita alrededor del jefe y su mano; al devorar se acerca a la cara. */
public final class Cinematic {
    private static int entity = -1;
    private static Vec3 cur;
    private static float t;

    private Cinematic() { }

    public static boolean active() { return entity >= 0; }

    public static void set(int id) {
        entity = id;
        cur = null;
        t = 0;
        if (id < 0) CamHolder.release();
    }

    public static void tick() {
        if (entity < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { entity = -1; return; }
        t++;
    }

    public static void camera(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entity) instanceof JefeEntity j)) return;
        Boss b = j.getBoss();
        if (b == null) return;
        Act a = j.getAct();
        float at = j.actTime(pt);
        double yaw = Math.toRadians(j.getYRot());
        Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        float sc = b.spec.scale;
        Vec3 base = j.getPosition(pt);
        Vec3 head = base.add(0, b.spec.eye * 0.95, 0).add(f.scale(1.0 * sc));
        Vec3 hand = j.handPoint(Math.max(0F, at));
        double tt = (t + pt);
        Vec3 want, target;
        if (a == Act.KILL) {
            float k = Mth.clamp((at - 6F) / 26F, 0F, 1F);
            double d = Mth.lerp(k, 7.5, 2.4);
            target = head.add(0, -0.1, 0);
            want = head.add(f.scale(d)).add(side.scale(Mth.lerp(k, 2.2, 0.0))).add(0, Mth.lerp(k, 0.4, -0.3), 0);
            if (at > 30F) {     // el mordisco: temblor fuerte
                double sh = Math.max(0.0, 1.0 - (at - 30F) / 60.0) * 0.18;
                want = want.add(Math.sin(tt * 3.1) * sh, Math.cos(tt * 4.3) * sh, Math.sin(tt * 2.7) * sh);
            }
        } else if (a == Act.THROW) {
            target = hand.add(f.scale(1.5));
            want = base.add(f.scale(-1.0 * sc)).add(side.scale(8.5 * sc)).add(0, 3.0 * sc, 0);
        } else {
            // orbita lenta de costado, mirando entre la mano y la cara del jefe
            double ang = Math.toRadians(70 + 28 * Math.sin(tt / 38.0));
            double r = 6.4 * Math.max(1.0, sc);
            Vec3 dir = f.scale(Math.cos(ang)).add(side.scale(Math.sin(ang)));
            target = hand.add(head.subtract(hand).scale(0.25));
            want = target.add(dir.scale(r)).add(0, 0.2 + 0.5 * Math.sin(tt / 55.0), 0);
            double sh = j.struggle() * 0.05;
            want = want.add(Math.sin(tt * 2.9) * sh, Math.cos(tt * 3.7) * sh, 0);
        }
        cur = cur == null ? want : cur.lerp(want, 0.18);
        CamHolder.place(cur, target.subtract(cur));
    }

    public static Vec3 position() { return cur; }
}
