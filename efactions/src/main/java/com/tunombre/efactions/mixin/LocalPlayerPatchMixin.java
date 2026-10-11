package com.tunombre.efactions.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;

import java.lang.reflect.Method;

/**
 * Solo cliente. Dos arreglos sobre el LocalPlayerPatch de Epic Fight:
 *
 * 1) Pistola de clavos (SA): Epic Fight la trata como arma cuerpo a cuerpo, se "come" el clic izquierdo y lanza la
 *    animacion de golpe, por eso no disparaba. Aqui se le dice que NO reproduzca animacion de ataque con ella en la mano
 *    (igual que hace epic_arsenal con las armas TACZ y los arcos).
 *
 * 2) Parpadeo del icono de Epic Fight en primera persona con arma TACZ/arco: epic_arsenal pasa a modo vanilla al entrar
 *    en primera persona y, en el mismo tick, otra parte suya lo devuelve a modo Epic Fight (y asi cada tick).
 *    Aqui se ignora ese regreso mientras se este en primera persona con un arma de esas (salvo durante una esquiva).
 */
@Mixin(value = LocalPlayerPatch.class, remap = false)
public abstract class LocalPlayerPatchMixin {
    private static Method efactions$dodgeGetInstance, efactions$dodgeActive;
    private static boolean efactions$dodgeInit;

    private static boolean efactions$isClass(ItemStack st, String suffix) {
        if (st == null || st.isEmpty()) return false;
        for (Class<?> c = st.getItem().getClass(); c != null && c != Object.class; c = c.getSuperclass())
            if (c.getName().endsWith(suffix)) return true;
        return false;
    }

    private static boolean efactions$isGunOrBow(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        if (st.getItem() instanceof BowItem) return true;
        for (Class<?> c = st.getItem().getClass(); c != null && c != Object.class; c = c.getSuperclass())
            for (Class<?> i : c.getInterfaces())
                if (i.getName().equals("com.tacz.guns.api.item.IGun")) return true;
        return false;
    }

    private static boolean efactions$dodgeActive() {
        try {
            if (!efactions$dodgeInit) {
                efactions$dodgeInit = true;
                Class<?> pm = Class.forName("org.merlin204.epicarsenal.client.PerspectiveManager");
                efactions$dodgeGetInstance = pm.getMethod("getInstance");
                efactions$dodgeActive = pm.getMethod("isFirstPersonDodgeActive");
            }
            if (efactions$dodgeGetInstance == null) return false;
            return (Boolean) efactions$dodgeActive.invoke(efactions$dodgeGetInstance.invoke(null));
        } catch (Throwable t) {
            efactions$dodgeGetInstance = null;
            return false;
        }
    }

    @Inject(method = "canPlayAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void efactions$noMeleeWithNailGun(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && efactions$isClass(mc.player.getMainHandItem(), ".NailGunItem")) cir.setReturnValue(false);
    }

    @Inject(method = "toEpicFightMode", at = @At("HEAD"), cancellable = true)
    private void efactions$noFlickerInFirstPerson(boolean synchronize, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        if (!efactions$isGunOrBow(mc.player.getMainHandItem())) return;
        if (efactions$dodgeActive()) return;
        ci.cancel();
    }
}
