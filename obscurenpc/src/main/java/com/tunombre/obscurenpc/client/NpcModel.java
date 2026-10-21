package com.tunombre.obscurenpc.client;

import com.tunombre.obscurenpc.Actor;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Modelo de jugador con las posturas y gestos de los NPC de "huesos normales" (a mano, sin animaciones de Epic Fight). */
public class NpcModel extends PlayerModel<Actor> {
    public NpcModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    private static void pose(ModelPart p, float x, float y, float z, float e) {
        p.xRot = Mth.lerp(e, p.xRot, x);
        p.yRot = Mth.lerp(e, p.yRot, y);
        p.zRot = Mth.lerp(e, p.zRot, z);
    }

    private static int duration(String g) {
        return switch (g) {
            case "talk_a", "talk_b", "talk_c", "talk_d", "talk_e" -> 55;
            case "wave", "beckon" -> 55;
            case "think", "look_around" -> 70;
            case "facepalm" -> 50;
            case "nod", "shake_head" -> 28;
            default -> 40;
        };
    }

    @Override
    public void setupAnim(Actor a, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        int st = a.stance();
        this.riding = st == 1;
        this.crouching = st == 3 || st == 9;
        super.setupAnim(a, limbSwing, limbSwingAmount, age, yaw, pitch);
        boolean still = limbSwingAmount < 0.05F;
        if (still) {
            switch (st) {
                case 4 -> { pose(rightArm, -0.85F, -0.55F, 0F, 1F); pose(leftArm, -0.85F, 0.55F, 0F, 1F); }
                case 5 -> { pose(rightArm, 0.45F, 0.2F, -0.15F, 1F); pose(leftArm, 0.45F, -0.2F, 0.15F, 1F); }
                case 6 -> { pose(rightArm, 0.05F, 0F, -0.55F, 1F); pose(leftArm, 0.05F, 0F, 0.55F, 1F); }
                case 7 -> { pose(rightArm, -1.9F, -0.4F, 0F, 1F); pose(leftArm, -0.9F, 0.5F, 0F, 1F); head.xRot += 0.12F; }
                case 8 -> { pose(rightArm, -1.15F, -0.3F, 0F, 1F); pose(leftArm, -1.15F, 0.3F, 0F, 1F); }
                default -> { }
            }
        }
        String g = a.gesture();
        if (!g.isEmpty() && still) {
            float t = age - a.gestureAt();
            int dur = duration(g);
            if (t >= 0 && t <= dur) {
                float e = Math.min(1F, Math.min(t / 6F, (dur - t) / 8F));
                float s = Mth.sin(t * 0.34F);
                switch (g) {
                    case "talk_a", "talk_d" -> { pose(rightArm, -0.95F + s * 0.35F, -0.2F, -0.2F, e); head.xRot += s * 0.05F * e; }
                    case "talk_c" -> { pose(leftArm, -0.95F + s * 0.35F, 0.2F, 0.2F, e); head.yRot += s * 0.05F * e; }
                    case "talk_b", "talk_e" -> {
                        pose(rightArm, -0.85F + s * 0.3F, -0.15F, -0.25F, e);
                        pose(leftArm, -0.85F - s * 0.3F, 0.15F, 0.25F, e);
                        head.xRot += s * 0.05F * e;
                    }
                    case "point" -> pose(rightArm, -1.55F, -0.1F, 0F, e);
                    case "wave" -> pose(rightArm, -2.9F, 0F, -0.3F + Mth.sin(t * 0.6F) * 0.45F, e);
                    case "salute" -> pose(rightArm, -2.5F, -0.4F, 0F, e);
                    case "shrug" -> { pose(rightArm, -0.5F, 0F, -0.75F, e); pose(leftArm, -0.5F, 0F, 0.75F, e); head.zRot += 0.1F * e; }
                    case "nod" -> head.xRot += (0.25F - 0.25F * Mth.cos(t * 0.7F)) * e;
                    case "shake_head" -> head.yRot += Mth.sin(t * 0.8F) * 0.5F * e;
                    case "look_around" -> head.yRot += Mth.sin(t * 0.11F) * 0.8F * e;
                    case "facepalm" -> { pose(rightArm, -2.1F, -0.3F, 0F, e); head.xRot += 0.2F * e; }
                    case "beckon" -> pose(rightArm, -1.3F + Mth.sin(t * 0.5F) * 0.4F, 0F, -0.2F, e);
                    case "stop" -> pose(rightArm, -1.5F, 0F, -0.15F, e);
                    case "think" -> { pose(rightArm, -1.9F, -0.4F, 0F, e); pose(leftArm, -0.9F, 0.5F, 0F, e); head.zRot += 0.1F * e; }
                    default -> { }
                }
            }
        }
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}
