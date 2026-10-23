package com.tunombre.lootmaster;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Zonas de aventura: dentro de una zona marcada los jugadores pasan a modo aventura; al salir, morir o desconectarse vuelven a supervivencia. */
public final class Zones {
    private static int counter;
    /** Último lugar seguro (en el suelo, dentro de su zona) de cada jugador encerrado: ahí se lo devuelve si intenta salir. */
    private static final java.util.Map<java.util.UUID, double[]> SAFE = new java.util.HashMap<>();
    private static final DustParticleOptions WALL = new DustParticleOptions(new Vector3f(0.75F, 0.05F, 0.08F), 1.4F);

    private Zones() { }

    public static boolean inside(State.Zone z, ServerPlayer p) {
        if (!z.enabled || !z.dim.equals(p.level().dimension().location().toString())) return false;
        int x = p.getBlockX(), y = p.getBlockY(), zz = p.getBlockZ();
        if (x < z.x1 || x > z.x2 || zz < z.z1 || zz > z.z2) return false;
        return z.ignoreY || (y >= z.y1 && y <= z.y2);
    }

    private static boolean inAny(State.Data d, ServerPlayer p) {
        for (State.Zone z : d.zones) if (inside(z, p)) return true;
        return false;
    }

    /** @return true si cambio algo del estado */
    private static boolean check(State.Data d, ServerPlayer p) {
        String id = p.getUUID().toString();
        boolean in = inAny(d, p) && !(d.zOps && p.hasPermissions(2));
        boolean managed = d.advManaged.contains(id);
        GameType gm = p.gameMode.getGameModeForPlayer();
        if (in) {
            if (managed) {
                // un admin lo cambio a mano: dejamos de gestionarlo
                if (gm != GameType.ADVENTURE) { d.advManaged.remove(id); return true; }
            } else if (gm == GameType.SURVIVAL) {
                p.setGameMode(GameType.ADVENTURE);
                d.advManaged.add(id);
                return true;
            }
        } else if (managed) {
            if (gm == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
            d.advManaged.remove(id);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ encierro ----
    private static State.Zone zone(State.Data d, String name) {
        if (name == null) return null;
        for (State.Zone z : d.zones) if (z.name.equals(name)) return z;
        return null;
    }

    private static boolean exempt(State.Data d, ServerPlayer p) {
        GameType gm = p.gameMode.getGameModeForPlayer();
        return gm == GameType.CREATIVE || gm == GameType.SPECTATOR || (d.zOps && p.hasPermissions(2));
    }

    private static ServerLevel levelOf(MinecraftServer s, String dim) {
        for (ServerLevel l : s.getAllLevels()) if (l.dimension().location().toString().equals(dim)) return l;
        return null;
    }

    /** Un lugar para pararse dentro de la zona: el último seguro del jugador, o el suelo en el centro. */
    private static double[] safeSpot(MinecraftServer s, State.Zone z, ServerPlayer p) {
        double[] sp = SAFE.get(p.getUUID());
        if (sp != null && sp[3] == z.name.hashCode()) return sp;
        ServerLevel lv = levelOf(s, z.dim);
        int cx = (z.x1 + z.x2) / 2, cz = (z.z1 + z.z2) / 2;
        int y = lv == null ? p.getBlockY() : lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        if (!z.ignoreY) y = Math.max(z.y1, Math.min(z.y2, y));
        return new double[]{cx + 0.5, y, cz + 0.5, z.name.hashCode()};
    }

    private static void sendBack(MinecraftServer s, State.Zone z, ServerPlayer p) {
        ServerLevel lv = levelOf(s, z.dim);
        if (lv == null) return;
        double[] sp = safeSpot(s, z, p);
        p.stopRiding();
        p.teleportTo(lv, sp[0], sp[1], sp[2], p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.displayClientMessage(Component.literal("§4No puedes salir de esta zona."), true);
    }

    /** Muestra el borde (partículas rojas) solo al jugador que se acerca. */
    private static void showWall(ServerPlayer p, State.Zone z) {
        double x = p.getX(), zz = p.getZ(), y = p.getY();
        double[][] faces = {{z.x1, Double.NaN}, {z.x2 + 1, Double.NaN}, {Double.NaN, z.z1}, {Double.NaN, z.z2 + 1}};
        ServerLevel lv = p.serverLevel();
        for (double[] f : faces) {
            double dist = !Double.isNaN(f[0]) ? Math.abs(x - f[0]) : Math.abs(zz - f[1]);
            if (dist > 5) continue;
            for (int k = 0; k < 10; k++) {
                double a = (p.getRandom().nextDouble() - 0.5) * 7, h = p.getRandom().nextDouble() * 3;
                double px = !Double.isNaN(f[0]) ? f[0] : Math.max(z.x1, Math.min(z.x2 + 1, x + a));
                double pz = !Double.isNaN(f[1]) ? f[1] : Math.max(z.z1, Math.min(z.z2 + 1, zz + a));
                lv.sendParticles(p, WALL, true, px, y + h, pz, 1, 0, 0, 0, 0);
            }
        }
    }

    /** @return true si cambió el estado guardado */
    private static boolean lockCheck(MinecraftServer s, State.Data d, ServerPlayer p, boolean particles) {
        String id = p.getUUID().toString();
        String tz = d.trapped.get(id);
        State.Zone z = zone(d, tz);
        boolean changed = false;
        if (tz != null && (z == null || !z.enabled || !z.lock)) { d.trapped.remove(id); SAFE.remove(p.getUUID()); return true; }
        if (exempt(d, p)) return false;
        if (z == null) {
            // ¿entró a una zona de encierro?
            for (State.Zone q : d.zones) if (q.lock && inside(q, p)) { d.trapped.put(id, q.name); z = q; changed = true; break; }
            if (z == null) return false;
        }
        if (inside(z, p)) {
            if (p.onGround() && !p.isPassenger()) SAFE.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ(), z.name.hashCode()});
            if (particles) showWall(p, z);
        } else if (p.isAlive()) {
            sendBack(s, z, p);
        }
        return changed;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        if (s == null) return;
        State st = State.get(s);
        State.Data d = st.d;
        if (d.zones.isEmpty() && d.advManaged.isEmpty() && d.trapped.isEmpty()) return;
        boolean changed = false;
        ++counter;
        boolean anyLock = !d.trapped.isEmpty();
        for (State.Zone z : d.zones) if (z.lock && z.enabled) { anyLock = true; break; }
        if (anyLock) for (ServerPlayer p : s.getPlayerList().getPlayers()) changed |= lockCheck(s, d, p, counter % 4 == 0);
        if (counter % 5 == 0) for (ServerPlayer p : s.getPlayerList().getPlayers()) changed |= check(d, p);
        if (changed) st.dirty();
    }

    public static int trappedCount(MinecraftServer s) { return State.get(s).d.trapped.size(); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        State st = State.get(p.getServer());
        if (st.d.advManaged.remove(p.getUUID().toString())) {
            if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
            st.dirty();
        }
    }

    /** Al reaparecer o conectarse se vuelve a comprobar al instante: si esta dentro de una zona, aventura de nuevo. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        State st = State.get(p.getServer());
        // encerrado: reaparece dentro de la zona (su cama o spawn de siempre no se tocan; al borrar la zona vuelve a usarlos)
        State.Zone z = zone(st.d, st.d.trapped.get(p.getUUID().toString()));
        if (z != null && z.enabled && z.lock && !exempt(st.d, p) && !e.isEndConquered() && !inside(z, p)) sendBack(p.getServer(), z, p);
        if (check(st.d, p)) st.dirty();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        State st = State.get(p.getServer());
        if (lockCheck(p.getServer(), st.d, p, false)) st.dirty();
        if (check(st.d, p)) st.dirty();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        State st = State.get(p.getServer());
        String id = p.getUUID().toString();
        if (st.d.advManaged.remove(id)) {
            if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
            st.dirty();
        }
    }

    public static int managedCount(MinecraftServer s) { return State.get(s).d.advManaged.size(); }
}
