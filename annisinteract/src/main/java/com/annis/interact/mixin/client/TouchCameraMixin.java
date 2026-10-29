package com.annis.interact.mixin.client;

import com.annis.interact.client.TouchCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
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

    @Inject(method = "update", at = @At("TAIL"))
    private void annisinteract$touchCam(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView,
                                        float tickDelta, CallbackInfo ci) {
        if (!TouchCam.active || focused == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity other = mc.world == null ? null : mc.world.getEntityById(TouchCam.receiver);
        if (other == null) {
            return;
        }
        Vec3d me = focused.getLerpedPos(tickDelta);
        Vec3d you = other.getLerpedPos(tickDelta);
        if (TouchCam.firstPerson) {
            // en los ojos, mirando su cara (un poco hacia abajo para ver las manos)
            // un poco detras de los ojos (la cabeza no se dibuja): asi los brazos propios no tapan todo
            Vec3d away = new Vec3d(me.x - you.x, 0, me.z - you.z);
            away = away.lengthSquared() < 1.0e-4 ? Vec3d.ZERO : away.normalize();
            Vec3d eye = focused.getCameraPosVec(tickDelta).add(away.multiply(0.32)).add(0, 0.10, 0);
            Vec3d face = you.add(0, other.getStandingEyeHeight() - 0.15, 0).subtract(eye);
            double h = Math.sqrt(face.x * face.x + face.z * face.z);
            float fy = (float) Math.toDegrees(Math.atan2(-face.x, face.z)) + TouchCam.lookYaw;
            float fp = (float) -Math.toDegrees(Math.atan2(face.y, h)) + TouchCam.lookPitch;
            setRotation(fy, Math.max(-60.0f, Math.min(75.0f, fp)));
            setPos(eye.x, eye.y, eye.z);
            return;
        }
        Vec3d mid = me.add(you).multiply(0.5).add(0, 1.45, 0);
        Vec3d back = new Vec3d(me.x - you.x, 0, me.z - you.z);
        back = back.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : back.normalize();
        double a = Math.toRadians(TouchCam.orbit);
        Vec3d dir = new Vec3d(back.x * Math.cos(a) - back.z * Math.sin(a), 0, back.x * Math.sin(a) + back.z * Math.cos(a));
        Vec3d cam = mid.add(dir.multiply(TouchCam.distance)).add(0, 0.45, 0);
        // que no atraviese paredes
        HitResult hit = mc.world.raycast(new RaycastContext(mid, cam, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, focused));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3d to = hit.getPos().subtract(mid);
            double len = Math.max(0.3, to.length() - 0.2);
            cam = mid.add(to.normalize().multiply(len));
        }
        Vec3d look = mid.add(0, -0.05, 0).subtract(cam);
        double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, horiz));
        setRotation(yaw, pitch);
        setPos(cam.x, cam.y, cam.z);
    }
}
