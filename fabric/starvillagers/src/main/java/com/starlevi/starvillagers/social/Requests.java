package com.starlevi.starvillagers.social;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starscenes.talk.ItemSpec;
import com.starlevi.starvillagers.StarVillagers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

/**
 * Pedidos: los aldeanos te piden cosas chicas ("traeme 5 panes") segun su oficio; si se las traes, te
 * lo agradecen con amistad y esmeraldas. Uno por dia y por jugador.
 * Acciones "pedido:pedir" y "pedido:entregar"; condiciones "pedido_activo", "pedido_listo", "pedido_libre";
 * marcadores %pedido% (lo que pidio) y %reaccion%.
 */
public final class Requests {
    private static final Random R = new Random();

    private Requests() {
    }

    private static ItemSpec spec(String s) {
        return s == null || s.isEmpty() ? null : ItemSpec.parse(s);
    }

    /** "5 Pan" (para los textos). */
    public static String text(Mind.Bond b) {
        ItemSpec sp = spec(b.request);
        if (sp == null) {
            return "";
        }
        return sp.count() + " " + sp.display().getHoverName().getString();
    }

    private static void save(Villager v, Mind m) {
        ((AttachmentTarget) (Object) v).setAttached(Social.MIND, m);
    }

    static boolean free(Villager v, ServerPlayer p) {
        Mind.Bond b = Social.mind(v).bond(p.getUUID());
        return b.request.isEmpty() && Social.day(v) > b.requestDay;
    }

    static String ask(ServerPlayer p, Villager v) {
        Mind m = Social.mind(v);
        Mind.Bond b = m.bond(p.getUUID());
        Personality pe = Personality.get(m.personality);
        if (!b.request.isEmpty()) {
            return pe.line("pedido_ya", R).replace("%pedido%", text(b));
        }
        if (Social.day(v) <= b.requestDay) {
            return pe.line("pedido_nada", R);
        }
        String prof = StarVillagers.profession(v);
        String pick = pe.line("pedidos_oficio_" + prof, R);
        if (pick.isEmpty() || R.nextInt(3) == 0) {
            pick = pe.line("pedidos", R);
        }
        if (spec(pick) == null) {
            pick = "minecraft:bread 3";
        }
        b.request = pick;
        b.requestDay = Social.day(v);
        save(v, m);
        return pe.line("pedido_pide", R).replace("%pedido%", text(b));
    }

    static boolean ready(Villager v, ServerPlayer p) {
        Mind.Bond b = Social.mind(v).bond(p.getUUID());
        ItemSpec sp = spec(b.request);
        return sp != null && sp.enough(p);
    }

    static String deliver(ServerPlayer p, Villager v) {
        Mind m = Social.mind(v);
        Mind.Bond b = m.bond(p.getUUID());
        Personality pe = Personality.get(m.personality);
        ItemSpec sp = spec(b.request);
        if (sp == null) {
            return pe.line("pedido_nada", R);
        }
        if (!sp.enough(p) || !sp.take(p)) {
            return pe.line("pedido_falta", R).replace("%pedido%", text(b));
        }
        b.request = "";
        b.requestDay = Social.day(v);
        m.addHearts(p.getUUID(), 8);
        m.addMood(12);
        save(v, m);
        int em = 1 + R.nextInt(3);
        ItemStack reward = new ItemStack(Items.EMERALD, em);
        if (!p.getInventory().add(reward)) {
            p.drop(reward, false);
        }
        StarScenesAPI.play(v, "happy");
        if (v.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + v.getBbHeight() + 0.2, v.getZ(), 8, 0.4, 0.3, 0.4, 0.02);
        }
        return pe.line("pedido_gracias", R).replace("%esmeraldas%", String.valueOf(em));
    }

    static void init(java.util.Map<java.util.UUID, String> reaction) {
        StarScenesAPI.registerAction("pedido", (p, s, val) -> {
            if (s instanceof Villager v) {
                String r = val.trim().equals("entregar") ? deliver(p, v) : ask(p, v);
                reaction.put(p.getUUID(), r.replace("%jugador%", p.getName().getString()));
            }
        });
        StarScenesAPI.registerCondition("pedido_activo", (p, s, val) -> s instanceof Villager v && !Social.mind(v).bond(p.getUUID()).request.isEmpty());
        StarScenesAPI.registerCondition("pedido_listo", (p, s, val) -> s instanceof Villager v && ready(v, p));
        StarScenesAPI.registerCondition("pedido_libre", (p, s, val) -> s instanceof Villager v && free(v, p));
        StarScenesAPI.registerPlaceholder("pedido", (p, s) -> s instanceof Villager v ? text(Social.mind(v).bond(p.getUUID())) : "");
    }

    static List<String> unused() {
        return List.of();
    }
}
