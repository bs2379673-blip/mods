package com.starlevi.starfest.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.starlevi.starfest.StarFest;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Iris (paquetes de shaders): le decimos a Iris con que programa dibujar nuestras piezas (si no, con un
 * paquete de shaders puesto no se ven), y si hay un paquete en uso, para usar los programas del juego.
 */
public final class IrisCompat {
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
    private static Object api;
    private static MethodHandle inUse, assign;
    private static Class<?> programs;
    private static boolean tried;

    private IrisCompat() {
    }

    private static void init() {
        if (tried) {
            return;
        }
        tried = true;
        if (!IRIS) {
            return;
        }
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance").invoke(null);
            programs = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            inUse = MethodHandles.publicLookup().findVirtual(c, "isShaderPackInUse", MethodType.methodType(boolean.class));
            assign = MethodHandles.publicLookup().findVirtual(c, "assignPipeline", MethodType.methodType(void.class, RenderPipeline.class, programs));
        } catch (Throwable t) {
            StarFest.LOGGER.warn("StarFest: no pude hablar con Iris: {}", t.toString());
            api = null;
        }
    }

    /** Si hay un paquete de shaders puesto ahora. */
    public static boolean shaders() {
        init();
        if (api == null || inUse == null) {
            return false;
        }
        try {
            return (boolean) inUse.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Que Iris dibuje este pipeline con su programa ("SKY_TEXTURED", "SKY_BASIC", "BASIC"...). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void assign(RenderPipeline p, String program) {
        init();
        if (api == null || assign == null) {
            return;
        }
        try {
            assign.invoke(api, p, Enum.valueOf((Class) programs, program));
        } catch (Throwable t) {
            StarFest.LOGGER.warn("StarFest: Iris no acepto el programa {}: {}", program, t.toString());
        }
    }
}
