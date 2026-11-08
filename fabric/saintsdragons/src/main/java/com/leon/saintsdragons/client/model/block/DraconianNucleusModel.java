package com.leon.saintsdragons.client.model.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public class DraconianNucleusModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("saintsdragons", "draconian_nucleus"), "main");

    private final ModelPart root;

    public DraconianNucleusModel(ModelPart bakedRoot) {
        this.root = bakedRoot.getChild("root");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition modelRoot = mesh.getRoot();
        PartDefinition root = modelRoot.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 16.0F, 0.0F));
        PartDefinition group = root.addOrReplaceChild("group", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition outer = group.addOrReplaceChild("outerlayer",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        outer.addOrReplaceChild("innerlayer",
                CubeListBuilder.create().texOffs(0, 24)
                        .addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 48, 48);
    }

	// port 1.21.11: las animaciones de bloques se "hornean" sobre las partes del modelo
	private final java.util.Map<AnimationDefinition, net.minecraft.client.animation.KeyframeAnimation> baked = new java.util.IdentityHashMap<>();

	public void animate(AnimationDefinition animation, long timeMillis) {
		this.root.getAllParts().forEach(ModelPart::resetPose);
		this.baked.computeIfAbsent(animation, a -> a.bake(this.root)).apply(timeMillis, 1.0F);
	}

	public ModelPart root() {
		return this.root;
	}

	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}
