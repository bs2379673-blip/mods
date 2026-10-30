package com.annis.interact.mixin.client;

import com.annis.interact.client.TouchCam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Modo mimos: la camara mira a los dos desde un costado (y se puede girar). */
@Mixin(Camera.class)
public abstract class TouchCameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    /** Primera persona de mimos: el juego cree que es tercera persona y dibuja el propio cuerpo. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void annisinteract$showBody(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (TouchCam.active && TouchCam.firstPerson) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void annisinteract$touchCam(BlockGetter area, Entity focused, boolean thirdPerson, boolean inverseView,
                                        float tickDelta, CallbackInfo ci) {
        if (!TouchCam.active || focused == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity other = mc.level == null ? null : mc.level.getEntity(TouchCam.receiver);
        if (other == null) {
            return;
        }
        Vec3 me = focused.getPosition(tickDelta);
        Vec3 you = other.getPosition(tickDelta);
        if (TouchCam.firstPerson) {
            // en los ojos, mirando su cara (un poco hacia abajo para ver las manos)
            // un poco detras de los ojos (la cabeza no se dibuja): asi los brazos propios no tapan todo
            Vec3 away = new Vec3(me.x - you.x, 0, me.z - you.z);
            away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
            Vec3 eye = focused.getEyePosition(tickDelta).add(away.scale(0.32)).add(0, 0.10, 0);
            Vec3 face = you.add(0, other.getEyeHeight() - 0.15, 0).subtract(eye);
            double h = Math.sqrt(face.x * face.x + face.z * face.z);
            float fy = (float) Math.toDegrees(Math.atan2(-face.x, face.z)) + TouchCam.lookYaw;
            float fp = (float) -Math.toDegrees(Math.atan2(face.y, h)) + TouchCam.lookPitch;
            setRotation(fy, Math.max(-60.0f, Math.min(75.0f, fp)));
            setPos(eye.x, eye.y, eye.z);
            return;
        }
        Vec3 mid = me.add(you).scale(0.5).add(0, 1.45, 0);
        Vec3 back = new Vec3(me.x - you.x, 0, me.z - you.z);
        back = back.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : back.normalize();
        double a = Math.toRadians(TouchCam.orbit);
        Vec3 dir = new Vec3(back.x * Math.cos(a) - back.z * Math.sin(a), 0, back.x * Math.sin(a) + back.z * Math.cos(a));
        Vec3 cam = mid.add(dir.scale(TouchCam.distance)).add(0, 0.45, 0);
        // que no atraviese paredes
        HitResult hit = mc.level.clip(new ClipContext(mid, cam, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, focused));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 to = hit.getLocation().subtract(mid);
            double len = Math.max(0.3, to.length() - 0.2);
            cam = mid.add(to.normalize().scale(len));
        }
        Vec3 look = mid.add(0, -0.05, 0).subtract(cam);
        double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, horiz));
        setRotation(yaw, pitch);
        setPos(cam.x, cam.y, cam.z);
    }
}
