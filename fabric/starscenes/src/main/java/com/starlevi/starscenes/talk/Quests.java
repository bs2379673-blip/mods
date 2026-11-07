package com.starlevi.starscenes.talk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Las misiones: config/starscenes/misiones/&lt;id&gt;.json. Objetivos: traer items, matar criaturas,
 * hablar con alguien, ir a un lugar o tener una marca. Las recompensas son acciones (las mismas de
 * los dialogos). El avance se ve en pantalla.
 */
public final class Quests {
    public record Objective(String type, ItemSpec item, String target, int count, double x, double y, double z, double radius, String text) {
    }

    public record Quest(String id, String name, String description, List<Objective> objectives, List<String> rewards, boolean repeatable,
                        boolean keepItems, boolean hidden) {
    }

    private static final Map<String, Quest> ALL = new LinkedHashMap<>();
    private static final Map<UUID, List<Component>> SENT = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();

    private Quests() {
    }

    public static List<String> ids() {
        return new ArrayList<>(ALL.keySet());
    }

    public static Quest get(String id) {
        return ALL.get(id);
    }

    public static String load(List<String> errors) {
        ALL.clear();
        for (Map.Entry<String, JsonObject> e : JsonFolder.read("misiones", errors).entrySet()) {
            try {
                ALL.put(e.getKey(), parse(e.getKey(), e.getValue()));
            } catch (Exception ex) {
                errors.add("misiones/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        SENT.clear();
        return ALL.size() + " misiones";
    }

    private static Quest parse(String id, JsonObject o) {
        List<Objective> objs = new ArrayList<>();
        if (o.has("objetivos")) {
            for (JsonElement el : o.getAsJsonArray("objetivos")) {
                JsonObject j = el.getAsJsonObject();
                String type = JsonFolder.str(j, "tipo", "traer").toLowerCase(Locale.ROOT);
                ItemSpec item = null;
                String target = null;
                int count = JsonFolder.num(j, "cantidad", 1);
                switch (type) {
                    case "traer" -> {
                        item = ItemSpec.parse(JsonFolder.str(j, "item", "") + " " + count);
                        if (item == null) {
                            throw new IllegalArgumentException("item desconocido: " + JsonFolder.str(j, "item", ""));
                        }
                    }
                    case "matar" -> target = JsonFolder.str(j, "entidad", "minecraft:zombie");
                    case "hablar" -> {
                        target = JsonFolder.str(j, "npc", JsonFolder.str(j, "dialogo", ""));
                        count = 1;
                    }
                    case "marca" -> {
                        target = JsonFolder.str(j, "marca", "");
                        count = 1;
                    }
                    case "ir" -> count = 1;
                    default -> throw new IllegalArgumentException("tipo de objetivo desconocido: " + type);
                }
                objs.add(new Objective(type, item, target, Math.max(1, count), JsonFolder.dbl(j, "x", 0), JsonFolder.dbl(j, "y", 0),
                        JsonFolder.dbl(j, "z", 0), JsonFolder.dbl(j, "radio", 4), JsonFolder.str(j, "texto", null)));
            }
        }
        return new Quest(id, JsonFolder.str(o, "nombre", id).replace('&', '§'), JsonFolder.str(o, "descripcion", "").replace('&', '§'), objs,
                JsonFolder.list(o, "recompensas"), JsonFolder.bool(o, "repetible", false), JsonFolder.bool(o, "no_quitar_items", false),
                JsonFolder.bool(o, "oculta", false));
    }

    /** "nueva", "activa", "lista" (todo hecho, falta entregar), "hecha" o "no existe". */
    public static String state(ServerPlayer p, String id) {
        Quest q = ALL.get(id);
        if (q == null) {
            return "no existe";
        }
        PlayerStore.Data d = PlayerStore.of(p);
        if (d.quests.containsKey(id)) {
            return ready(p, q) ? "lista" : "activa";
        }
        if (d.done.containsKey(id) && !q.repeatable()) {
            return "hecha";
        }
        return "nueva";
    }

    private static int progress(ServerPlayer p, Quest q, int i) {
        Objective o = q.objectives().get(i);
        int[] prog = PlayerStore.of(p).quests.get(q.id());
        return switch (o.type()) {
            case "traer" -> Math.min(o.count(), o.item().has(p));
            case "marca" -> PlayerStore.of(p).flags.contains(o.target()) ? 1 : 0;
            default -> prog == null || i >= prog.length ? 0 : Math.min(o.count(), prog[i]);
        };
    }

    private static boolean ready(ServerPlayer p, Quest q) {
        for (int i = 0; i < q.objectives().size(); i++) {
            if (progress(p, q, i) < q.objectives().get(i).count()) {
                return false;
            }
        }
        return true;
    }

    public static void start(ServerPlayer p, String id) {
        Quest q = ALL.get(id);
        if (q == null || !state(p, id).equals("nueva")) {
            return;
        }
        PlayerStore.of(p).quests.put(id, new int[q.objectives().size()]);
        PlayerStore.changed(p);
        p.displayClientMessage(Component.literal("§6✦ Nueva misión: §e" + q.name()), false);
        if (!q.description().isEmpty()) {
            p.displayClientMessage(Component.literal("§7" + q.description()), false);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 1f, 1f);
    }

    public static void deliver(ServerPlayer p, Entity speaker, String id) {
        Quest q = ALL.get(id);
        if (q == null || !state(p, id).equals("lista")) {
            return;
        }
        if (!q.keepItems()) {
            for (Objective o : q.objectives()) {
                if (o.type().equals("traer")) {
                    o.item().take(p);
                }
            }
        }
        PlayerStore.Data d = PlayerStore.of(p);
        d.quests.remove(id);
        d.done.merge(id, 1, Integer::sum);
        PlayerStore.changed(p);
        p.displayClientMessage(Component.literal("§a✔ Misión completada: §f" + q.name()), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        Actions.runAll(p, speaker, q.rewards());
    }

    public static void abandon(ServerPlayer p, String id) {
        if (PlayerStore.of(p).quests.remove(id) != null) {
            PlayerStore.changed(p);
        }
    }

    private static void add(ServerPlayer p, java.util.function.BiPredicate<Quest, Objective> match, int amount) {
        PlayerStore.Data d = PlayerStore.of(p);
        boolean any = false;
        for (Map.Entry<String, int[]> e : d.quests.entrySet()) {
            Quest q = ALL.get(e.getKey());
            if (q == null) {
                continue;
            }
            int[] prog = e.getValue();
            if (prog.length != q.objectives().size()) {
                prog = java.util.Arrays.copyOf(prog, q.objectives().size());
                e.setValue(prog);
            }
            for (int i = 0; i < prog.length; i++) {
                Objective o = q.objectives().get(i);
                if (prog[i] < o.count() && match.test(q, o)) {
                    prog[i] = Math.min(o.count(), prog[i] + amount);
                    any = true;
                }
            }
        }
        if (any) {
            PlayerStore.changed(p);
        }
    }

    /** Murio algo: si lo mato un jugador, cuenta para sus misiones de matar. */
    public static void killed(LivingEntity dead, DamageSource src) {
        if (!(src.getEntity() instanceof ServerPlayer p) || PlayerStore.of(p).quests.isEmpty()) {
            return;
        }
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        add(p, (q, o) -> o.type().equals("matar") && matchesEntity(o.target(), type, dead), 1);
    }

    private static boolean matchesEntity(String target, Identifier type, Entity e) {
        if (target.startsWith("#")) {
            Identifier tag = Identifier.tryParse(target.substring(1));
            return tag != null && e.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tag));
        }
        Identifier want = Identifier.tryParse(target);
        return want != null && want.equals(type);
    }

    /** Hablo con alguien: cuenta para "hablar" (por nombre del NPC o por id del dialogo). */
    public static void talked(ServerPlayer p, Entity speaker, String dialogue) {
        if (PlayerStore.of(p).quests.isEmpty()) {
            return;
        }
        String name = speaker == null ? "" : strip(speaker.getDisplayName().getString());
        add(p, (q, o) -> o.type().equals("hablar") && (o.target().equalsIgnoreCase(dialogue) || !name.isEmpty() && strip(o.target()).equalsIgnoreCase(name)), 1);
    }

    private static String strip(String s) {
        return s.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "").trim();
    }

    public static void dirty(ServerPlayer p) {
        DIRTY.add(p.getUUID());
    }

    /** Cada segundo: lugares a los que hay que ir y el cartel de misiones de cada jugador. */
    public static void tick(MinecraftServer server) {
        boolean second = server.getTickCount() % 20 == 0;
        if (!second && DIRTY.isEmpty()) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!second && !DIRTY.contains(p.getUUID())) {
                continue;
            }
            PlayerStore.Data d = PlayerStore.of(p);
            if (second && !d.quests.isEmpty()) {
                add(p, (q, o) -> o.type().equals("ir") && p.distanceToSqr(o.x(), o.y(), o.z()) <= o.radius() * o.radius(), 1);
            }
            List<Component> lines = hud(p, d);
            if (!lines.equals(SENT.get(p.getUUID()))) {
                SENT.put(p.getUUID(), lines);
                ServerPlayNetworking.send(p, new Net.QuestHud(lines));
            }
        }
        DIRTY.clear();
    }

    private static List<Component> hud(ServerPlayer p, PlayerStore.Data d) {
        List<Component> out = new ArrayList<>();
        for (String id : d.quests.keySet()) {
            Quest q = ALL.get(id);
            if (q == null || q.hidden()) {
                continue;
            }
            boolean ready = ready(p, q);
            out.add(Component.literal((ready ? "§a✔ " : "§6✦ ") + "§e" + q.name()));
            for (int i = 0; i < q.objectives().size(); i++) {
                Objective o = q.objectives().get(i);
                int have = progress(p, q, i);
                boolean ok = have >= o.count();
                MutableComponent line = Component.literal(ok ? " §a☑ §7" : " §f☐ §f");
                line.append(describe(o));
                if (o.count() > 1) {
                    line.append(Component.literal((ok ? " §7" : " §e") + have + "/" + o.count()));
                }
                out.add(line);
            }
            if (ready) {
                out.add(Component.literal(" §a» Volvé para entregarla"));
            }
        }
        return out;
    }

    private static Component describe(Objective o) {
        if (o.text() != null) {
            return Component.literal(o.text().replace('&', '§'));
        }
        return switch (o.type()) {
            case "traer" -> Component.literal("Traer ").append(o.item().display().getHoverName());
            case "matar" -> {
                Identifier id = Identifier.tryParse(o.target().replace("#", ""));
                EntityType<?> t = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                yield Component.literal("Matar ").append(t == null ? Component.literal(o.target()) : t.getDescription());
            }
            case "hablar" -> Component.literal("Hablar con " + o.target().replace('&', '§'));
            case "ir" -> Component.literal(String.format(Locale.ROOT, "Ir a %.0f %.0f %.0f", o.x(), o.y(), o.z()));
            default -> Component.literal(o.target());
        };
    }

    public static void left(ServerPlayer p) {
        SENT.remove(p.getUUID());
        DIRTY.remove(p.getUUID());
    }
}
