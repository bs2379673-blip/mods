package com.annis.interact.client;

import com.annis.interact.client.anim.PairAnims;
import com.annis.interact.client.anim.Rig;
import com.annis.npc.client.Activities;
import com.annis.npc.client.NpcModel;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/** Registra las animaciones de pareja en Annis NPC (sirven para NPC, aldeanos y jugadores). */
public final class RigBridge {
    private RigBridge() {
    }

    public static void registerAll() {
        for (Map.Entry<String, PairAnims.Anim> e : PairAnims.ALL.entrySet()) {
            PairAnims.Anim anim = e.getValue();
            String name = e.getKey();
            Activities.register(e.getKey(), new Activities.Custom() {
                private final Rig rig = new Rig();

                /** Version del paquete (retocada en Punchy Studio), si existe y la animacion no es dinamica. */
                private com.annis.npc.client.punchy.BedrockClip packClip() {
                    if (name.startsWith("de_la_mano") || name.startsWith("mimos_")) {
                        return null;
                    }
                    return com.annis.npc.client.punchy.PunchyClips.get("annis_" + name);
                }

                @Override
                public void apply(NpcModel m, float t) {
                    com.annis.npc.client.punchy.BedrockClip pc = packClip();
                    if (pc != null) {
                        com.annis.npc.client.punchy.PunchyClips.apply(pc, m, t);
                        return;
                    }
                    ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
                    Rig.P[] ps = this.rig.parts();
                    this.rig.walkLegs = false;
                    for (int i = 0; i < 6; i++) {
                        ModelPart p = parts[i];
                        ps[i].load(p.pitch, p.yaw, p.roll, p.pivotX, p.pivotY, p.pivotZ);
                    }
                    anim.pose().apply(this.rig, t);
                    for (int i = 0; i < 6; i++) {
                        Rig.P r = ps[i];
                        if (r.touched && i >= 2) {
                            Rig.straighten(r);
                        }
                        if (r.touched) {
                            ModelPart p = parts[i];
                            p.pitch = r.pitch;
                            p.yaw = r.yaw;
                            p.roll = r.roll;
                            p.pivotX = r.x;
                            p.pivotY = r.y;
                            p.pivotZ = r.z;
                        }
                    }
                }

                @Override
                public boolean springy() {
                    // de la mano: la mano sigue al companero real sin retraso
                    // mimos: la mano sigue al raton (ya va suavizada) sin retraso extra
                    return !name.startsWith("de_la_mano") && !name.startsWith("mimos_");
                }

                @Override
                public boolean springPart(int i) {
                    // las piernas que siguen el paso de caminar no se suavizan: perderian el vaiven
                    return !(i >= 4 && this.rig.walkLegs);
                }

                @Override
                public Activities.RootPose root(float t) {
                    com.annis.npc.client.punchy.BedrockClip pc = packClip();
                    if (pc != null) {
                        return com.annis.npc.client.punchy.PunchyClips.root(pc, t);
                    }
                    if (anim.root() == null) {
                        return null;
                    }
                    Rig.Root r = anim.root().at(t);
                    return r == null ? null : new Activities.RootPose(r.pitch(), r.yaw(), r.roll(), r.forward(), r.up(), r.left());
                }
            });
        }
    }
}
