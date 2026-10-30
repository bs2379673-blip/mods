package com.annis.interact.client;

import com.annis.interact.client.anim.PairAnims;
import com.annis.interact.client.anim.Rig;
import com.annis.npc.client.Activities;
import com.annis.npc.client.NpcModel;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;

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
                    ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
                    Rig.P[] ps = this.rig.parts();
                    this.rig.walkLegs = false;
                    float baseR = m.rightLeg.xRot, baseL = m.leftLeg.xRot;
                    for (int i = 0; i < 6; i++) {
                        ModelPart p = parts[i];
                        ps[i].load(p.xRot, p.yRot, p.zRot, p.x, p.y, p.z);
                    }
                    anim.pose().apply(this.rig, t);
                    // camina con las piernas si la animacion deja las piernas al paso (o no las toca)
                    boolean walks = this.rig.walkLegs || !ps[4].touched && !ps[5].touched;
                    com.annis.npc.client.punchy.BedrockClip pc = packClip();
                    if (pc != null) {
                        com.annis.npc.client.punchy.PunchyClips.apply(pc, m, t);
                        if (walks) {
                            walkLegs(m, 0, 0);
                        }
                        return;
                    }
                    for (int i = 0; i < 6; i++) {
                        Rig.P r = ps[i];
                        if (r.touched && i >= 2) {
                            Rig.straighten(r);
                        }
                        if (r.touched) {
                            ModelPart p = parts[i];
                            p.xRot = r.pitch;
                            p.yRot = r.yaw;
                            p.zRot = r.roll;
                            p.x = r.x;
                            p.y = r.y;
                            p.z = r.z;
                        }
                    }
                    if (walks) {
                        // las piernas guardaron el paso del modelo: se cambia por el paso real
                        walkLegs(m, ps[4].touched ? baseR : m.rightLeg.xRot, ps[5].touched ? baseL : m.leftLeg.xRot);
                    }
                }

                /**
                 * Paso de caminar calculado con el movimiento real de la entidad (como vanilla), sumado a
                 * la pose de la animacion. No depende de lo que otros mods (Punchy, EMF) dejen en el modelo.
                 */
                private void walkLegs(NpcModel m, float baseR, float baseL) {
                    net.minecraft.world.entity.LivingEntity e = Activities.current();
                    if (e == null) {
                        return;
                    }
                    float pt = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                    float pos = e.walkAnimation.position(pt);
                    float spd = Math.min(1.0f, e.walkAnimation.speed(pt));
                    float sw = net.minecraft.util.Mth.cos(pos * 0.6662f) * 1.4f * spd * 0.5f;
                    m.rightLeg.xRot += sw - baseR;
                    m.leftLeg.xRot += -sw - baseL;
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
