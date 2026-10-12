package com.tunombre.infectados.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.function.Function;

/** Renderer de Epic Fight con malla propia (una por mob o por variante) y escala propia (el de la motosierra es enorme). */
@SuppressWarnings({"rawtypes", "unchecked"})
public class InfRenderer extends PHumanoidRenderer {
    private final Function<LivingEntity, AssetAccessor<HumanoidMesh>> mesh;
    private final float sx, sy, sz;

    public InfRenderer(AssetAccessor<HumanoidMesh> def, Function<LivingEntity, AssetAccessor<HumanoidMesh>> mesh, EntityRendererProvider.Context ctx, EntityType<?> type, float sx, float sy, float sz) {
        super(def, ctx, type);
        this.mesh = mesh;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
    }

    @Override
    public AssetAccessor getMeshProvider(LivingEntityPatch patch) {
        return mesh.apply((LivingEntity) patch.getOriginal());
    }

    @Override
    public void mulPoseStack(PoseStack ps, Armature armature, LivingEntity e, LivingEntityPatch patch, float pt) {
        super.mulPoseStack(ps, armature, e, patch, pt);
        ps.scale(sx, sy, sz);
    }
}
