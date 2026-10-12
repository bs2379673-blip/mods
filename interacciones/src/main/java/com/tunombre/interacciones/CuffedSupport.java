package com.tunombre.interacciones;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Compatibilidad con Cuffed (esposas, grilletes, cinta...) por reflexion: no hace falta Cuffed para compilar ni para
 * jugar; si no esta, 'Esposar' simplemente avisa. Usa la misma via que Cuffed cuando le das clic a alguien con
 * las esposas: RestraintAPI.getRestraintFromStack + capacidad.TryEquipRestraint(cautivo, captor, restriccion).
 */
final class CuffedSupport {
    private CuffedSupport() { }

    private static final String P = "com.lazrproductions.cuffed.";
    private static Boolean loaded;

    static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("cuffed");
        return loaded;
    }

    private static boolean call(String method, ItemStack s) {
        try {
            return (Boolean) Class.forName(P + "restraints.RestraintAPI").getMethod(method, ItemStack.class).invoke(null, s);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isArm(ItemStack s) { return loaded() && !s.isEmpty() && call("IsArmRestraintItem", s); }

    static boolean isLeg(ItemStack s) { return loaded() && !s.isEmpty() && call("IsLegRestraintItem", s); }

    static boolean isRestraint(ItemStack s) { return isArm(s) || isLeg(s); }

    /** pone la restriccion; devuelve null si ha ido bien o el motivo si no */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String cuff(ServerPlayer captive, ServerPlayer captor, ItemStack stack) {
        try {
            boolean arm = isArm(stack);
            if (!arm && !isLeg(stack)) return "Ya no tienes las esposas en la mano";
            Class<?> api = Class.forName(P + "restraints.RestraintAPI");
            Class<?> typeC = Class.forName(P + "restraints.base.RestraintType");
            Object type = Enum.valueOf((Class) typeC, arm ? "Arm" : "Leg");
            Object restraint = api.getMethod("getRestraintFromStack", ItemStack.class, typeC, ServerPlayer.class, ServerPlayer.class)
                    .invoke(null, stack, type, captive, captor);
            if (restraint == null) return "Esas esposas no se pueden poner";
            Capability<?> cap = (Capability<?>) Class.forName(P + "cap.provider.RestrainableCapabilityProvider").getField("CAP").get(null);
            Object c = captive.getCapability((Capability) cap).resolve().orElse(null);
            if (c == null) return "No se puede esposar a este jugador";
            if ((Boolean) c.getClass().getMethod(arm ? "armsRestrained" : "legsRestrained").invoke(c)) return "Ya esta esposado";
            Class<?> rc = Class.forName(P + "restraints.base." + (arm ? "AbstractArmRestraint" : "AbstractLegRestraint"));
            Method eq = c.getClass().getMethod("TryEquipRestraint", ServerPlayer.class, ServerPlayer.class, rc);
            boolean ok = (Boolean) eq.invoke(c, captive, captor, restraint);
            if (!ok) return "No se pudo esposar";
            if (!captor.isCreative()) stack.shrink(1);
            return null;
        } catch (Throwable t) {
            Interacciones.LOG.warn("Cuffed: no se pudo esposar: {}", t.toString());
            return "No se pudo esposar (Cuffed)";
        }
    }
}
