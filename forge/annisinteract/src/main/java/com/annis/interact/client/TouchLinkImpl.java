package com.annis.interact.client;

import com.annis.interact.client.anim.PairAnims;
import com.annis.npc.client.Activities;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Modo mimos: da a las animaciones el punto de contacto, suavizado y en el modelo de cada uno. */
final class TouchLinkImpl implements PairAnims.TouchLink {
    @Override
    public PairAnims.Contact asToucher() {
        LivingEntity me = Activities.current();
        if (me == null) {
            return null;
        }
        ClientState.TouchState s = ClientState.TOUCHES.get(me.getId());
        if (s == null) {
            return null;
        }
        advance(s);
        Minecraft mc = Minecraft.getInstance();
        Entity other = mc.level == null ? null : mc.level.getEntity(s.receiver);
        if (other == null || s.w <= 0.001f) {
            return null;
        }
        float pt = mc.getPartialTick();
        Vec3 w = HandLinkImpl.toWorld(other.getPosition(pt), yaw(other, pt), s.smooth[0], s.smooth[1], s.smooth[2]);
        float[] mine = HandLinkImpl.toModel(me.getPosition(pt), yaw(me, pt), w);
        return new PairAnims.Contact(s.shownZone, mine, null, s.w);
    }

    @Override
    public PairAnims.Contact asReceiver() {
        LivingEntity me = Activities.current();
        if (me == null) {
            return null;
        }
        for (ClientState.TouchState s : ClientState.TOUCHES.values()) {
            if (s.receiver == me.getId()) {
                advance(s);
                if (s.w <= 0.001f) {
                    return null;
                }
                return new PairAnims.Contact(s.shownZone, s.smooth.clone(), s.start.clone(), s.w);
            }
        }
        return null;
    }

    /** Cuerpo: el giro fijado por la interaccion si lo hay. */
    static float yaw(Entity e, float pt) {
        Float anchored = ClientState.ANCHORS.get(e.getId());
        if (anchored != null) {
            return anchored;
        }
        if (e instanceof LivingEntity l) {
            return Mth.rotLerp(pt, l.yBodyRotO, l.yBodyRot);
        }
        return e.getViewYRot(pt);
    }

    /** Acerca lo dibujado a lo pedido (una vez por fotograma aunque se pregunte varias veces). */
    static void advance(ClientState.TouchState s) {
        long now = System.nanoTime();
        float dt = s.last == 0 ? 0 : Math.min(0.1f, (now - s.last) / 1.0e9f);
        if (s.last != 0 && dt < 0.002f) {
            return;
        }
        s.last = now;
        boolean on = s.active && s.zone != com.annis.interact.Touch.NONE;
        if (on && (s.w < 0.05f || s.shownZone != s.zone)) {
            // empieza a tocar (o cambia de zona): la mano sale de donde estaba hacia el punto
            if (s.w < 0.05f) {
                System.arraycopy(s.target, 0, s.smooth, 0, 3);
            }
            s.shownZone = s.zone;
        }
        float k = 1.0f - (float) Math.exp(-16.0f * dt);
        for (int i = 0; i < 3; i++) {
            s.smooth[i] += (s.target[i] - s.smooth[i]) * k;
        }
        float kw = 1.0f - (float) Math.exp(-(on ? 9.0f : 6.0f) * dt);
        s.w += ((on ? 1 : 0) - s.w) * kw;
    }
}
