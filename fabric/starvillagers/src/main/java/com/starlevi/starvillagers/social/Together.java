package com.starlevi.starvillagers.social;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Cosas que el jugador y un aldeano hacen juntos: chocar los cinco, abrazarse, darse un beso y
 * caminar de la mano. Los dos hacen el gesto (el aldeano con StarScenes, el jugador con los gestos
 * de jugador de StarScenes). Se piden desde la charla con la accion "juntos:chocar|abrazar|mano|besar".
 */
public final class Together {
    private record Kind(String id, int hearts, int gain, String anim, double dist, int ticks, ParticleOptions particle) {
    }

    private static final Map<String, Kind> KINDS = Map.of(
            "chocar", new Kind("chocar", 20, 2, "high_five", 0.95, 30, ParticleTypes.HAPPY_VILLAGER),
            "abrazar", new Kind("abrazar", 40, 4, "hug", 0.62, 54, ParticleTypes.HEART),
            "besar", new Kind("besar", 80, 5, "kiss", 0.55, 50, ParticleTypes.HEART),
            "mano", new Kind("mano", 60, 3, "hold_left", 0, 6000, ParticleTypes.HEART));

    private static final class Job {
        Villager v;
        UUID player;
        Kind kind;
        long end;
        boolean noAi;
        int refresh;
    }

    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final Random R = new Random();

    private Together() {
    }

    /** Si el aldeano esta haciendo algo con alguien (no saluda ni charla con otros). */
    public static boolean busy(Villager v) {
        return JOBS.containsKey(v.getUUID());
    }

    private static String line(Villager v, ServerPlayer p, String key) {
        Identity id = StarVillagers.identity(v);
        return Social.personality(v).line(key, R).replace("%jugador%", p.getName().getString()).replace("%npc%", id != null ? id.name() : "");
    }

    private static void particles(Villager v, ParticleOptions type, int n) {
        if (v.level() instanceof ServerLevel sl) {
            sl.sendParticles(type, v.getX(), v.getY() + v.getBbHeight() + 0.2, v.getZ(), n, 0.35, 0.2, 0.35, 0.02);
        }
    }

    /** Empieza (o explica por que no). */
    public static void start(ServerPlayer p, Villager v, String what) {
        Kind k = KINDS.get(what.trim().toLowerCase(java.util.Locale.ROOT));
        if (k == null) {
            StarVillagers.LOGGER.warn("StarVillagers: no conozco 'juntos:{}' (chocar, abrazar, mano, besar)", what);
            return;
        }
        StarScenesAPI.run(p, v, List.of("cerrar"));
        Mind m = Social.mind(v);
        int hearts = m.hearts(p.getUUID());
        if (hearts < k.hearts()) {
            StarScenesAPI.say(v, line(v, p, "juntos_no"), 3);
            StarScenesAPI.play(v, Social.anim(Social.personality(v), "juntos_no"));
            return;
        }
        if (m.mood < -40) {
            StarScenesAPI.say(v, line(v, p, "juntos_sin_ganas"), 3);
            StarScenesAPI.play(v, Social.anim(Social.personality(v), "juntos_sin_ganas"));
            return;
        }
        stop(v); // si ya estaba haciendo otra cosa
        for (Job j : JOBS.values()) {
            if (j.player.equals(p.getUUID())) {
                stop(j.v); // el jugador soltaba a otro
                break;
            }
        }
        Job j = new Job();
        j.v = v;
        j.player = p.getUUID();
        j.kind = k;
        j.end = v.level().getGameTime() + k.ticks();
        j.noAi = v.isNoAi();
        JOBS.put(v.getUUID(), j);
        v.getNavigation().stop();
        v.setNoAi(true);
        if (k.dist() > 0) {
            // se pone enfrente tuyo, mirandote
            Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
            Vec3 at = p.position().add(look.scale(k.dist()));
            float yaw = p.getYRot() + 180;
            v.snapTo(at.x, p.getY(), at.z, yaw, 0);
            v.setYBodyRot(yaw);
            v.setYHeadRot(yaw);
            StarScenesAPI.playerGesture(p, k.anim(), k.ticks() / 20f);
            StarScenesAPI.play(v, k.anim());
        } else {
            follow(j, p, true);
        }
        StarScenesAPI.say(v, line(v, p, "juntos_" + k.id()), 3);
        particles(v, k.particle(), k.particle() == ParticleTypes.HEART ? 4 : 6);
        // una vez por dia cada cosa suma amistad y alegra
        Mind.Bond b = m.bond(p.getUUID());
        long day = Social.day(v);
        if (b.togetherDay != day) {
            b.togetherDay = day;
            b.togetherDone = "";
        }
        if (!("," + b.togetherDone + ",").contains("," + k.id() + ",")) {
            b.togetherDone = b.togetherDone.isEmpty() ? k.id() : b.togetherDone + "," + k.id();
            m.addHearts(p.getUUID(), k.gain());
            m.addMood(8);
        }
        ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) (Object) v).setAttached(Social.MIND, m);
    }

    /** De la mano: el aldeano camina a tu derecha, te da la mano izquierda. */
    private static void follow(Job j, ServerPlayer p, boolean first) {
        Villager v = j.v;
        float yaw = p.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 want = p.position().add(right.scale(0.78)).add(fwd.scale(0.05));
        Vec3 cur = v.position();
        Vec3 d = want.subtract(cur);
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        Vec3 next = first || len > 3 ? want : len > 0.4 ? cur.add(d.x / len * 0.4, 0, d.z / len * 0.4) : want;
        v.snapTo(next.x, p.getY(), next.z, yaw, 0);
        v.setYBodyRot(yaw);
        v.setYHeadRot(yaw);
        if (first || --j.refresh <= 0) {
            j.refresh = 40;
            StarScenesAPI.play(v, "hold_left");
            StarScenesAPI.playerGesture(p, "hold_right", 3);
        }
    }

    public static void stop(Villager v) {
        Job j = JOBS.remove(v.getUUID());
        if (j == null) {
            return;
        }
        v.setNoAi(j.noAi);
        if (j.kind.id().equals("mano")) {
            StarScenesAPI.play(v, "idle");
            if (v.level().getServer() != null) {
                ServerPlayer p = v.level().getServer().getPlayerList().getPlayer(j.player);
                if (p != null) {
                    StarScenesAPI.playerGesture(p, "hold_right", 0);
                    StarScenesAPI.say(v, line(v, p, "juntos_mano_fin"), 3);
                    StarScenesAPI.play(v, "wave");
                }
            }
        }
    }

    private static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        for (Iterator<Job> it = List.copyOf(JOBS.values()).iterator(); it.hasNext(); ) {
            Job j = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(j.player);
            Villager v = j.v;
            long now = v.level().getGameTime();
            boolean gone = p == null || !v.isAlive() || p.level() != v.level() || p.isDeadOrDying();
            if (gone || now >= j.end) {
                stop(v);
                continue;
            }
            if (j.kind.id().equals("mano")) {
                // se suelta agachandote, si te alejas mucho (teletransporte) o si te subis a algo
                if (p.isShiftKeyDown() || p.distanceTo(v) > 8 || p.isPassenger() || v.isSleeping()) {
                    stop(v);
                } else {
                    follow(j, p, false);
                }
            }
        }
    }

    public static void init() {
        StarScenesAPI.registerAction("juntos", (p, s, val) -> {
            if (s instanceof Villager v) {
                start(p, v, val);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Together::tick);
        // que nadie quede congelado al guardar y cerrar
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (Job j : List.copyOf(JOBS.values())) {
                stop(j.v);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            if (e instanceof Villager v && JOBS.containsKey(v.getUUID())) {
                stop(v);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> s.execute(() -> {
            for (Job j : List.copyOf(JOBS.values())) {
                if (j.player.equals(h.player.getUUID())) {
                    stop(j.v);
                }
            }
        }));
    }
}
