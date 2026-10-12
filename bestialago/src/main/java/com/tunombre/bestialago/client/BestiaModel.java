package com.tunombre.bestialago.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tunombre.bestialago.Bestia;
import com.tunombre.bestialago.BestiaAnim;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Modelo de cubos de la Bestia. GENERADO por export_java.py desde model.py (62 cubos). */
public class BestiaModel extends EntityModel<Bestia> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart barbel1_r;
    private final ModelPart barbel2_r;
    private final ModelPart barbel1_l;
    private final ModelPart barbel2_l;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart tail4;
    private final ModelPart fin_r;
    private final ModelPart rfin_r;
    private final ModelPart fin_l;
    private final ModelPart rfin_l;
    private final float[] rot = new float[BestiaAnim.COUNT * 3];

    public BestiaModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = root.getChild("body").getChild("neck");
        this.head = root.getChild("body").getChild("neck").getChild("head");
        this.jaw = root.getChild("body").getChild("neck").getChild("head").getChild("jaw");
        this.barbel1_r = root.getChild("body").getChild("neck").getChild("head").getChild("barbel1_r");
        this.barbel2_r = root.getChild("body").getChild("neck").getChild("head").getChild("barbel1_r").getChild("barbel2_r");
        this.barbel1_l = root.getChild("body").getChild("neck").getChild("head").getChild("barbel1_l");
        this.barbel2_l = root.getChild("body").getChild("neck").getChild("head").getChild("barbel1_l").getChild("barbel2_l");
        this.tail1 = root.getChild("body").getChild("tail1");
        this.tail2 = root.getChild("body").getChild("tail1").getChild("tail2");
        this.tail3 = root.getChild("body").getChild("tail1").getChild("tail2").getChild("tail3");
        this.tail4 = root.getChild("body").getChild("tail1").getChild("tail2").getChild("tail3").getChild("tail4");
        this.fin_r = root.getChild("body").getChild("fin_r");
        this.rfin_r = root.getChild("body").getChild("tail1").getChild("rfin_r");
        this.fin_l = root.getChild("body").getChild("fin_l");
        this.rfin_l = root.getChild("body").getChild("tail1").getChild("rfin_l");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10F, -8F, -11F, 20F, 15F, 22F)
                .texOffs(76, 83).addBox(-1F, -11F, -9F, 2F, 3F, 5F)
                .texOffs(0, 83).addBox(-1F, -11F, 0F, 2F, 3F, 6F),
                PartPose.offset(0F, 0F, 0F));
        PartDefinition p_neck = p_body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(0, 63).addBox(-9F, -6F, -8F, 18F, 12F, 8F),
                PartPose.offset(0F, -1F, -11F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(114, 0).addBox(-11F, -6F, -22F, 22F, 7F, 22F)
                .texOffs(86, 63).addBox(-8F, -8F, -16F, 16F, 2F, 13F)
                .texOffs(16, 83).addBox(-5F, -9F, -12F, 10F, 1F, 8F)
                .texOffs(16, 92).addBox(-10F, -4F, -22.5F, 2F, 2F, 1F)
                .texOffs(22, 92).addBox(8F, -4F, -22.5F, 2F, 2F, 1F)
                .texOffs(180, 83).addBox(-10.0F, 1F, -21.5F, 1F, 3F, 1F)
                .texOffs(164, 83).addBox(-7.55F, 1F, -21.5F, 1F, 4F, 1F)
                .texOffs(184, 83).addBox(-5.1F, 1F, -21.5F, 1F, 3F, 1F)
                .texOffs(168, 83).addBox(-2.6499999999999995F, 1F, -21.5F, 1F, 4F, 1F)
                .texOffs(188, 83).addBox(-0.1999999999999993F, 1F, -21.5F, 1F, 3F, 1F)
                .texOffs(172, 83).addBox(2.25F, 1F, -21.5F, 1F, 4F, 1F)
                .texOffs(192, 83).addBox(4.700000000000001F, 1F, -21.5F, 1F, 3F, 1F)
                .texOffs(176, 83).addBox(7.150000000000002F, 1F, -21.5F, 1F, 4F, 1F)
                .texOffs(196, 83).addBox(9.600000000000001F, 1F, -21.5F, 1F, 3F, 1F)
                .texOffs(200, 83).addBox(-11.0F, 1F, -19.0F, 1F, 3F, 1F)
                .texOffs(204, 83).addBox(-11.0F, 1F, -15.8F, 1F, 3F, 1F)
                .texOffs(208, 83).addBox(-11.0F, 1F, -12.6F, 1F, 3F, 1F)
                .texOffs(212, 83).addBox(-11.0F, 1F, -9.399999999999999F, 1F, 3F, 1F)
                .texOffs(216, 83).addBox(-11.0F, 1F, -6.199999999999999F, 1F, 3F, 1F)
                .texOffs(220, 83).addBox(10.0F, 1F, -19.0F, 1F, 3F, 1F)
                .texOffs(224, 83).addBox(10.0F, 1F, -15.8F, 1F, 3F, 1F)
                .texOffs(228, 83).addBox(10.0F, 1F, -12.6F, 1F, 3F, 1F)
                .texOffs(232, 83).addBox(10.0F, 1F, -9.399999999999999F, 1F, 3F, 1F)
                .texOffs(236, 83).addBox(10.0F, 1F, -6.199999999999999F, 1F, 3F, 1F),
                PartPose.offset(0F, -1F, -7F));
        PartDefinition p_jaw = p_head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(0, 37).addBox(-10.5F, 0F, -21F, 21F, 5F, 21F)
                .texOffs(240, 83).addBox(-9.0F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(244, 83).addBox(-6.4F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(248, 83).addBox(-3.8F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(252, 83).addBox(-1.1999999999999993F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(0, 92).addBox(1.4000000000000004F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(4, 92).addBox(4.0F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(8, 92).addBox(6.600000000000001F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(12, 92).addBox(9.2F, -3F, -20.5F, 1F, 3F, 1F)
                .texOffs(28, 92).addBox(-10.5F, -2F, -17.0F, 1F, 2F, 1F)
                .texOffs(32, 92).addBox(-10.5F, -2F, -13.8F, 1F, 2F, 1F)
                .texOffs(36, 92).addBox(-10.5F, -2F, -10.6F, 1F, 2F, 1F)
                .texOffs(40, 92).addBox(-10.5F, -2F, -7.399999999999999F, 1F, 2F, 1F)
                .texOffs(44, 92).addBox(-10.5F, -2F, -4.199999999999999F, 1F, 2F, 1F)
                .texOffs(48, 92).addBox(9.5F, -2F, -17.0F, 1F, 2F, 1F)
                .texOffs(52, 92).addBox(9.5F, -2F, -13.8F, 1F, 2F, 1F)
                .texOffs(56, 92).addBox(9.5F, -2F, -10.6F, 1F, 2F, 1F)
                .texOffs(60, 92).addBox(9.5F, -2F, -7.399999999999999F, 1F, 2F, 1F)
                .texOffs(64, 92).addBox(9.5F, -2F, -4.199999999999999F, 1F, 2F, 1F),
                PartPose.offset(0F, 1F, 0F));
        PartDefinition p_barbel1_r = p_head.addOrReplaceChild("barbel1_r", CubeListBuilder.create()
                .texOffs(236, 63).addBox(-0.5F, 0F, -0.5F, 1F, 9F, 1F),
                PartPose.offset(11F, 0F, -18F));
        PartDefinition p_barbel2_r = p_barbel1_r.addOrReplaceChild("barbel2_r", CubeListBuilder.create()
                .texOffs(52, 83).addBox(-0.5F, 0F, -0.5F, 1F, 8F, 1F),
                PartPose.offset(0F, 9F, 0F));
        PartDefinition p_barbel1_l = p_head.addOrReplaceChild("barbel1_l", CubeListBuilder.create()
                .texOffs(240, 63).addBox(-0.5F, 0F, -0.5F, 1F, 9F, 1F),
                PartPose.offset(-11F, 0F, -18F));
        PartDefinition p_barbel2_l = p_barbel1_l.addOrReplaceChild("barbel2_l", CubeListBuilder.create()
                .texOffs(56, 83).addBox(-0.5F, 0F, -0.5F, 1F, 8F, 1F),
                PartPose.offset(0F, 9F, 0F));
        PartDefinition p_tail1 = p_body.addOrReplaceChild("tail1", CubeListBuilder.create()
                .texOffs(84, 37).addBox(-8F, -6F, 0F, 16F, 12F, 14F)
                .texOffs(90, 83).addBox(-1F, -9F, 3F, 2F, 3F, 5F),
                PartPose.offset(0F, 0F, 11F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                .texOffs(144, 37).addBox(-6F, -5F, 0F, 12F, 10F, 14F)
                .texOffs(60, 83).addBox(-1F, -8F, 3F, 2F, 3F, 6F),
                PartPose.offset(0F, 0F, 14F));
        PartDefinition p_tail3 = p_tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                .texOffs(196, 37).addBox(-4F, -4F, 0F, 8F, 8F, 14F),
                PartPose.offset(0F, 0F, 14F));
        PartDefinition p_tail4 = p_tail3.addOrReplaceChild("tail4", CubeListBuilder.create()
                .texOffs(52, 63).addBox(-2.5F, -3F, 0F, 5F, 6F, 12F)
                .texOffs(84, 0).addBox(-0.5F, -10F, 2F, 1F, 20F, 14F),
                PartPose.offset(0F, 0F, 14F));
        PartDefinition p_fin_r = p_body.addOrReplaceChild("fin_r", CubeListBuilder.create()
                .texOffs(144, 63).addBox(0F, -1F, -4F, 14F, 2F, 9F),
                PartPose.offset(10F, 4F, -5F));
        PartDefinition p_rfin_r = p_tail1.addOrReplaceChild("rfin_r", CubeListBuilder.create()
                .texOffs(104, 83).addBox(0F, -1F, -3F, 9F, 2F, 6F),
                PartPose.offset(8F, 4F, 6F));
        PartDefinition p_fin_l = p_body.addOrReplaceChild("fin_l", CubeListBuilder.create()
                .texOffs(190, 63).addBox(-14F, -1F, -4F, 14F, 2F, 9F),
                PartPose.offset(-10F, 4F, -5F));
        PartDefinition p_rfin_l = p_tail1.addOrReplaceChild("rfin_l", CubeListBuilder.create()
                .texOffs(134, 83).addBox(-9F, -1F, -3F, 9F, 2F, 6F),
                PartPose.offset(-8F, 4F, 6F));
        return LayerDefinition.create(mesh, 256, 128);
    }

    private void set(ModelPart p, int i) {
        p.xRot = rot[i * 3];
        p.yRot = rot[i * 3 + 1];
        p.zRot = rot[i * 3 + 2];
    }

    /** limbSwing.. no se usan: la pose sale de los valores interpolados de la entidad */
    @Override
    public void setupAnim(Bestia e, float a, float b, float ageInTicks, float headYaw, float headPitch) {
        float pt = ageInTicks - e.tickCount;
        e.animPose(rot, pt);
        set(body, BestiaAnim.BODY);
        set(neck, BestiaAnim.NECK);
        set(head, BestiaAnim.HEAD);
        set(jaw, BestiaAnim.JAW);
        set(barbel1_r, BestiaAnim.BARBEL1_R);
        set(barbel2_r, BestiaAnim.BARBEL2_R);
        set(barbel1_l, BestiaAnim.BARBEL1_L);
        set(barbel2_l, BestiaAnim.BARBEL2_L);
        set(tail1, BestiaAnim.TAIL1);
        set(tail2, BestiaAnim.TAIL2);
        set(tail3, BestiaAnim.TAIL3);
        set(tail4, BestiaAnim.TAIL4);
        set(fin_r, BestiaAnim.FIN_R);
        set(rfin_r, BestiaAnim.RFIN_R);
        set(fin_l, BestiaAnim.FIN_L);
        set(rfin_l, BestiaAnim.RFIN_L);
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(ps, vc, light, overlay, r, g, b, a);
    }
}
