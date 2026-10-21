package com.tunombre.obscurenpc;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/** Armas de TACZ para los NPCs, por reflexion (el mod funciona igual sin TACZ). */
public final class TaczBridge {
    private static boolean ok;
    private static Method getIGun, setAmmo, setBullet, fromLiving, draw, aim, shoot, reload, bolt;
    private static final Map<UUID, String> DRAWN = new HashMap<>();
    private static final Random RND = new Random();

    static {
        try {
            Class<?> ig = Class.forName("com.tacz.guns.api.item.IGun");
            getIGun = ig.getMethod("getIGunOrNull", ItemStack.class);
            setAmmo = ig.getMethod("setCurrentAmmoCount", ItemStack.class, int.class);
            setBullet = ig.getMethod("setBulletInBarrel", ItemStack.class, boolean.class);
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            fromLiving = op.getMethod("fromLivingEntity", LivingEntity.class);
            draw = op.getMethod("draw", Supplier.class);
            aim = op.getMethod("aim", boolean.class);
            shoot = op.getMethod("shoot", Supplier.class, Supplier.class);
            reload = op.getMethod("reload");
            bolt = op.getMethod("bolt");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
    }

    private TaczBridge() { }

    public static boolean available() { return ok; }

    public static boolean holdsGun(LivingEntity e) {
        if (!ok) return false;
        try { return getIGun.invoke(null, e.getMainHandItem()) != null; } catch (Throwable t) { return false; }
    }

    public static void aim(LivingEntity e, boolean on) {
        if (!ok) return;
        try { aim.invoke(fromLiving.invoke(null, e), on); } catch (Throwable ignored) { }
    }

    /** dispara al objetivo con el arma que lleva en la mano */
    public static void shoot(LivingEntity e, LivingEntity target, float spreadDeg, boolean infinite) {
        if (!ok) return;
        try {
            ItemStack stack = e.getMainHandItem();
            Object gun = getIGun.invoke(null, stack);
            if (gun == null) return;
            if (infinite) {
                setAmmo.invoke(gun, stack, 999);
                setBullet.invoke(gun, stack, true);
            }
            Object op = fromLiving.invoke(null, e);
            String sig = stack.getItem() + String.valueOf(stack.getTag());
            if (!sig.equals(DRAWN.get(e.getUUID()))) {
                DRAWN.put(e.getUUID(), sig);
                draw.invoke(op, (Supplier<ItemStack>) e::getMainHandItem);
                return;
            }
            Vec3 from = e.getEyePosition();
            Vec3 to = target.getBoundingBox().getCenter();
            double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * 57.29577951308232D) - 90.0F) + (float) (RND.nextGaussian() * spreadDeg);
            float pitch = (float) -(Mth.atan2(dy, horiz) * 57.29577951308232D) + (float) (RND.nextGaussian() * spreadDeg * 0.6);
            Object res = shoot.invoke(op, (Supplier<Float>) () -> pitch, (Supplier<Float>) () -> yaw);
            String r = String.valueOf(res);
            if (r.equals("NO_AMMO")) reload.invoke(op);
            else if (r.equals("NEED_BOLT")) bolt.invoke(op);
        } catch (Throwable t) {
            // un fallo del arma no debe romper al NPC
        }
    }
}
