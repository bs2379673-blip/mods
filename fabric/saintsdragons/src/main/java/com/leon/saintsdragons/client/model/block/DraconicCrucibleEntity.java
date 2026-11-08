package com.leon.saintsdragons.client.model.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public class DraconicCrucibleEntity {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(Identifier.fromNamespaceAndPath("saintsdragons", "draconic_crucible"), "main");
	private final ModelPart root;
	private final ModelPart neck;
	private final ModelPart upperjaw;
	private final ModelPart lowerjaw;

	public DraconicCrucibleEntity(ModelPart root) {
		this.root = root.getChild("root");
		this.neck = this.root.getChild("neck");
		this.upperjaw = this.root.getChild("upperjaw");
		this.lowerjaw = this.root.getChild("lowerjaw");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(-1.0F, 20.0F, 0.0F));

		PartDefinition neck = root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -12.0F, -7.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

		PartDefinition upperjaw = root.addOrReplaceChild("upperjaw", CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, -14.0F, -10.0F, 11.0F, 14.0F, 20.0F, new CubeDeformation(0.0F))
				.texOffs(39, 94).addBox(-2.0F, -14.0F, -10.0F, 2.0F, 6.0F, 20.0F, new CubeDeformation(0.01F))
				.texOffs(64, 0).addBox(0.0F, -25.0F, -8.0F, 11.0F, 11.0F, 16.0F, new CubeDeformation(0.0F))
				.texOffs(0, 101).addBox(-4.0F, -25.0F, -8.0F, 4.0F, 11.0F, 16.0F, new CubeDeformation(0.01F)), PartPose.offset(1.0F, -2.0F, 0.0F));

		PartDefinition cube_r1 = upperjaw.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(54, 66).addBox(-1.0F, -5.75F, -10.0F, 5.0F, 7.0F, 5.0F, new CubeDeformation(-0.01F))
				.texOffs(54, 66).addBox(-1.0F, -5.75F, -25.0F, 5.0F, 7.0F, 5.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(11.0F, 0.6F, 15.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition lowerjaw = root.addOrReplaceChild("lowerjaw", CubeListBuilder.create().texOffs(0, 101).mirror().addBox(0.0F, -23.0F, -8.0F, 4.0F, 11.0F, 16.0F, new CubeDeformation(-0.01F)).mirror(false)
				.texOffs(62, 32).addBox(-11.0F, -14.0F, -10.0F, 11.0F, 14.0F, 20.0F, new CubeDeformation(0.0F))
				.texOffs(0, 66).addBox(-11.0F, -25.0F, -8.0F, 11.0F, 11.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, -2.0F, 0.0F));

		PartDefinition cube_r2 = lowerjaw.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(54, 66).mirror().addBox(-4.0F, -5.75F, -10.0F, 5.0F, 7.0F, 5.0F, new CubeDeformation(-0.01F)).mirror(false)
				.texOffs(54, 66).mirror().addBox(-4.0F, -5.75F, -25.0F, 5.0F, 7.0F, 5.0F, new CubeDeformation(-0.01F)).mirror(false), PartPose.offsetAndRotation(-11.0F, 0.6F, 15.0F, 0.0F, 0.0F, 0.3927F));

		return LayerDefinition.create(meshdefinition, 128, 128);
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
