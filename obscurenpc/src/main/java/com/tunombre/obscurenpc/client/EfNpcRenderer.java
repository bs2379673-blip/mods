package com.tunombre.obscurenpc.client;

import com.tunombre.obscurenpc.Actor;
import com.tunombre.obscurenpc.ActorPatch;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer;

/** Renderer de Epic Fight del NPC: malla de jugador (brazos anchos o finos segun la skin). */
public class EfNpcRenderer extends PHumanoidRenderer<Actor, ActorPatch, NpcModel, NpcRenderer, HumanoidMesh> {
    public EfNpcRenderer(EntityRendererProvider.Context ctx, EntityType<?> type) {
        super(Meshes.BIPED, ctx, type);
    }

    @Override
    public AssetAccessor<HumanoidMesh> getMeshProvider(ActorPatch patch) {
        return patch.getOriginal().getEntityData().get(Actor.SLIM) ? Meshes.ALEX : Meshes.BIPED;
    }
}
