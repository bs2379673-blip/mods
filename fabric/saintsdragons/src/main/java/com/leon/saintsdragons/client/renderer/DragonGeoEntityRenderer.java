package com.leon.saintsdragons.client.renderer;

import com.leon.saintsdragons.client.compat.geo.LegacyGeoEntityRenderer;
import com.leon.saintsdragons.client.compat.geo.SDRenderState;
import com.leon.saintsdragons.client.renderer.vfx.DragonDiveTrailRenderer;
import com.leon.saintsdragons.server.entity.base.RideableDragonBase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base de los dragones. Port 1.21.11 (GeckoLib 5): los huesos que siguen los jinetes, las bocas, alas, etc.
 * se toman al dibujar (en el hueso, con el PoseStack de ese momento) en vez de con las matrices de GeckoLib 4.
 */
public abstract class DragonGeoEntityRenderer<T extends RideableDragonBase> extends LegacyGeoEntityRenderer<T> {
    private static final double DIVE_TRAIL_RENDER_DISTANCE = 256.0D;
    private static final double DIVE_TRAIL_CULL_PADDING = 48.0D;
    /** Posicion en el mundo de los huesos seguidos en el ultimo dibujo (bloques). */
    private final Map<String, Vec3> boneWorld = new HashMap<>();
    /** Matriz del hueso relativa a la camara (para efectos que se dibujan pegados al hueso). */
    protected final Map<String, Matrix4f> bonePoses = new HashMap<>();
    /** El modelo del ultimo dibujo (GeckoLib 5). */
    protected software.bernie.geckolib.cache.model.BakedGeoModel lastBakedModel;
    private RenderPassInfo<SDRenderState> boneInfo;

    protected DragonGeoEntityRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    @Override
    public float getMotionAnimThreshold(T animatable) {
        return 0.000001f;
    }

    @Override
    protected float getDeathMaxRotation(T entity) {
        return 0.0F;
    }

    @Override
    public boolean shouldRender(@NotNull T entity, @NotNull Frustum frustum, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return true;
        }

        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double maxDistance = DIVE_TRAIL_RENDER_DISTANCE * DIVE_TRAIL_RENDER_DISTANCE;
        if (dx * dx + dy * dy + dz * dz > maxDistance) {
            return false;
        }
        if (DragonDiveTrailRenderer.getTrailIntensity(entity) <= 0.0F) {
            return false;
        }

        AABB trailBounds = entity.getBoundingBox().inflate(DIVE_TRAIL_CULL_PADDING);
        return frustum.isVisible(trailBounds);
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        return entity.getRenderCullingBox();
    }

    @Override
    public void extractRenderState(T entity, SDRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.shadowRadius = entity.isBaby() ? getBabyShadowRadius(entity) : getAdultShadowRadius(entity);
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<SDRenderState> info, float widthScale, float heightScale) {
        T entity = entityOf(info.renderState());
        float scale = entity == null ? 1 : getRenderScale(entity);
        super.scaleModelForRender(info, widthScale * scale, heightScale * scale);
    }

    @Override
    public RenderType getRenderType(T animatable, Identifier texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutNoCull(texture);
    }

    protected float getRenderScale(T entity) {
        return 1.0f;
    }

    protected abstract float getBabyShadowRadius(T entity);

    protected abstract float getAdultShadowRadius(T entity);

    protected String[] trackedBoneNames() {
        return new String[0];
    }

    protected LocatorSpec[] locatorSpecs(T entity) {
        return new LocatorSpec[0];
    }

    protected void afterDragonRender(T entity, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
    }

    /** Para los renderers que antes miraban cada hueso en renderRecursively. */
    protected void onDragonBone(T entity, GeoBone bone, PoseStack poseAtBone) {
    }

    protected Set<String> extraSampledBones(T entity) {
        return Set.of();
    }

    @Override
    protected String[] sampledBones(T entity) {
        Set<String> names = new LinkedHashSet<>();
        for (String n : trackedBoneNames()) {
            names.add(n);
        }
        for (LocatorSpec spec : locatorSpecs(entity)) {
            names.add(spec.boneName());
        }
        RiderConfig.RiderSpec riderSpec = RiderConfig.getSpec(entity);
        if (riderSpec != null) {
            riderSpec.seats().values().forEach(seat -> names.add(seat.boneName()));
        }
        names.addAll(extraSampledBones(entity));
        return names.toArray(new String[0]);
    }

    @Override
    protected void onBonePose(T entity, GeoBone bone, PoseStack poseAtBone, RenderPassInfo<SDRenderState> info) {
        this.boneInfo = info;
        this.lastBakedModel = info.model();
        boolean main = RenderPassContext.isExtractionAllowedNow() && !EntityPreviewRenderContext.isRendering()
                && !ShaderPassCompatibility.isIrisShadowPass();
        Vec3 cam = info.cameraState().pos;
        Matrix4f pose = new Matrix4f(poseAtBone.last().pose());
        String name = bone.name();
        this.bonePoses.put(name, pose);
        Vector3f origin = pose.transformPosition(new Vector3f());
        this.boneWorld.put(name, new Vec3(cam.x + origin.x, cam.y + origin.y, cam.z + origin.z));
        if (main) {
            for (LocatorSpec spec : locatorSpecs(entity)) {
                if (spec.boneName().equals(name)) {
                    Vec3 w = transformLocator(pose, cam, spec.x(), spec.y(), spec.z());
                    for (String locator : spec.locatorNames()) {
                        entity.setClientLocatorPosition(locator, w);
                    }
                }
            }
            RiderConfig.RiderSpec riderSpec = RiderConfig.getSpec(entity);
            if (riderSpec != null) {
                Matrix4f relative = new Matrix4f(info.getPreRenderMatrixState()).invert().mul(pose);
                float partial = partialOf(info.renderState());
                riderSpec.seats().forEach((index, seat) -> {
                    if (seat.boneName().equals(name)) {
                        DragonRiderAttachments.storeSeat(entity, index, partial, relative);
                        if (seat.locatorName() != null) {
                            Vector3f off = seat.locatorOffset();
                            entity.setClientLocatorPosition(seat.locatorName(), transformLocator(pose, cam, off.x(), off.y(), off.z()));
                        }
                    }
                });
            }
        }
        onDragonBone(entity, bone, poseAtBone);
    }

    @Override
    protected void renderExtras(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (EntityPreviewRenderContext.isRendering() || ShaderPassCompatibility.isIrisShadowPass()) {
            return;
        }
        afterDragonRender(entity, poseStack, bufferSource, partialTick);
    }

    /** El hueso (en su pivote) relativo al origen de la entidad, como hacia GeckoLib 4 con entityRenderTranslations. */
    protected Matrix4f relativeAtPivot(PoseStack poseAtBone) {
        Matrix4f pre = this.boneInfo == null ? new Matrix4f() : new Matrix4f(this.boneInfo.getPreRenderMatrixState());
        return pre.invert().mul(poseAtBone.last().pose());
    }

    /** Igual, pero en el origen del hueso (despues de volver del pivote). */
    protected Matrix4f relativeFull(GeoBone bone, PoseStack poseAtBone) {
        return relativeAtPivot(poseAtBone).translate(-bone.pivotX() / 16f, -bone.pivotY() / 16f, -bone.pivotZ() / 16f);
    }

    protected Vec3 getBoneWorldPosition(String boneName) {
        return this.boneWorld.get(boneName);
    }

    protected static Vec3 transformLocator(Matrix4f poseAtBone, Vec3 cam, float px, float py, float pz) {
        Vector4f out = new Matrix4f(poseAtBone).transform(new Vector4f(px / 16f, py / 16f, pz / 16f, 1f));
        return new Vec3(cam.x + out.x(), cam.y + out.y(), cam.z + out.z());
    }

    public record LocatorSpec(String boneName, float x, float y, float z, String... locatorNames) {
    }
}
