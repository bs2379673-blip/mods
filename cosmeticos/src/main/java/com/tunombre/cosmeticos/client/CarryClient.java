package com.tunombre.cosmeticos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tunombre.cosmeticos.Hook;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.Set;

/**
 * Pose del jugador enganchado por la guadaña, vista igual por todos (también por él mismo en tercera persona): colgando de la hoja con
 * balanceo de péndulo, o tirado boca arriba y arrastrado por el piso. Se aplica girando todo el modelo antes de dibujarlo, así sirve
 * tanto con el modelo normal como con el de Epic Fight (que dibuja dentro del mismo evento y lo cancela: por eso se cierra también ahí).
 */
public final class CarryClient {
    private CarryClient() { }

    private static final Set<Integer> PUSHED = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        if (!(le.getVehicle() instanceof Hook h)) return;
        if (!PUSHED.add(le.getId())) { PUSHED.remove(le.getId()); return; }   // quedó marcado de antes: no se arriesga a desbalancear la pila
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        float pt = e.getPartialTick();
        if (h.dragging()) {
            double hx = Mth.lerp(pt, h.xo, h.getX()), hz = Mth.lerp(pt, h.zo, h.getZ());
            double px = Mth.lerp(pt, le.xo, le.getX()), pz = Mth.lerp(pt, le.zo, le.getZ());
            double dx = hx - px, dz = hz - pz, l = Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-4) { dx = 0; dz = 1; l = 1; }
            dx /= l; dz /= l;
            // acostado: la cabeza hacia el gancho, con algún saltito al arrastrarse
            float bump = (float) (Math.abs(Math.sin((le.tickCount + pt) * 0.9)) * 0.05 * Math.min(1, h.getDeltaMovement().horizontalDistance() * 8));
            ps.translate(0, 0.14 + bump, 0);
            ps.mulPose(new Quaternionf().rotationAxis((float) (Math.PI / 2), (float) dz, 0F, (float) -dx));
        } else {
            // colgando de la espalda: gira alrededor del punto donde está la hoja
            float t = le.tickCount + pt;
            float sx = h.swingX + 0.06F * (float) Math.sin(t * 0.07F), sz = h.swingZ + 0.05F * (float) Math.cos(t * 0.061F);
            ps.translate(0, 1.45, 0);
            ps.mulPose(new Quaternionf().rotationX(sx).rotateZ(sz));
            ps.translate(0, -1.45, 0);
        }
    }

    /** Epic Fight dibuja dentro del Pre y lo cancela: el Post no llega, así que se cierra acá */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void preLate(RenderLivingEvent.Pre<?, ?> e) {
        if (e.isCanceled() && PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(RenderLivingEvent.Post<?, ?> e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }
}
