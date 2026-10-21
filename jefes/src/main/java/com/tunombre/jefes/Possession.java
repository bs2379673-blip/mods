package com.tunombre.jefes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Un administrador "se convierte" en un jefe: queda en espectador, invisible, y lo que aprieta mueve al jefe. */
public final class Possession {
    private static final class Session {
        final JefeEntity boss;
        final GameType prev;

        Session(JefeEntity boss, GameType prev) { this.boss = boss; this.prev = prev; }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private Possession() { }

    public static boolean active(ServerPlayer p) { return SESSIONS.containsKey(p.getUUID()); }

    public static JefeEntity boss(ServerPlayer p) { Session s = SESSIONS.get(p.getUUID()); return s == null ? null : s.boss; }

    /** false si el jefe ya lo maneja otro */
    public static boolean begin(ServerPlayer admin, JefeEntity boss) {
        if (boss.getController() != null && !boss.getController().equals(admin.getUUID())) {
            admin.displayClientMessage(Component.literal("§cEse jefe ya lo esta manejando otro administrador."), true);
            return false;
        }
        end(admin);
        GameType prev = admin.gameMode.getGameModeForPlayer();
        SESSIONS.put(admin.getUUID(), new Session(boss, prev == GameType.SPECTATOR ? GameType.SURVIVAL : prev));
        boss.setController(admin.getUUID());
        admin.setGameMode(GameType.SPECTATOR);
        admin.teleportTo(boss.getX(), boss.getY(), boss.getZ());
        Net.toPlayer(admin, new Net.Possess(boss.getId()));
        admin.displayClientMessage(Component.literal("§aAhora sos §f" + boss.displayName() + "§a. Mouse: mirar. WASD: moverte. Clic izq: zarpazo. Clic der: agarrar. Ver controles en pantalla."), false);
        return true;
    }

    public static void end(ServerPlayer admin) {
        Session s = SESSIONS.remove(admin.getUUID());
        if (s == null) return;
        s.boss.setController(null);
        s.boss.releaseControl();
        Net.toPlayer(admin, new Net.Possess(-1));
        if (admin.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) admin.setGameMode(s.prev);
        admin.teleportTo(s.boss.getX(), s.boss.getY(), s.boss.getZ());
    }

    public static void input(ServerPlayer p, Net.Input in) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) s.boss.setInput(in);
    }

    public static void action(ServerPlayer p, int id) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        if (id == 7) { end(p); return; }
        s.boss.doAction(id);
    }

    /** cada tick del servidor: si el jefe desaparecio o el admin se fue, se termina */
    public static void tick() {
        for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
            Session s = e.getValue();
            ServerPlayer p = s.boss.getServer() == null ? null : s.boss.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p.hasDisconnected() || s.boss.isRemoved() || !s.boss.isAlive()) {
                SESSIONS.remove(e.getKey());
                if (!s.boss.isRemoved()) { s.boss.setController(null); s.boss.releaseControl(); }
                if (p != null && !p.hasDisconnected()) { Net.toPlayer(p, new Net.Possess(-1)); if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) p.setGameMode(s.prev); }
                continue;
            }
            // mantiene al admin pegado al jefe (para que el servidor cargue los chunks alrededor suyo)
            if (p.distanceToSqr(s.boss) > 9) p.teleportTo(s.boss.getX(), s.boss.getY(), s.boss.getZ());
        }
    }

    public static void onLogout(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        s.boss.setController(null);
        s.boss.releaseControl();
        if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) p.setGameMode(s.prev);
    }
}
