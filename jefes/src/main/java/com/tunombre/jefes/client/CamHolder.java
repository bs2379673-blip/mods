package com.tunombre.jefes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

/** Camara libre sin mixins: una entidad "marcador" solo del cliente a la que se le pasa la vista de Minecraft. */
final class CamHolder {
    private static Marker cam;
    private static Entity prev;

    private CamHolder() { }

    static boolean using() { return cam != null; }

    static void place(Vec3 pos, Vec3 look) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (cam == null || cam.level() != mc.level) {
            cam = new Marker(EntityType.MARKER, mc.level);
            cam.setPos(pos.x, pos.y, pos.z);
            cam.xo = cam.xOld = pos.x; cam.yo = cam.yOld = pos.y; cam.zo = cam.zOld = pos.z;
            if (mc.getCameraEntity() == mc.player) prev = mc.player;
            mc.setCameraEntity(cam);
        }
        double len = Math.sqrt(look.x * look.x + look.y * look.y + look.z * look.z);
        if (len < 1e-6) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.asin(look.y / len));
        cam.setPos(pos.x, pos.y, pos.z);
        cam.xo = cam.xOld = pos.x; cam.yo = cam.yOld = pos.y; cam.zo = cam.zOld = pos.z;
        cam.setYRot(yaw); cam.setXRot(pitch);
        cam.yRotO = yaw; cam.xRotO = pitch;
    }

    static void release() {
        if (cam == null) return;
        cam = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        prev = null;
    }
}
