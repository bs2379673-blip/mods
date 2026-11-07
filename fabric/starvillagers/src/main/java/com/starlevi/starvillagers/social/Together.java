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
    enum Mode { FACE, HAND, SIT, CARRY, CARRIED }

    /** anim = la del aldeano, playerAnim = la tuya ("" = ninguna). */
    private record Kind(String id, Mode mode, int hearts, int gain, String anim, String playerAnim, double dist, int ticks, ParticleOptions particle) {
    }

    private static final Map<String, Kind> KINDS = Map.of(
            "chocar", new Kind("chocar", Mode.FACE, 20, 2, "high_five", "high_five", 0.95, 30, ParticleTypes.HAPPY_VILLAGER),
            "acariciar", new Kind("acariciar", Mode.FACE, 30, 2, "", "pat", 0.75, 36, ParticleTypes.HEART),
            "sentarse", new Kind("sentarse", Mode.SIT, 30, 3, "sit_ground", "sit_ground", 0, 12000, ParticleTypes.HAPPY_VILLAGER),
            "abrazar", new Kind("abrazar", Mode.FACE, 40, 4, "hug", "hug", 0.62, 54, ParticleTypes.HEART),
            "mano", new Kind("mano", Mode.HAND, 60, 3, "hold_left", "hold_right", 0, 6000, ParticleTypes.HEART),
            "cargar", new Kind("cargar", Mode.CARRY, 60, 3, "carried", "carry", 0, 6000, ParticleTypes.HEART),
            "que_me_cargue", new Kind("que_me_cargue", Mode.CARRIED, 70, 3, "carry", "carried", 0, 6000, ParticleTypes.HEART),
            "besar", new Kind("besar", Mode.FACE, 80, 5, "kiss", "kiss", 0.55, 50, ParticleTypes.HEART));

    /** Para los puntos de montura: el aldeano que llevas en brazos, o el que te lleva. */
    public static boolean carrying(net.minecraft.world.entity.Entity vehicle, net.minecraft.world.entity.Entity passenger) {
        // (los aldeanos solo se suben a jugadores, y los jugadores a aldeanos, con esto: vale igual en el cliente)
        return vehicle instanceof net.minecraft.world.entity.player.Player && passenger instanceof Villager
                || vehicle instanceof Villager && passenger instanceof net.minecraft.world.entity.player.Player;
    }

    private static final class Job {
        Villager v;
        UUID player;
        Kind kind;
        long end;
        boolean noAi;
        int refresh;
        Vec3 at;
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
        if (p.isPassenger() || v.isPassenger() || p.isVehicle()) {
            StarScenesAPI.say(v, line(v, p, "juntos_sin_ganas"), 3);
            return;
        }
        JOBS.put(v.getUUID(), j);
        v.getNavigation().stop();
        v.setNoAi(k.mode() != Mode.CARRIED);
        j.at = p.position();
        switch (k.mode()) {
            case FACE -> {
                // se pone enfrente tuyo, mirandote
                Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
                Vec3 at = p.position().add(look.scale(k.dist()));
                float yaw = p.getYRot() + 180;
                v.snapTo(at.x, p.getY(), at.z, yaw, 0);
                v.setYBodyRot(yaw);
                v.setYHeadRot(yaw);
                if (!k.playerAnim().isEmpty()) {
                    StarScenesAPI.playerGesture(p, k.playerAnim(), k.ticks() / 20f);
                }
                StarScenesAPI.play(v, k.anim().isEmpty() ? Social.anim(Social.personality(v), "juntos_" + k.id()) : k.anim());
            }
            case HAND -> follow(j, p, true);
            case SIT -> {
                // a tu derecha, mirando para el mismo lado
                Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
                Vec3 at = p.position().add(new Vec3(-fwd.z, 0, fwd.x).scale(0.85));
                v.snapTo(at.x, p.getY(), at.z, p.getYRot(), 0);
                v.setYBodyRot(p.getYRot());
                v.setYHeadRot(p.getYRot());
                gestures(j, p, true);
            }
            case CARRY -> {
                v.startRiding(p, true, true);
                gestures(j, p, true);
            }
            case CARRIED -> {
                p.startRiding(v, true, true);
                gestures(j, p, true);
            }
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
        gestures(j, p, first);
    }

    /** Los gestos en ronda se vuelven a mandar cada 2 s (asi siguen mientras dure). */
    private static void gestures(Job j, ServerPlayer p, boolean first) {
        if (first || --j.refresh <= 0) {
            j.refresh = 40;
            StarScenesAPI.play(j.v, j.kind.anim());
            StarScenesAPI.playerGesture(p, j.kind.playerAnim(), 3);
        }
    }

    public static void stop(Villager v) {
        Job j = JOBS.remove(v.getUUID());
        if (j == null) {
            return;
        }
        v.setNoAi(j.noAi);
        ServerPlayer p = v.level().getServer() == null ? null : v.level().getServer().getPlayerList().getPlayer(j.player);
        if (j.kind.mode() == Mode.CARRY && v.isPassenger()) {
            v.stopRiding();
            if (p != null) {
                // lo baja adelante tuyo
                Vec3 at = p.position().add(Vec3.directionFromRotation(0, p.getYRot()).scale(0.9));
                v.snapTo(at.x, p.getY(), at.z, p.getYRot() + 180, 0);
            }
        }
        if (j.kind.mode() == Mode.CARRIED && p != null && p.getVehicle() == v) {
            p.stopRiding();
        }
        if (j.kind.mode() != Mode.FACE) {
            StarScenesAPI.play(v, "idle");
            if (p != null) {
                StarScenesAPI.playerGesture(p, j.kind.playerAnim(), 0);
                StarScenesAPI.say(v, line(v, p, "juntos_" + j.kind.id() + "_fin"), 3);
                StarScenesAPI.play(v, "wave");
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
            switch (j.kind.mode()) {
                case HAND -> {
                    // se suelta agachandote, si te alejas mucho (teletransporte) o si te subis a algo
                    if (p.isShiftKeyDown() || p.distanceTo(v) > 8 || p.isPassenger() || v.isSleeping()) {
                        stop(v);
                    } else {
                        follow(j, p, false);
                    }
                }
                case SIT -> {
                    // te levantas al moverte
                    if (p.isShiftKeyDown() || p.position().distanceTo(j.at) > 0.6 || p.isPassenger()) {
                        stop(v);
                    } else {
                        gestures(j, p, false);
                    }
                }
                case CARRY -> {
                    // lo bajas agachandote
                    if (p.isShiftKeyDown() || v.getVehicle() != p) {
                        stop(v);
                    } else {
                        float yaw = p.getYRot() + 90;
                        v.setYRot(yaw);
                        v.setYBodyRot(yaw);
                        v.setYHeadRot(p.getYRot());
                        gestures(j, p, false);
                    }
                }
                case CARRIED -> {
                    // te bajas como de cualquier montura (agachandote)
                    if (p.getVehicle() != v) {
                        stop(v);
                    } else {
                        gestures(j, p, false);
                    }
                }
                default -> {
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
