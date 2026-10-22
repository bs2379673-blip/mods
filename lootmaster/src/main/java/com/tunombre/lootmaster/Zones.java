package com.tunombre.lootmaster;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Zonas de aventura: dentro de una zona marcada los jugadores pasan a modo aventura; al salir, morir o desconectarse vuelven a supervivencia. */
public final class Zones {
    private static int counter;

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

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++counter % 10 != 0) return;
        MinecraftServer s = e.getServer();
        if (s == null) return;
        State st = State.get(s);
        State.Data d = st.d;
        if (d.zones.isEmpty() && d.advManaged.isEmpty() && d.advExempt.isEmpty()) return;
        boolean changed = false;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            String id = p.getUUID().toString();
            boolean in = inAny(d, p) && !(d.zOps && p.hasPermissions(2));
            boolean managed = d.advManaged.contains(id);
            if (in) {
                if (managed) {
                    // un admin lo cambio a mano: dejamos de gestionarlo
                    if (p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) { d.advManaged.remove(id); changed = true; }
                } else if (!d.advExempt.contains(id) && p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
                    p.setGameMode(GameType.ADVENTURE);
                    d.advManaged.add(id);
                    changed = true;
                }
            } else {
                if (managed) {
                    if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
                    d.advManaged.remove(id);
                    changed = true;
                }
                if (d.advExempt.remove(id)) changed = true;
            }
        }
        if (changed) st.dirty();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        State st = State.get(p.getServer());
        String id = p.getUUID().toString();
        if (st.d.advManaged.remove(id)) {
            if (p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) p.setGameMode(GameType.SURVIVAL);
            st.d.advExempt.add(id); // no vuelve a aventura hasta que salga de la zona
            st.dirty();
        }
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
