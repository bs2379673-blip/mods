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

                @Override
                public void apply(NpcModel m, float t) {
                    ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
                    Rig.P[] ps = this.rig.parts();
                    for (int i = 0; i < 6; i++) {
                        ModelPart p = parts[i];
                        ps[i].load(p.pitch, p.yaw, p.roll, p.pivotX, p.pivotY, p.pivotZ);
                    }
                    anim.pose().apply(this.rig, t);
                    ModelPart[] lowers = {null, null, m.rightForearm, m.leftForearm, m.rightShin, m.leftShin};
                    boolean jointed = m.rightForearm != null;
                    for (int i = 0; i < 6; i++) {
                        Rig.P r = ps[i];
                        if (r.touched && i >= 2) {
                            if (jointed) {
                                ModelPart lo = lowers[i];
                                lo.pitch = r.bend;
                                lo.yaw = 0;
                                lo.roll = 0;
                            } else if (r.rigid != null) {
                                // sin codos ni rodillas: recto hacia el punto
                                r.aim(r.rigid[0], r.rigid[1], r.rigid[2]);
                            }
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
                    return !name.startsWith("de_la_mano");
                }

                @Override
                public Activities.RootPose root(float t) {
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
