package com.starlevi.starvillagers.family;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.Looks;
import com.starlevi.starvillagers.StarVillagers;
import com.starlevi.starvillagers.social.Mind;
import com.starlevi.starvillagers.social.Social;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Noviazgo, casamiento (con el anillo), hijos y el arbol familiar. Los aldeanos que tienen un bebe
 * entre ellos (como en Minecraft) quedan casados y el bebe hereda sus genes.
 * Acciones: "familia:noviazgo|casamiento|hijo|terminar|arbol". Condiciones: "pareja", "novios",
 * "casados", "libre", "tiene_anillo", "puede_tener_hijo".
 */
public final class Family {
    private static final Random R = new Random();

    private Family() {
    }

    public static FamilyStore.Person person(Villager v) {
        Identity id = StarVillagers.identity(v);
        return FamilyStore.getOrCreate(v.getUUID(), id != null ? id.name() : v.getDisplayName().getString(), id != null && id.female(), false);
    }

    public static FamilyStore.Person person(ServerPlayer p) {
        return FamilyStore.getOrCreate(p.getUUID(), p.getName().getString(), false, true);
    }

    public static boolean partners(Villager v, ServerPlayer p) {
        FamilyStore.Person pv = person(v);
        return p.getUUID().equals(pv.partner) || p.getUUID().equals(pv.spouse);
    }

    public static boolean married(Villager v, ServerPlayer p) {
        return p.getUUID().equals(person(v).spouse);
    }

    private static boolean free(FamilyStore.Person x) {
        return x.partner == null && x.spouse == null;
    }

    /** Lo que es para vos (se muestra al lado de los corazones): "esposa", "novio", "hija"... */
    public static String role(Villager v, ServerPlayer p) {
        FamilyStore.Person pv = person(v);
        boolean f = pv.female;
        if (p.getUUID().equals(pv.spouse)) {
            return f ? "esposa" : "esposo";
        }
        if (p.getUUID().equals(pv.partner)) {
            return f ? "novia" : "novio";
        }
        if (pv.parents.contains(p.getUUID())) {
            return f ? "hija" : "hijo";
        }
        return "";
    }

    private static ItemStack ring(ServerPlayer p) {
        ItemStack st = p.getMainHandItem();
        return st.is(StarVillagers.RING) ? st : ItemStack.EMPTY;
    }

    private static long day(Entity e) {
        return e.level().getDayTime() / 24000L;
    }

    private static void say(Villager v, ServerPlayer p, String key, String anim) {
        String s = Social.personality(v).line(key, R).replace("%jugador%", p.getName().getString());
        if (!s.isEmpty()) {
            StarScenesAPI.say(v, s, 3.5f);
        }
        if (anim != null) {
            StarScenesAPI.play(v, anim);
        }
    }

    private static void hearts(Villager v, int n) {
        if (v.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, v.getX(), v.getY() + v.getBbHeight() + 0.3, v.getZ(), n, 0.4, 0.3, 0.4, 0.02);
        }
    }

    private static void act(ServerPlayer p, Villager v, String what) {
        FamilyStore.Person pv = person(v), pp = person(p);
        int h = Social.mind(v).hearts(p.getUUID());
        Mind m = Social.mind(v);
        switch (what.trim()) {
            case "noviazgo" -> {
                StarScenesAPI.run(p, v, List.of("cerrar"));
                if (partners(v, p)) {
                    return;
                }
                if (v.isBaby() || h < 80 || m.mood < -20 || !free(pv) || !free(pp)) {
                    say(v, p, !free(pv) ? "familia_ocupado" : !free(pp) ? "familia_vos_ocupado" : "familia_no", "shrug");
                    return;
                }
                pv.partner = p.getUUID();
                pp.partner = v.getUUID();
                FamilyStore.changed();
                say(v, p, "familia_si_novios", "happy");
                hearts(v, 8);
            }
            case "casamiento" -> {
                StarScenesAPI.run(p, v, List.of("cerrar"));
                if (!p.getUUID().equals(pv.partner)) {
                    return;
                }
                ItemStack st = ring(p);
                if (st.isEmpty()) {
                    say(v, p, "familia_sin_anillo", "think");
                    return;
                }
                if (h < 95) {
                    say(v, p, "familia_todavia_no", "shy");
                    return;
                }
                if (!p.getAbilities().instabuild) {
                    st.shrink(1);
                }
                pv.partner = pp.partner = null;
                pv.spouse = p.getUUID();
                pp.spouse = v.getUUID();
                FamilyStore.changed();
                say(v, p, "familia_si_casamiento", "happy");
                hearts(v, 16);
                StarScenesAPI.playerGesture(p, "hug", 2.6f);
                if (p.level().getServer() != null) {
                    p.level().getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§d♥ " + p.getName().getString() + " y " + pv.name + " se casaron. ¡Felicidades! ♥"), false);
                }
            }
            case "hijo" -> {
                StarScenesAPI.run(p, v, List.of("cerrar"));
                if (!canHaveChild(v, p)) {
                    say(v, p, "familia_hijo_no", "shrug");
                    return;
                }
                Villager baby = baby(v, null, p.getUUID());
                if (baby != null) {
                    say(v, p, "familia_hijo_si", "happy");
                    hearts(v, 10);
                }
            }
            case "terminar" -> {
                StarScenesAPI.run(p, v, List.of("cerrar"));
                if (!partners(v, p)) {
                    return;
                }
                pv.partner = pv.spouse = null;
                pp.partner = pp.spouse = null;
                FamilyStore.changed();
                m.addHearts(p.getUUID(), -40);
                m.addMood(-50);
                ((AttachmentTarget) (Object) v).setAttached(Social.MIND, m);
                say(v, p, "familia_terminar", "sad");
            }
            case "arbol" -> {
                StarScenesAPI.run(p, v, List.of("cerrar"));
                send(p, v.getUUID());
            }
            default -> StarVillagers.LOGGER.warn("StarVillagers: no conozco 'familia:{}'", what);
        }
    }

    public static boolean canHaveChild(Villager v, ServerPlayer p) {
        FamilyStore.Person pv = person(v);
        return married(v, p) && !v.isBaby() && Social.mind(v).hearts(p.getUUID()) >= 90 && day(v) - pv.lastChildDay >= 3;
    }

    /** Nace un bebe al lado de la madre/padre aldeano. other es el otro padre si es aldeano; player si es un jugador. */
    public static Villager baby(Villager v, Villager other, UUID player) {
        if (!(v.level() instanceof ServerLevel level)) {
            return null;
        }
        Villager baby = EntityType.VILLAGER.create(level, EntitySpawnReason.BREEDING);
        if (baby == null) {
            return null;
        }
        baby.setAge(-24000);
        baby.setVillagerData(baby.getVillagerData().withType(v.getVillagerData().type()));
        baby.snapTo(v.getX() + (R.nextFloat() - 0.5) * 0.8, v.getY(), v.getZ() + (R.nextFloat() - 0.5) * 0.8, R.nextFloat() * 360, 0);
        born(baby, v, other, player);
        level.addFreshEntityWithPassengers(baby);
        return baby;
    }

    /** Le da al bebe su identidad (genes de los padres) y lo anota en el arbol. */
    public static void born(Villager baby, Villager a, Villager b, UUID player) {
        Identity ia = StarVillagers.identity(a), ib = b == null ? null : StarVillagers.identity(b);
        Identity id = Looks.child(R, ia, ib);
        ((AttachmentTarget) (Object) baby).setAttached(StarVillagers.IDENTITY, id);
        baby.setCustomName(Component.literal(id.name()));
        FamilyStore.Person pc = FamilyStore.getOrCreate(baby.getUUID(), id.name(), id.female(), false);
        FamilyStore.Person pa = person(a);
        pc.parents.add(a.getUUID());
        pa.children.add(baby.getUUID());
        pa.lastChildDay = day(a);
        if (b != null) {
            FamilyStore.Person pb = person(b);
            pc.parents.add(b.getUUID());
            pb.children.add(baby.getUUID());
            // si no tenian pareja, quedan casados
            if (free(pa) && free(pb)) {
                pa.spouse = b.getUUID();
                pb.spouse = a.getUUID();
            }
        } else if (player != null) {
            FamilyStore.Person pp = FamilyStore.get(player);
            pc.parents.add(player);
            if (pp != null) {
                pp.children.add(baby.getUUID());
            }
            // el bebe quiere a su mama/papa jugador
            Mind m = Social.mind(baby);
            m.addHearts(player, 60);
            ((AttachmentTarget) (Object) baby).setAttached(Social.MIND, m);
        }
        FamilyStore.changed();
    }

    // ---- arbol ----

    private static void add(List<FamilyNet.Node> out, Set<UUID> seen, UUID id, String role) {
        if (id == null || !seen.add(id)) {
            return;
        }
        FamilyStore.Person p = FamilyStore.get(id);
        if (p == null) {
            return;
        }
        out.add(new FamilyNet.Node(id.toString(), p.name, p.female, p.player, p.dead, role));
    }

    public static List<FamilyNet.Node> tree(UUID center) {
        List<FamilyNet.Node> out = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        FamilyStore.Person c = FamilyStore.get(center);
        add(out, seen, center, "yo");
        if (c == null) {
            return out;
        }
        for (UUID par : c.parents) {
            add(out, seen, par, "padre");
            FamilyStore.Person pp = FamilyStore.get(par);
            if (pp != null) {
                for (UUID gp : pp.parents) {
                    add(out, seen, gp, "abuelo");
                }
                for (UUID sib : pp.children) {
                    add(out, seen, sib, "hermano");
                }
            }
        }
        add(out, seen, c.spouse, "esposo");
        add(out, seen, c.partner, "pareja");
        for (UUID ch : c.children) {
            add(out, seen, ch, "hijo");
        }
        return out;
    }

    public static void send(ServerPlayer p, UUID center) {
        if (FamilyStore.get(center) == null) {
            return;
        }
        ServerPlayNetworking.send(p, new FamilyNet.Tree(center.toString(), tree(center)));
    }

    public static void init() {
        FamilyNet.register();
        ServerLifecycleEvents.SERVER_STARTED.register(FamilyStore::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> FamilyStore.stop());
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 600 == 0) {
                FamilyStore.save();
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            if (e instanceof Villager v) {
                FamilyStore.Person p = FamilyStore.get(v.getUUID());
                if (p != null) {
                    p.dead = true;
                    FamilyStore.changed();
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(FamilyNet.Ask.TYPE, (msg, ctx) -> {
            try {
                send(ctx.player(), UUID.fromString(msg.id()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        StarScenesAPI.registerAction("familia", (p, s, val) -> {
            if (s instanceof Villager v) {
                act(p, v, val);
            }
        });
        StarScenesAPI.registerCondition("pareja", (p, s, val) -> s instanceof Villager v && partners(v, p));
        StarScenesAPI.registerCondition("novios", (p, s, val) -> s instanceof Villager v && p.getUUID().equals(person(v).partner));
        StarScenesAPI.registerCondition("casados", (p, s, val) -> s instanceof Villager v && married(v, p));
        StarScenesAPI.registerCondition("libre", (p, s, val) -> s instanceof Villager v && free(person(v)) && free(person(p)));
        StarScenesAPI.registerCondition("tiene_anillo", (p, s, val) -> !ring(p).isEmpty());
        StarScenesAPI.registerCondition("puede_tener_hijo", (p, s, val) -> s instanceof Villager v && canHaveChild(v, p));
        StarScenesAPI.registerCondition("tiene_familia", (p, s, val) -> s instanceof Villager v && tree(v.getUUID()).size() > 1);
        // /familia: tu arbol
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("familia").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            person(p);
            send(p, p.getUUID());
            return 1;
        })));
    }
}
