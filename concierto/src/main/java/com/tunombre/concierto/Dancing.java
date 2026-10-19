package com.tunombre.concierto;

import com.tunombre.dancers.DancerEntity;
import com.tunombre.dancers.Dancers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Marcas y aparicion de las bailarinas, todo dentro del mod (sin datapack, sin marcadores, sin el mod dancers aparte).
 * Las marcas se guardan en el mundo (una por escala: 1, 4 o 10) con su dimension y hacia donde miran.
 */
public final class Dancing {
    private Dancing() { }

    public record Mark(ResourceKey<Level> dim, double x, double y, double z, float yaw) { }

    /** marcas guardadas en el mundo principal (dimension incluida) */
    public static final class Data extends SavedData {
        public final TreeMap<Integer, Mark> marks = new TreeMap<>();

        static Data load(CompoundTag t) {
            Data d = new Data();
            ListTag l = t.getList("m", 10);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                d.marks.put(c.getInt("s"), new Mark(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("d"))),
                        c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), c.getFloat("yaw")));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            marks.forEach((s, m) -> {
                CompoundTag c = new CompoundTag();
                c.putInt("s", s); c.putString("d", m.dim().location().toString());
                c.putDouble("x", m.x()); c.putDouble("y", m.y()); c.putDouble("z", m.z()); c.putFloat("yaw", m.yaw());
                l.add(c);
            });
            t.put("m", l);
            return t;
        }
    }

    public static Data data(MinecraftServer srv) {
        return srv.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "concierto_marcas");
    }

    /** quien: modelo, separacion lateral (en escalas), animacion de entrada, color del contorno */
    private record Look(String model, double side, String anim, ChatFormatting color) { }

    private static Look look(String who) {
        return switch (who.toLowerCase(Locale.ROOT)) {
            case "miku" -> new Look("mikupd", 0, "mikushow:entrada", ChatFormatting.AQUA);
            case "teto" -> new Look("teto", 1.1, "mikushow:aparicion_teto", ChatFormatting.RED);
            case "neru" -> new Look("neru", -1.1, "mikushow:aparicion_neru", ChatFormatting.YELLOW);
            default -> null;
        };
    }

    public static boolean known(String who) { return look(who) != null; }

    // ---------- tareas diferidas (reloj del servidor) ----------
    private record Task(long due, Runnable run) { }
    private static final List<Task> tasks = new ArrayList<>();

    private static void later(float secs, Runnable r) { tasks.add(new Task(System.currentTimeMillis() + (long) (secs * 1000F), r)); }

    /** lo llama Show en cada tick del servidor */
    public static void tick() {
        if (tasks.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var it = tasks.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.due() <= now) { it.remove(); try { t.run().run(); } catch (Exception ignored) { } }
        }
    }

    // ---------- marcas ----------
    public static String setMark(ServerPlayer p, int scale) {
        Data d = data(p.server);
        d.marks.put(scale, new Mark(p.level().dimension(), p.getX(), p.getY(), p.getZ(), p.getYRot()));
        d.setDirty();
        ((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 50, 0.2, 0.8, 0.2, 0.01);
        return "Marca guardada (escala " + scale + ") en " + p.level().dimension().location() + ". Las bailarinas solo aparecen cuando la estrella toca el escenario. Para probar: /concierto entrada miku";
    }

    public static int clearMarks(MinecraftServer srv) {
        Data d = data(srv);
        int n = d.marks.size();
        d.marks.clear(); d.setDirty();
        return n;
    }

    public static String listMarks(MinecraftServer srv) {
        Data d = data(srv);
        if (d.marks.isEmpty()) return "No hay marcas. Parate donde bailan y usa /concierto marcar";
        StringBuilder sb = new StringBuilder("Marcas:");
        d.marks.forEach((s, m) -> sb.append(String.format(Locale.ROOT, " [escala %d: %.0f %.0f %.0f en %s]", s, m.x(), m.y(), m.z(), m.dim().location())));
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            d.marks.forEach((s, m) -> {
                ServerLevel lv = srv.getLevel(m.dim());
                if (lv != null) lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, m.x(), m.y() + 1, m.z(), 60, 0.2, 1, 0.2, 0.01);
            });
            break;
        }
        return sb.toString();
    }

    /** marca principal para el escenario: la de escala 4 o, si no hay, la primera */
    public static Mark stageMark(MinecraftServer srv) {
        Data d = data(srv);
        Mark m = d.marks.get(4);
        return m != null ? m : (d.marks.isEmpty() ? null : d.marks.firstEntry().getValue());
    }

    // ---------- aparicion ----------
    private static boolean bailesOn(MinecraftServer srv) {
        var sb = srv.getScoreboard();
        var obj = sb.getObjective("mikushow");
        if (obj == null || !sb.hasPlayerScore("#baile", obj)) return true;
        return sb.getOrCreatePlayerScore("#baile", obj).getScore() == 1;
    }

    private static void warn(MinecraftServer srv, String m) {
        Component c = Component.literal("[Concierto] " + m).withStyle(ChatFormatting.RED);
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) { p.sendSystemMessage(c); p.sendSystemMessage(c, true); }
    }

    /**
     * Hace aparecer a la bailarina en todas las marcas (con la animacion de aparicion del mod) y la deja en reposo a los 5 s.
     * Si falla, avisa en chat y sobre la barra de objetos con el motivo exacto.
     * @param fallback marca del escenario, por si el usuario puso el escenario a mano y nunca uso /concierto marcar
     */
    public static int appear(MinecraftServer srv, String who, Mark fallback) {
        Look lk = look(who);
        if (lk == null) { warn(srv, "quien debe ser miku, teto o neru"); return 0; }
        if (!bailesOn(srv)) { warn(srv, "los bailes estan APAGADOS: usa /function mikushow:config/bailes_on"); return 0; }
        Data d = data(srv);
        TreeMap<Integer, Mark> marks = new TreeMap<>(d.marks);
        if (marks.isEmpty() && fallback != null) marks.put(4, fallback);
        if (marks.isEmpty()) { warn(srv, "NO HAY MARCA: parate donde bailan y usa /concierto marcar (o /concierto escenario aqui 4)"); return 0; }

        String tag = "mks_" + who.toLowerCase(Locale.ROOT);
        // las anteriores se desvanecen
        for (ServerLevel lv : srv.getAllLevels())
            for (Entity en : lv.getAllEntities())
                if (en instanceof DancerEntity de && de.getTags().contains(tag)) de.vanish();

        var sb = srv.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(tag);
        if (team == null) team = sb.addPlayerTeam(tag);
        team.setColor(lk.color());

        List<UUID> ids = new ArrayList<>();
        String err = null;
        // siempre a escala 10, en la marca de escala 10 (o, si no hay, en la primera que exista)
        final int scale = 10;
        Mark m = marks.containsKey(10) ? marks.get(10) : marks.containsKey(4) ? marks.get(4) : marks.firstEntry().getValue();
        ServerLevel lv = srv.getLevel(m.dim());
        if (lv == null) err = "la dimension de la marca " + m.dim().location() + " no existe";
        else {
            DancerEntity de = Dancers.DANCER.get().create(lv);
            if (de == null) err = "no se pudo crear la entidad dancers:dancer";
            else {
                double yr = Math.toRadians(m.yaw());
                double off = lk.side() * scale;
                double px = m.x() + Math.cos(yr) * off, pz = m.z() + Math.sin(yr) * off;
                de.moveTo(px, m.y(), pz, m.yaw(), 0F);
                de.setYBodyRot(m.yaw()); de.setYHeadRot(m.yaw());
                de.setModelName(lk.model());
                de.setDancerScale(scale);
                de.setTravel(1F);
                de.appear();
                de.addTag(tag); de.addTag("mikushow");
                de.play(lk.anim(), false);
                de.setGlowingTag(true);
                lv.addFreshEntity(de);
                sb.addPlayerToTeam(de.getStringUUID(), team);
                ids.add(de.getUUID());
            }
        }
        if (ids.isEmpty()) { warn(srv, who + " NO aparecio: " + (err == null ? "sin marcas validas" : err)); return 0; }

        // a los 5 s queda bailando en reposo; a los 7 s se apaga el contorno
        later(5F, () -> forEach(srv, ids, de -> { if (lk.anim().equals(de.getAnim())) de.play("mikushow:idle", true); }));
        later(7F, () -> forEach(srv, ids, de -> de.setGlowingTag(false)));
        return ids.size();
    }

    private record Dance(String miku, String teto, String neru, boolean mic) { }

    private static Dance danceOf(String name) {
        return switch (name.toLowerCase(Locale.ROOT).replace("_", "")) {
            case "static" -> new Dance("static", null, null, false);
            case "kontonboogie" -> new Dance("konton_boogie", null, null, false);
            case "loopingtherooms" -> new Dance("looping_the_rooms", null, null, false);
            case "magicalcure" -> new Dance("magical_cure_love_shot", null, null, false);
            case "melt" -> new Dance("melt", null, null, true);
            case "mesmerizer" -> new Dance("mesmerizer", "mesmerizer", null, false);
            case "minnamikumiku" -> new Dance("minna_miku_miku", null, null, false);
            case "anamanaguchi" -> new Dance("anamanaguchi_miku", null, null, false);
            case "triplebaka" -> new Dance("triple_baka", "triple_baka", "triple_baka", false);
            default -> null;
        };
    }

    /** al empezar cada cancion cada bailarina arranca su baile (las que no bailan quedan en reposo); el modelo de Miku vuelve a mikupd salvo en Melt */
    public static void danceFor(MinecraftServer srv, String song) {
        Dance d = danceOf(song);
        if (d == null || !bailesOn(srv)) return;
        for (ServerLevel lv : srv.getAllLevels())
            for (Entity en : lv.getAllEntities()) {
                if (!(en instanceof DancerEntity de)) continue;
                String a, model = null;
                boolean loop = false;
                if (de.getTags().contains("mks_teto")) { a = d.teto(); }
                else if (de.getTags().contains("mks_neru")) { a = d.neru(); }
                else { a = d.miku(); model = d.mic() ? "mikupd_mic" : "mikupd"; }
                if (a == null) { a = "idle"; loop = true; }
                if (model != null) de.setModelName(model);
                de.setTravel(1F);
                de.play("mikushow:" + a, loop);
            }
    }

    private static void forEach(MinecraftServer srv, List<UUID> ids, java.util.function.Consumer<DancerEntity> f) {
        for (UUID id : ids)
            for (ServerLevel lv : srv.getAllLevels())
                if (lv.getEntity(id) instanceof DancerEntity de) { f.accept(de); break; }
    }
}
