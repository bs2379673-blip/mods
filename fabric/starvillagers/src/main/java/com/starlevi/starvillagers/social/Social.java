package com.starlevi.starvillagers.social;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Personalidad, humor, amistad, regalos y memoria de los aldeanos. Todo se usa desde los dialogos
 * con marcadores (%saludo%, %charla%, %humor%, %relacion%, %reaccion%, %personalidad%), acciones
 * ("charlar", "regalar") y condiciones ("amistad:>=40", "personalidad:timido", "humor:<0", "en_mano").
 */
public final class Social {
    public static final AttachmentType<Mind> MIND = AttachmentRegistry.create(StarVillagers.id("mente"), b -> b.persistent(Mind.CODEC));
    private static final Random R = new Random();
    /** La reaccion al ultimo regalo de cada jugador (para %reaccion%). */
    private static final Map<UUID, String> REACTION = new HashMap<>();

    private Social() {
    }

    public static Mind mind(Villager v) {
        AttachmentTarget t = (AttachmentTarget) (Object) v;
        Mind m = t.getAttached(MIND);
        if (m == null) {
            String p = Personality.random(R);
            m = new Mind(p, Personality.get(p).baseMood, Map.of());
            t.setAttached(MIND, m);
        }
        return m;
    }

    private static void touch(Villager v, Mind m) {
        ((AttachmentTarget) (Object) v).setAttached(MIND, m);
    }

    public static Personality personality(Villager v) {
        return Personality.get(mind(v).personality);
    }

    public static long day(Entity e) {
        return e.level().getDayTime() / 24000L;
    }

    private static String fill(String s, Villager v, ServerPlayer p) {
        Identity id = StarVillagers.identity(v);
        return s.replace("%jugador%", p.getName().getString()).replace("%npc%", id != null ? id.name() : v.getDisplayName().getString());
    }

    /** Los corazones que se ven al lado del nombre. */
    public static String hearts(int h) {
        if (h < 0) {
            return "&4" + "✘".repeat(Math.min(3, 1 + (-h) / 34));
        }
        int full = Math.min(5, h / 20);
        return "&c" + "❤".repeat(full) + "&8" + "❤".repeat(5 - full);
    }

    public static String relation(int h) {
        return h < 0 ? "relacion_enemigo" : h < 20 ? "relacion_desconocido" : h < 40 ? "relacion_conocido" : h < 60 ? "relacion_amigo"
                : h < 80 ? "relacion_buen_amigo" : "relacion_mejor_amigo";
    }

    public static String moodKey(int m) {
        return m >= 50 ? "humor_feliz" : m >= 15 ? "humor_bien" : m > -15 ? "humor_normal" : m > -50 ? "humor_triste" : "humor_enojado";
    }

    /** Como te saluda (en el dialogo o en el globito). Tambien recuerda que te vio hoy. */
    public static String greeting(Villager v, ServerPlayer p) {
        Mind m = mind(v);
        Personality pe = Personality.get(m.personality);
        Mind.Bond b = m.bond(p.getUUID());
        long today = day(v);
        String key;
        if (com.starlevi.starvillagers.family.Family.partners(v, p) && b.hearts >= 0) {
            key = "saludo_pareja";
        } else if (b.hearts < 0) {
            key = "saludo_enojado";
        } else if (b.lastSeen >= 0 && today - b.lastSeen >= 5 && b.hearts >= 20) {
            key = "saludo_reencuentro";
        } else if (b.hearts >= 40) {
            key = "saludo_amigo";
        } else {
            long t = v.level().getDayTime() % 24000;
            key = R.nextInt(3) == 0 ? (t < 6000 || t >= 23000 ? "saludo_manana" : t < 12000 ? "saludo_tarde" : "saludo_noche") : "saludo";
        }
        b.lastSeen = today;
        touch(v, m);
        String s = pe.line(key, R);
        if (s.isEmpty()) {
            s = pe.line("saludo", R);
        }
        return fill(s, v, p);
    }

    public static boolean angryAt(Villager v, ServerPlayer p) {
        return mind(v).hearts(p.getUUID()) < 0;
    }

    private static void particles(Villager v, ParticleOptions type, int n) {
        if (v.level() instanceof ServerLevel sl) {
            sl.sendParticles(type, v.getX(), v.getY() + v.getBbHeight() + 0.3, v.getZ(), n, 0.3, 0.2, 0.3, 0.02);
        }
    }

    private static String chat(Villager v, ServerPlayer p) {
        Mind m = mind(v);
        Personality pe = Personality.get(m.personality);
        Mind.Bond b = m.bond(p.getUUID());
        if (b.hearts < 0) {
            return fill(pe.line("charla_enojado", R), v, p);
        }
        // de que habla: se arma una bolsa de temas posibles y se elige uno
        java.util.List<String> bag = new java.util.ArrayList<>();
        java.util.function.Consumer<String> add = k -> {
            String x = pe.line(k, R);
            if (!x.isEmpty()) {
                bag.add(x);
            }
        };
        add.accept("charla");
        add.accept("charla");
        add.accept("charla_general");
        add.accept("charla_general");
        add.accept("charla_oficio_" + StarVillagers.profession(v));
        add.accept("charla_oficio_" + StarVillagers.profession(v));
        long t = v.level().getDayTime() % 24000;
        add.accept(t < 6000 ? "charla_manana" : t < 12000 ? "charla_tarde" : "charla_noche");
        if (v.level().isRaining()) {
            add.accept("charla_lluvia");
            add.accept("charla_lluvia");
        }
        add.accept(moodKey(m.mood).replace("humor_", "charla_humor_"));
        if (!b.lastGift.isEmpty()) {
            String x = pe.line("recuerdo_regalo", R).replace("%regalo%", b.lastGift);
            if (!x.isEmpty()) {
                bag.add(x);
            }
        }
        if (b.hits > 0 && b.hearts < 30) {
            add.accept("recuerdo_golpe");
        }
        if (b.hearts >= 60) {
            add.accept("charla_amigo");
            add.accept("charla_amigo");
        }
        if (com.starlevi.starvillagers.family.Family.partners(v, p)) {
            add.accept("charla_pareja");
            add.accept("charla_pareja");
        }
        // chismes: de otro aldeano de cerca
        java.util.List<Villager> near = v.level().getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(24), o -> o != v && StarVillagers.identity(o) != null);
        if (!near.isEmpty()) {
            String x = pe.line("chisme", R);
            if (!x.isEmpty()) {
                bag.add(x.replace("%otro%", StarVillagers.identity(near.get(R.nextInt(near.size()))).name()));
            }
        }
        String pick = bag.isEmpty() ? "..." : bag.get(R.nextInt(bag.size()));
        return fill(pick, v, p);
    }

    /** Lo que le gusta (para "¿Qué te gusta?"). */
    private static String likes(Villager v, ServerPlayer p) {
        Personality pe = personality(v);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (var spec : pe.likes) {
            names.add(spec.display().getHoverName().getString());
        }
        for (var spec : Personality.PROFESSION_LIKES.getOrDefault(StarVillagers.profession(v), java.util.List.of())) {
            names.add(spec.display().getHoverName().getString());
        }
        java.util.Collections.shuffle(names, R);
        String l = names.isEmpty() ? "las cosas lindas" : String.join(", ", names.subList(0, Math.min(3, names.size())));
        return fill(pe.line("gustos", R), v, p).replace("%gustos%", l);
    }

    /** En que anda la amistad y que se desbloquea despues. */
    private static String progress(Villager v, ServerPlayer p) {
        int h = mind(v).hearts(p.getUUID());
        int[] at = {20, 30, 40, 60, 70, 80, 95};
        String[] what = {"chocar los cinco", "acariciarle la cabeza y sentarse juntos", "darse un abrazo", "tomarse de la mano, cargarlo y ser novios",
                "que te lleve a upa", "un beso y ser novios", "casarse (con el anillo)"};
        for (int i = 0; i < at.length; i++) {
            if (h < at[i]) {
                return "&7Amistad &f" + Math.max(0, h) + "&7/100 · a los &f" + at[i] + "&7: " + what[i]
                        + ". &8(Sube charlando una vez por día, con regalos que le gusten y haciendo cosas juntos.)";
            }
        }
        return "&7Amistad &f" + h + "&7/100 · ¡ya desbloqueaste todo!";
    }

    private static String giftName;

    private static void giveGift(ServerPlayer p, Villager v) {
        Mind m = mind(v);
        Personality pe = Personality.get(m.personality);
        Mind.Bond b = m.bond(p.getUUID());
        ItemStack st = p.getMainHandItem();
        String key;
        if (st.isEmpty()) {
            key = "regalo_nada";
        } else {
            long today = day(v);
            if (b.lastGiftDay != today) {
                b.lastGiftDay = today;
                b.giftsToday = 0;
            }
            String name = st.getHoverName().getString();
            giftName = name;
            if (b.giftsToday >= 3) {
                key = "regalo_demasiado";
            } else {
                int taste = pe.taste(st, StarVillagers.profession(v));
                b.giftsToday++;
                int pts = switch (taste) {
                    case 2 -> 12;
                    case 1 -> 6;
                    case 0 -> 3;
                    default -> -6;
                };
                if (pts > 0) {
                    pts = Math.max(1, pts * (4 - b.giftsToday) / 3);
                }
                m.addHearts(p.getUUID(), pts);
                m.addMood(taste * 10 + 4);
                if (!p.getAbilities().instabuild) {
                    st.shrink(1);
                }
                if (taste >= 1) {
                    b.lastGift = name;
                }
                key = taste == 2 ? "regalo_encanta" : taste == 1 ? "regalo_gusta" : taste == 0 ? "regalo_normal" : "regalo_no_gusta";
                particles(v, taste == 2 ? ParticleTypes.HEART : taste >= 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER, taste == 2 ? 5 : 6);
            }
        }
        touch(v, m);
        StarScenesAPI.play(v, anim(pe, key.replace("regalo_", "")));
        String item = st.isEmpty() && giftName == null ? "" : giftName != null ? giftName : st.getHoverName().getString();
        giftName = null;
        REACTION.put(p.getUUID(), fill(pe.line(key, R), v, p).replace("%item%", item));
    }

    /** El gesto de esta personalidad para algo ("saludo", "encanta", "humor_feliz"...). */
    public static String anim(Personality pe, String what) {
        String a = pe.line("animacion_" + what, R);
        return a.isEmpty() ? "talk" : a;
    }

    /** Con que gesto te saluda (enojado si le caes mal). */
    public static String greetAnim(Villager v, ServerPlayer p) {
        Personality pe = personality(v);
        return anim(pe, angryAt(v, p) ? "enojado" : "saludo");
    }

    private static int compare(String expr, int have) {
        // devuelve 1 si se cumple
        String e = expr.replace(" ", "");
        String[] ops = {">=", "<=", "!=", ">", "<", "="};
        for (String op : ops) {
            if (e.startsWith(op)) {
                int n;
                try {
                    n = Integer.parseInt(e.substring(op.length()));
                } catch (NumberFormatException x) {
                    return 0;
                }
                boolean ok = switch (op) {
                    case ">=" -> have >= n;
                    case "<=" -> have <= n;
                    case "!=" -> have != n;
                    case ">" -> have > n;
                    case "<" -> have < n;
                    default -> have == n;
                };
                return ok ? 1 : 0;
            }
        }
        try {
            return have >= Integer.parseInt(e) ? 1 : 0;
        } catch (NumberFormatException x) {
            return 0;
        }
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> Personality.load());
        // marcadores para los dialogos
        StarScenesAPI.registerPlaceholder("titulo", (p, s) -> {
            if (!(s instanceof Villager v) || StarVillagers.identity(v) == null) {
                return "";
            }
            String role = com.starlevi.starvillagers.family.Family.role(v, p);
            return hearts(mind(v).hearts(p.getUUID())) + (role.isEmpty() ? "" : " &d(" + role + ")");
        });
        StarScenesAPI.registerPlaceholder("saludo", (p, s) -> s instanceof Villager v ? greeting(v, p) : "");
        StarScenesAPI.registerPlaceholder("charla", (p, s) -> s instanceof Villager v ? chat(v, p) : "");
        StarScenesAPI.registerPlaceholder("humor", (p, s) -> s instanceof Villager v ? fill(personality(v).line(moodKey(mind(v).mood), R), v, p) : "");
        StarScenesAPI.registerPlaceholder("relacion", (p, s) -> s instanceof Villager v ? fill(personality(v).line(relation(mind(v).hearts(p.getUUID())), R), v, p) : "");
        StarScenesAPI.registerPlaceholder("gustos", (p, s) -> s instanceof Villager v ? likes(v, p) : "");
        StarScenesAPI.registerPlaceholder("progreso", (p, s) -> s instanceof Villager v ? progress(v, p) : "");
        StarScenesAPI.registerPlaceholder("reaccion", (p, s) -> REACTION.getOrDefault(p.getUUID(), ""));
        StarScenesAPI.registerPlaceholder("anim_saludo", (p, s) -> s instanceof Villager v ? greetAnim(v, p) : "");
        StarScenesAPI.registerPlaceholder("anim_charla", (p, s) -> s instanceof Villager v ? anim(personality(v), angryAt(v, p) ? "enojado" : "charla") : "");
        StarScenesAPI.registerPlaceholder("anim_humor", (p, s) -> s instanceof Villager v ? anim(personality(v), moodKey(mind(v).mood)) : "");
        StarScenesAPI.registerPlaceholder("anim_relacion", (p, s) -> s instanceof Villager v ? anim(personality(v), relation(mind(v).hearts(p.getUUID()))) : "");
        StarScenesAPI.registerPlaceholder("personalidad", (p, s) -> s instanceof Villager v ? personality(v).name : "");
        Requests.init(REACTION);
        // acciones
        StarScenesAPI.registerAction("charlar", (p, s, val) -> {
            if (s instanceof Villager v) {
                Mind m = mind(v);
                Mind.Bond b = m.bond(p.getUUID());
                long today = day(v);
                if (b.lastChat != today && b.hearts >= 0) {
                    b.lastChat = today;
                    m.addHearts(p.getUUID(), 3);
                    m.addMood(5);
                    touch(v, m);
                } else if (b.hearts < 0 && b.lastChat != today) {
                    // charlar tambien ayuda a hacer las paces, de a poquito
                    b.lastChat = today;
                    m.addHearts(p.getUUID(), 2);
                    touch(v, m);
                }
            }
        });
        StarScenesAPI.registerAction("regalar", (p, s, val) -> {
            if (s instanceof Villager v) {
                giveGift(p, v);
            }
        });
        StarScenesAPI.registerAction("amistad", (p, s, val) -> {
            if (s instanceof Villager v) {
                Mind m = mind(v);
                m.addHearts(p.getUUID(), Integer.parseInt(val.trim()));
                touch(v, m);
            }
        });
        StarScenesAPI.registerAction("humor", (p, s, val) -> {
            if (s instanceof Villager v) {
                Mind m = mind(v);
                m.addMood(Integer.parseInt(val.trim()));
                touch(v, m);
            }
        });
        // condiciones
        StarScenesAPI.registerCondition("amistad", (p, s, val) -> s instanceof Villager v && compare(val, mind(v).hearts(p.getUUID())) == 1);
        StarScenesAPI.registerCondition("humor", (p, s, val) -> s instanceof Villager v && compare(val, mind(v).mood) == 1);
        StarScenesAPI.registerCondition("personalidad", (p, s, val) -> s instanceof Villager v && mind(v).personality.equalsIgnoreCase(val.trim()));
        StarScenesAPI.registerCondition("en_mano", (p, s, val) -> !p.getMainHandItem().isEmpty());
        // si le pegas, se acuerda
        ServerLivingEntityEvents.AFTER_DAMAGE.register((e, source, base, taken, blocked) -> {
            if (e instanceof Villager v && source.getEntity() instanceof ServerPlayer p && StarVillagers.identity(v) != null && taken > 0) {
                Mind m = mind(v);
                m.addHearts(p.getUUID(), -15);
                m.bond(p.getUUID()).hits++;
                m.addMood(-30);
                touch(v, m);
                if (v.isAlive()) {
                    StarScenesAPI.say(v, fill(Personality.get(m.personality).line("golpe", R), v, p), 2.5f);
                    particles(v, ParticleTypes.ANGRY_VILLAGER, 4);
                    StarScenesAPI.play(v, anim(Personality.get(m.personality), "golpe"));
                }
            }
        });
    }

    /** Cada minuto el humor vuelve de a poco a lo normal (y el clima y la noche lo bajan). */
    public static void moodTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long t = level.getDayTime() % 24000;
            int weather = level.isThundering() ? -25 : level.isRaining() ? -12 : 0;
            int night = t >= 13000 && t < 23000 ? -5 : 0;
            for (Villager v : level.getEntities(EntityTypeTest.forClass(Villager.class), x -> StarVillagers.identity(x) != null)) {
                Mind m = mind(v);
                int target = Personality.get(m.personality).baseMood + weather + night;
                int d = target - m.mood;
                if (d != 0) {
                    m.mood += Integer.signum(d) * Math.min(5, Math.abs(d));
                    touch(v, m);
                }
            }
        }
    }
}
