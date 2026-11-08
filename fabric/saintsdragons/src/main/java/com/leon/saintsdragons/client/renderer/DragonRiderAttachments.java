package com.leon.saintsdragons.client.renderer;

import com.leon.saintsdragons.server.entity.base.RideableDragonBase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Render-thread attachment poses.
 * Port 1.21.11 (GeckoLib 5): las animaciones solo se evaluan al dibujar, asi que el asiento de cada
 * jinete es el del ultimo cuadro en que se dibujo el dragon (un cuadro de atraso, no se nota).
 */
public final class DragonRiderAttachments {
    private static final Map<UUID, FramePose> POSES = new HashMap<>();
    private static Object activeLevel;
    private static long frame;

    private DragonRiderAttachments() {}

    public static void beginRenderFrame(@Nullable Object level) {
        if (activeLevel != level) {
            clear();
            activeLevel = level;
        }
        frame++;
        POSES.values().removeIf(pose -> pose.lastUsedFrame < frame - 40);
    }

    public static void clear() {
        POSES.clear();
        activeLevel = null;
    }

    public static boolean usesPoseCache(RideableDragonBase dragon) {
        return dragon.isVehicle() && !dragon.isRemoved() && RiderConfig.getSpec(dragon) != null
                && dragon.level() == Minecraft.getInstance().level
                && !EntityPreviewRenderContext.isRendering();
    }

    @Nullable
    public static SeatPose resolve(RideableDragonBase dragon, int seat, float partialTick) {
        if (!usesPoseCache(dragon) || !RiderConfig.getSpec(dragon).seats().containsKey(seat)) {
            return null;
        }
        FramePose pose = POSES.get(dragon.getUUID());
        if (pose == null || pose.entityId != dragon.getId()) {
            return null;
        }
        pose.lastUsedFrame = frame;
        return pose.seats.get(seat);
    }

    /** boneTransform: el hueso del asiento relativo a la posicion del dragon (en bloques, ya girado con el). */
    public static void storeSeat(RideableDragonBase dragon, int seat, float partialTick, Matrix4f boneTransform) {
        if (!usesPoseCache(dragon) || !boneTransform.isFinite()
                || Math.abs(boneTransform.determinant3x3()) < 1.0E-8F) {
            return;
        }
        if (ShaderPassCompatibility.isIrisShadowPass()) {
            return;
        }
        FramePose pose = POSES.computeIfAbsent(dragon.getUUID(), ignored -> new FramePose());
        pose.entityId = dragon.getId();
        pose.lastUsedFrame = frame;
        // Bone translation includes model scale; the rider keeps their own size.
        Matrix4f riderTransform = new Matrix4f(boneTransform).normalize3x3();
        riderTransform.translate(RiderConfig.getSeatOffset(dragon, seat));
        float yaw = Mth.rotLerp(partialTick, dragon.yBodyRotO, dragon.yBodyRot);
        riderTransform.rotateY((yaw + RiderConfig.getYawOffset(dragon, seat)) * Mth.DEG_TO_RAD);
        pose.seats.put(seat, new SeatPose(riderTransform));
    }

    public static boolean transformRider(Entity rider, float partialTick, PoseStack poses, Vec3 renderOffset) {
        if (!(rider.getVehicle() instanceof RideableDragonBase dragon)) {
            return false;
        }
        SeatPose seat = resolve(dragon, dragon.getRiderSeatIndex(rider), partialTick);
        if (seat == null) {
            return false;
        }
        Vec3 delta = interpolatedPosition(dragon, partialTick)
                .subtract(interpolatedPosition(rider, partialTick)).subtract(renderOffset);
        poses.translate(delta.x, delta.y, delta.z);
        poses.mulPose(seat.riderTransform);
        poses.last().normal().mul(new Matrix3f(seat.riderTransform).invert().transpose());
        return true;
    }

    public static Vec3 interpolatedPosition(Entity entity, float partialTick) {
        // Match LevelRenderer, including remote entity interpolation.
        return new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    public static final class SeatPose {
        private final Matrix4f riderTransform;

        private SeatPose(Matrix4f riderTransform) {
            this.riderTransform = riderTransform;
        }

        public Vec3 eyePosition(RideableDragonBase dragon, Entity rider, float partialTick) {
            return worldPosition(dragon, partialTick, new Vec3(0, rider.getEyeHeight(), 0));
        }

        public Vec3 worldPosition(RideableDragonBase dragon, float partialTick, Vec3 riderLocalPosition) {
            Vector3f point = riderTransform.transformPosition(riderLocalPosition.toVector3f());
            return interpolatedPosition(dragon, partialTick).add(point.x, point.y, point.z);
        }

        public float cameraRoll(float yawDegrees, float pitchDegrees) {
            var look = new org.joml.Quaternionf().rotationYXZ(-yawDegrees * Mth.DEG_TO_RAD,
                    pitchDegrees * Mth.DEG_TO_RAD, 0);
            Vector3f up = riderTransform.transformDirection(0, 1, 0, new Vector3f()).normalize();
            Vector3f viewUp = look.transform(new Vector3f(0, 1, 0));
            Vector3f viewLeft = look.transform(new Vector3f(1, 0, 0));
            float x = up.dot(viewLeft);
            float y = up.dot(viewUp);
            if (x * x + y * y < 1.0E-6F) {
                // Looking along the seat's up axis: use its left axis to resolve the horizon.
                Vector3f left = riderTransform.transformDirection(1, 0, 0, new Vector3f()).normalize();
                return (float) Math.atan2(left.dot(viewUp), left.dot(viewLeft)) * Mth.RAD_TO_DEG;
            }
            return (float) Math.atan2(-x, y) * Mth.RAD_TO_DEG;
        }
    }

    private static final class FramePose {
        private long lastUsedFrame;
        private int entityId;
        private final Map<Integer, SeatPose> seats = new HashMap<>();
    }
}
