package com.tunombre.spawncontrol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@Mod(SpawnControl.MODID)
public class SpawnControl {
    public static final String MODID = "spawncontrol";
    public static final Logger LOG = LogUtils.getLogger();

    /** mobs a los que se les nego el spawn: si aun asi intentan entrar al mundo, se cancela */
    private static final Set<Mob> DENIED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** jugadores viendo los bordes de las zonas: uuid -> {clave, ticks restantes} */
    static final Map<UUID, Object[]> VIEWERS = new ConcurrentHashMap<>();
    /** intentos de spawn natural vistos desde que arranco el servidor: id -> {dejados pasar, bloqueados}. Sirve para saber que mobs "quieren" aparecer. */
    static final Map<String, int[]> STATS = new ConcurrentHashMap<>();
    /** mobs que ya pasaron por el spawn normal y fueron aprobados: su entrada al mundo no se vuelve a revisar */
    private static final Set<Mob> APPROVED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** /spawns depurar: avisa a los operadores de cada mob que entra al mundo y por que se dejo pasar o se bloqueo */
    static volatile boolean DEBUG = false;
    private static long lastDebug;
    private static int tick;

    static void debug(Mob m, String how, boolean disk, boolean ok) {
        if (!DEBUG) return;
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
        Rule r = Rules.find(m.getType());
        String why;
        if (r == null) why = Rules.defaultBlock && m.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER ? "sin regla, monstruo bloqueado por defecto" : "sin regla, libre";
        else if (!r.types.contains(how.equals("DIRECT") ? "NATURAL" : how)) why = "tiene regla pero NO controla el tipo " + how + " (controla " + r.types + ")";
        else why = r.enabled ? "regla ACTIVA" : "regla DESACTIVADA";
        String line = "[spawns] " + id + " via " + how + (disk ? " (guardado en chunk)" : "") + " -> " + (ok ? "PASA" : "BLOQUEADO") + " (" + why + ")";
        LOG.info(line);
        long now = System.currentTimeMillis();
        if (now - lastDebug < 250 && ok) return; // no inundar el chat con lo que pasa
        lastDebug = now;
        var s = m.getServer();
        if (s != null) for (ServerPlayer p : s.getPlayerList().getPlayers()) if (p.hasPermissions(2)) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
    }

    public SpawnControl() {
        MinecraftForge.EVENT_BUS.addListener(SpawnControl::onStarting);
        MinecraftForge.EVENT_BUS.addListener(SpawnControl::onFinalize);
        MinecraftForge.EVENT_BUS.addListener(SpawnControl::onJoin);
        MinecraftForge.EVENT_BUS.addListener(SpawnControl::onTick);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Cmds.register(e.getDispatcher()));
        Net.init();
    }

    static void onStarting(ServerStartingEvent e) { Rules.load(); }

    static void onFinalize(MobSpawnEvent.FinalizeSpawn e) {
        Mob m = e.getEntity();
        ServerLevel lv = e.getLevel().getLevel();
        String st = e.getSpawnType().name();
        boolean ok = Rules.allowed(m.getType(), st, lv.dimension().location().toString(), m.position());
        if (Rules.isNaturalType(st)) {
            var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
            if (id != null) STATS.computeIfAbsent(id.toString(), k -> new int[2])[ok ? 0 : 1]++;
        }
        debug(m, st, false, ok);
        if (!ok) {
            e.setSpawnCancelled(true);
            DENIED.add(m);
        } else APPROVED.add(m);
    }

    static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide) return;
        if (!(e.getEntity() instanceof Mob m)) return;
        if (!DENIED.isEmpty() && DENIED.remove(m)) { e.setCanceled(true); return; }
        if (e.loadedFromDisk()) {
            // un mob guardado en el chunk (de antes de apagar el spawn): se borra al cargarse, salvo que sea persistente o tenga nombre
            if (m.isPersistenceRequired() || m.hasCustomName() || m.isPassenger() || m.isLeashed()) return;
            String st = m.getSpawnType() != null ? m.getSpawnType().name() : "DIRECT";
            ServerLevel lvd = (ServerLevel) e.getLevel();
            boolean okd = Rules.allowed(m.getType(), st, lvd.dimension().location().toString(), m.position(), false);
            debug(m, st, true, okd);
            if (!okd) e.setCanceled(true);
            return;
        }
        if (APPROVED.remove(m)) return;
        // entra al mundo sin haber pasado por el spawn normal (otro mod lo agrega directo): se le aplican las mismas reglas
        ServerLevel lv = (ServerLevel) e.getLevel();
        boolean ok = Rules.allowed(m.getType(), "DIRECT", lv.dimension().location().toString(), m.position());
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
        if (id != null) STATS.computeIfAbsent(id.toString(), k -> new int[2])[ok ? 0 : 1]++;
        debug(m, "DIRECT", false, ok);
        if (!ok) e.setCanceled(true);
    }

    static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        tick++;
        if (tick % 20 == 0) checkReleases(s);
        if (tick % 40 == 0) countAlive(s);
        if (tick % 5 == 0 && !VIEWERS.isEmpty()) showZones(s);
    }

    /** libera por dia: cuando el mundo llega al dia programado, activa el mob y lanza el aviso */
    private static void checkReleases(MinecraftServer s) {
        long day = s.overworld().getDayTime() / 24000L + 1;
        boolean changed = false;
        for (Map.Entry<String, Rule> en : Rules.all().entrySet()) {
            Rule r = en.getValue();
            if (r.releaseDay > 0 && !r.released && day >= r.releaseDay) {
                r.enabled = true;
                r.released = true;
                changed = true;
                Announce.fire(s, en.getKey(), r);
                SpawnControl.LOG.info("SpawnControl: dia {} -> liberado {}", day, en.getKey());
            }
        }
        if (changed) Rules.save();
    }

    private static void countAlive(MinecraftServer s) {
        boolean any = false;
        for (Rule r : Rules.all().values()) if (r.max > 0) { any = true; break; }
        if (!any) return;
        Map<Rule, Integer> n = new java.util.IdentityHashMap<>();
        for (ServerLevel lv : s.getAllLevels()) {
            for (Entity en : lv.getAllEntities()) {
                if (!(en instanceof Mob)) continue;
                Rule r = Rules.find(en.getType());
                if (r != null && r.max > 0) n.merge(r, 1, Integer::sum);
            }
        }
        for (Rule r : Rules.all().values()) if (r.max > 0) r.alive.set(n.getOrDefault(r, 0));
    }

    private static void showZones(MinecraftServer s) {
        for (Map.Entry<UUID, Object[]> en : VIEWERS.entrySet()) {
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            Object[] v = en.getValue();
            int left = (Integer) v[1] - 5;
            if (p == null || left <= 0) { VIEWERS.remove(en.getKey()); continue; }
            v[1] = left;
            Rule r = Rules.all().get((String) v[0]);
            if (r == null) continue;
            String dim = p.serverLevel().dimension().location().toString();
            for (Zone z : r.zones) {
                if (!z.dim.equals(dim)) continue;
                DustParticleOptions col = new DustParticleOptions(z.allow ? new Vector3f(0.2F, 1F, 0.3F) : new Vector3f(1F, 0.15F, 0.1F), 2F);
                int pts = (int) Math.max(24, Math.min(240, z.r * 2.2));
                for (int i = 0; i < pts; i++) {
                    double a = Math.PI * 2 * i / pts;
                    double x = z.x + Math.cos(a) * z.r, zz = z.z + Math.sin(a) * z.r;
                    if ((x - p.getX()) * (x - p.getX()) + (zz - p.getZ()) * (zz - p.getZ()) > 90 * 90) continue;
                    for (double dy : new double[]{0.3, 1.8, 3.3}) p.serverLevel().sendParticles(p, col, true, x, p.getY() + dy, zz, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
