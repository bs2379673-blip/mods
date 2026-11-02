package com.starlevi.starriders.client;

import com.starlevi.starriders.PhoenixEntity;
import com.starlevi.starriders.StarRiders;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Do a Barrel Roll (opcional): mientras volás montado en el Fenix, ese mod maneja el mouse y la
 * camara (cabeceo, giro y tonel) como con la elitra, y el Fenix sigue tu direccion y se ladea con
 * tu giro. Sin el mod, el Fenix se ladea solo en las curvas.
 */
public final class DabrCompat {
    private static Method getRoll;
    private static boolean active;

    private DabrCompat() {
    }

    public static void init() {
        if (!ModList.get().isLoaded("do_a_barrel_roll")) {
            return;
        }
        try {
            Class<?> group = Class.forName("nl.enjarai.doabarrelroll.api.event.RollGroup");
            Object g = group.getMethod("of", ResourceLocation.class).invoke(null,
                    ResourceLocation.fromNamespaceAndPath(StarRiders.MOD_ID, "fenix"));
            Supplier<Boolean> cond = DabrCompat::ridingFlying;
            group.getMethod("trueIf", Supplier.class).invoke(g, cond);
            getRoll = Class.forName("nl.enjarai.doabarrelroll.api.RollEntity").getMethod("doABarrelRoll$getRoll", float.class);
            active = true;
            StarRiders.LOGGER.info("StarRiders: Do a Barrel Roll detectado, se usa para volar en las monturas");
        } catch (Throwable t) {
            StarRiders.LOGGER.warn("StarRiders: no se pudo enlazar con Do a Barrel Roll: {}", t.toString());
        }
    }

    /** El jugador local va montado en un fenix que esta volando. */
    public static boolean ridingFlying() {
        Player p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof PhoenixEntity ph && ph.isFlyingNow();
    }

    /** Giro (grados) del jinete segun Do a Barrel Roll, o null si no esta. */
    public static Float roll(Player p, float partial) {
        if (!active || getRoll == null) {
            return null;
        }
        try {
            return (Float) getRoll.invoke(p, partial);
        } catch (Throwable t) {
            return null;
        }
    }
}
