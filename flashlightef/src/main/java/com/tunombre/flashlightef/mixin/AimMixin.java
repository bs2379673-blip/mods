package com.tunombre.flashlightef.mixin;

import com.keerdm.flashlightmod.client.render.ActiveFlashlight;
import com.keerdm.flashlightmod.client.render.GlassPositionManager;
import com.tunombre.flashlightef.EpicSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * La luz de la linterna en tercera persona se calcula con la matriz del item en la mano. Con Epic Fight esa matriz sale
 * con otra orientacion y la luz apunta a la espalda. Para jugadores en modo Epic Fight se corrige: la luz sale de la mano
 * y apunta hacia donde mira el jugador.
 */
@Pseudo
@Mixin(targets = "com.keerdm.flashlightmod.client.render.FlashlightLightDataManager", remap = false)
public abstract class AimMixin {
    @Inject(method = "buildOtherPlayerLight", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void flashlightef$aim(UUID uid, Vec3 cameraPos, Matrix4f invView, float partialTick, CallbackInfoReturnable<ActiveFlashlight> cir) {
        ActiveFlashlight fl = cir.getReturnValue();
        if (fl == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Player p = mc.level != null ? mc.level.getPlayerByUUID(uid) : null;
            if (p == null || !EpicSupport.isEpicMode(p)) return;
            Vec3 dir = p.getViewVector(partialTick).normalize();
            Vec3 origin = fl.origin;
            GlassPositionManager.HandTransformData hand = GlassPositionManager.getFreshHandTransform(uid);
            if (hand != null) {
                // la matriz capturada ya incluye el giro de FlipModel: se deshace para quedarse con la posicion de la mano
                Matrix4f w = new Matrix4f((Matrix4fc) invView).mul((Matrix4fc) hand.transformMatrix).mul((Matrix4fc) com.tunombre.flashlightef.FlipModel.FLIP);
                // posicion de la mano, un poco hacia adelante para que el haz no nazca dentro del cuerpo
                origin = new Vec3(w.m30(), w.m31(), w.m32()).add(dir.scale(0.3));
            }
            cir.setReturnValue(new ActiveFlashlight(origin, dir, fl.coneAngleCos, fl.range, fl.isShadowed, fl.intensity));
        } catch (Throwable ignored) {
            // si algo cambia en una version nueva del mod de la linterna, se deja su comportamiento original
        }
    }
}
