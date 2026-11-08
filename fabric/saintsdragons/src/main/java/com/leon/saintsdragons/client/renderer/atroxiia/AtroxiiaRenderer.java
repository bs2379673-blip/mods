package com.leon.saintsdragons.client.renderer.atroxiia;

import com.leon.saintsdragons.client.renderer.layer.DragonEquipmentLayer;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.model.GeoBone;
import com.leon.saintsdragons.client.renderer.vfx.AtroxiiaQuakeTailRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import com.leon.saintsdragons.client.model.atroxiia.AtroxiiaModel;
import com.leon.saintsdragons.client.renderer.DragonGeoEntityRenderer;
import com.leon.saintsdragons.client.renderer.layer.atroxiia.AtroxiiaNightEmissiveLayer;
import com.leon.saintsdragons.server.entity.dragons.atroxiia.Atroxiia;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import org.joml.Vector3f;

public class AtroxiiaRenderer extends DragonGeoEntityRenderer<Atroxiia> {
    private Vec3 tailTipPosition;
    private static final String PASSENGER_BONE = "passengerBone";
    private static final float PASSENGER_X = 0.0f;
    private static final float PASSENGER_Y = -3.0f;
    private static final float PASSENGER_Z = 0.0f;

    public AtroxiiaRenderer(EntityRendererProvider.Context context) {
        super(context, new AtroxiiaModel());
        this.withRenderLayer(new DragonEquipmentLayer<>(
                this,
                Atroxiia::hasSaddle,
                SaintsDragonsCommon.rl("textures/entity/atroxiia/atroxiia_saddle_layer.png")
        ));
        this.withRenderLayer(new DragonEquipmentLayer<>(
                this,
                Atroxiia::hasAtroxiiaChest,
                SaintsDragonsCommon.rl("textures/entity/atroxiia/atroxiia_chest_layer.png")
        ));
        this.withRenderLayer(new AtroxiiaNightEmissiveLayer(this));
    }

    @Override
    protected java.util.Set<String> extraSampledBones(Atroxiia entity) {
        return java.util.Set.of("tailtip");
    }

    @Override
    protected void onDragonBone(Atroxiia entity, GeoBone bone, PoseStack poses) {
        if (!bone.name().equals("tailtip")) return;
        float partialTick = partialOf(null);
        var point = relativeAtPivot(poses).transformPosition(new Vector3f());
        tailTipPosition = new Vec3(point.x + entity.getX(), point.y + entity.getY(), point.z + entity.getZ());
    }

    @Override
    protected float getBabyShadowRadius(Atroxiia entity) {
        return 0.45F;
    }

    @Override
    protected float getAdultShadowRadius(Atroxiia entity) {
        return 2.0F;
    }

    @Override
    protected String[] trackedBoneNames() {
        return new String[] {PASSENGER_BONE, "tailtip"};
    }

    @Override
    protected LocatorSpec[] locatorSpecs(Atroxiia entity) {
        return new LocatorSpec[] {
                new LocatorSpec(PASSENGER_BONE, PASSENGER_X, PASSENGER_Y, PASSENGER_Z, "passengerLocator")
        };
    }
    @Override
    protected void afterDragonRender(Atroxiia entity, PoseStack poses, MultiBufferSource buffers, float partialTick) {
        AtroxiiaQuakeTailRenderer.render(entity, tailTipPosition, poses, buffers, partialTick);
    }
}
