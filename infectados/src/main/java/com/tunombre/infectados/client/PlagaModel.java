package com.tunombre.infectados.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/**
 * La plaga que sale del cuello (tipo guillotina): tallo de tres tramos y una cuchilla curva arriba.
 * Coordenadas del hueso de la cabeza de Epic Fight (y = hacia arriba desde el cuello).
 */
public class PlagaModel {
    private final ModelPart root, s1, s2, s3, blade;

    public PlagaModel(ModelPart root) {
        this.root = root;
        this.s1 = root.getChild("s1");
        this.s2 = s1.getChild("s2");
        this.s3 = s2.getChild("s3");
        this.blade = s3.getChild("blade");
    }

    public static LayerDefinition create() {
        MeshDefinition m = new MeshDefinition();
        PartDefinition r = m.getRoot();
        PartDefinition p1 = r.addOrReplaceChild("s1", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, 0F, -3F, 6, 8, 6)
                .texOffs(24, 0).addBox(-4F, -1F, -4F, 8, 3, 8), PartPose.ZERO);
        PartDefinition p2 = p1.addOrReplaceChild("s2", CubeListBuilder.create().texOffs(0, 14).addBox(-2.5F, 0F, -2.5F, 5, 9, 5), PartPose.offset(0, 8, 0));
        PartDefinition p3 = p2.addOrReplaceChild("s3", CubeListBuilder.create().texOffs(20, 14).addBox(-2F, 0F, -2F, 4, 8, 4)
                .texOffs(36, 14).addBox(-3F, 6F, -3F, 6, 4, 6), PartPose.offset(0, 9, 0));
        p3.addOrReplaceChild("blade", CubeListBuilder.create()
                .texOffs(0, 28).addBox(-1F, -1F, 0F, 2, 3, 20)          // hoja larga hacia un lado
                .texOffs(44, 28).addBox(-0.5F, -3F, 16F, 1, 3, 6)       // punta curva
                .texOffs(0, 51).addBox(-1F, -1F, -8F, 2, 3, 8),          // contrapeso
                PartPose.offset(0, 9, 0));
        return LayerDefinition.create(m, 64, 64);
    }

    /** t = ticks; attack = ticks desde que empezo el tajo (o grande si no ataca) */
    public void pose(float t, float attack) {
        float idle = 1F;
        s1.xRot = 0.18F * Mth.sin(t * 0.11F);
        s1.zRot = 0.14F * Mth.sin(t * 0.09F + 1F);
        s1.yRot = 0.4F * Mth.sin(t * 0.05F);
        s2.xRot = 0.3F * Mth.sin(t * 0.11F + 0.8F);
        s2.zRot = 0.2F * Mth.sin(t * 0.1F + 2F);
        s3.xRot = 0.35F * Mth.sin(t * 0.11F + 1.6F);
        blade.yRot = 0.6F * Mth.sin(t * 0.07F);
        blade.xRot = 0.15F * Mth.sin(t * 0.13F);
        if (attack >= 0 && attack < 32) {
            if (attack < 15) {                 // se arma: se echa atras y se eleva
                float k = attack / 15F;
                s1.xRot = -0.5F * k;
                s2.xRot = -0.6F * k;
                s3.xRot = -0.3F * k;
                blade.yRot = -1.2F * k;
            } else if (attack < 26) {          // tajo: se tumba en horizontal y da la vuelta entera
                float k = (attack - 15) / 11F;
                s1.xRot = 1.25F;
                s2.xRot = 0.25F;
                s3.xRot = 0.1F;
                s1.yRot = k * (float) (Math.PI * 2);
                blade.yRot = 1.57F;
            } else {                           // vuelve
                float k = (attack - 26) / 6F;
                s1.xRot = 1.25F * (1 - k);
                s2.xRot = 0.25F * (1 - k);
                blade.yRot = 1.57F * (1 - k);
            }
        }
    }

    public void render(PoseStack ps, VertexConsumer vc, int light, int overlay) {
        root.render(ps, vc, light, overlay);
    }
}
