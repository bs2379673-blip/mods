package com.tunombre.obscurearcade;

import com.cotii.cubosu.api.CubOsuResult;
import com.cotii.cubosu.api.CubOsuServerApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Un duelo en una consola. Toda la logica corre en el servidor: los clientes solo avisan "acerte/falle el carril N" y aqui se
 * decide la barra, quien golpea a quien, las animaciones (Epic Fight las sincroniza a todos los que estan cerca), el KO y el
 * ganador. Las canciones las lanza el servidor a los dos jugadores en el mismo tick.
 */
public final class Duel {
    public enum Phase { WAIT, COUNT, PLAY, END }

    public final ServerLevel level;
    public final BlockPos pos;
    private final MinecraftServer server;
    private final String[] songs = new String[2];
    private final Styles.Style style;
    private final String endCmd;
    private final Vec3[] spots = new Vec3[2];
    private final Fighter[] f = new Fighter[2];
    private final ServerBossEvent boss = new ServerBossEvent(Component.literal("Duelo"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    public Phase phase = Phase.WAIT;
    private int timer;
    private float bar = 0.5F;
    private final int[] combo = new int[2];
    private final long[] lastAttack = new long[2], lastReact = new long[2], lastAny = new long[2];
    private final boolean[] finished = new boolean[2];
    private final int[] score = new int[2];
    private long deadline = Long.MAX_VALUE;
    private int rate;
    private long rateTick;

    Duel(ServerLevel level, VersusEntity be, ServerPlayer first) {
        this.level = level;
        this.pos = be.getBlockPos();
        this.server = level.getServer();
        this.songs[0] = be.song1;
        this.songs[1] = be.song2.isBlank() ? be.song1 : be.song2;
        this.style = Styles.get(be.style);
        this.endCmd = be.endCmd;
        this.spots[0] = be.spot(0);
        this.spots[1] = be.spot(1);
        this.f[0] = Fighter.of(first);
        this.timer = 20 * 60;
    }

    public boolean has(java.util.UUID id) { return (f[0] != null && f[0].id().equals(id)) || (f[1] != null && f[1].id().equals(id)); }

    private int side(java.util.UUID id) {
        if (f[0] != null && f[0].id().equals(id)) return 0;
        if (f[1] != null && f[1].id().equals(id)) return 1;
        return -1;
    }

    public boolean joinSecond(ServerPlayer p) {
        if (phase != Phase.WAIT || f[1] != null) return false;
        f[1] = Fighter.of(p);
        phase = Phase.COUNT;
        timer = 20 * 4;
        boss.setName(Component.literal(f[0].name() + "  vs  " + f[1].name()));
        boss.setProgress(0.5F);
        for (int i = 0; i < 2; i++) {
            Vec3 s = spots[i], o = spots[1 - i];
            float yaw = (float) (Mth.atan2(-(o.x - s.x), o.z - s.z) * 57.29577951308232D);
            f[i].player().teleportTo(level, s.x, s.y, s.z, yaw, 0F);
            f[i].play(style.stance);
            lastAny[i] = server.getTickCount();
        }
        return true;
    }

    // ------------------------------------------------------------------ ciclo ----
    /** @return false cuando el duelo termino y debe borrarse */
    boolean tick() {
        long now = server.getTickCount();
        switch (phase) {
            case WAIT -> {
                if (!f[0].valid() || --timer <= 0) { cancel("El duelo se canceló: nadie se unió a tiempo."); return false; }
                if (now % 40 == 0) f[0].player().displayClientMessage(Component.literal("Esperando rival… otra persona debe usar la consola."), true);
            }
            case COUNT -> {
                if (!f[0].valid() || !f[1].valid()) { cancel("El duelo se canceló."); return false; }
                if (timer % 20 == 0 && timer > 0) title("" + (timer / 20), "Prepárense", 0xFFFFFF);
                if (--timer <= 0) startPlay();
            }
            case PLAY -> {
                if (!f[0].valid() || !f[1].valid()) {
                    int gone = !f[0].valid() ? 0 : 1;
                    if (f[1 - gone].valid()) end(1 - gone, "El rival abandonó");
                    else cancel("El duelo se canceló.");
                    return phase == Phase.END;
                }
                if (now % 20 == 0) { boss.setProgress(Mth.clamp(bar, 0F, 1F)); updateWatchers(); }
                if (now % 10 == 0) anchor();
                for (int i = 0; i < 2; i++) if (now - lastAny[i] > 30) { f[i].play(style.stance); lastAny[i] = now; }
                if (finished[0] && finished[1]) decide();
                else if ((finished[0] || finished[1]) && now > deadline) decide();
            }
            case END -> {
                if (--timer <= 0) { boss.removeAllPlayers(); return false; }
            }
        }
        return true;
    }

    private void startPlay() {
        boolean ok = true;
        for (int i = 0; i < 2; i++) {
            ServerPlayer p = f[i].player();
            if (!CubOsuServerApi.hasMod(p) || !CubOsuServerApi.play(p, songs[i])) ok = false;
        }
        if (!ok) { cancel("No se pudo iniciar la canción (¿ambos tienen CubOsu y la canción en el servidor?)."); return; }
        phase = Phase.PLAY;
        title("¡PELEEN!", f[0].name() + " vs " + f[1].name(), 0xFF5555);
        for (int i = 0; i < 2; i++) {
            Net.toClient(f[i].player(), "duel", "on");
            f[i].play(style.taunt);
            lastAny[i] = server.getTickCount();
        }
        updateWatchers();
    }

    /** el servidor mantiene a los dos en el ring: la barra empuja la linea de pelea hacia el que va perdiendo */
    private void anchor() {
        Vec3 axis = spots[1].subtract(spots[0]);
        axis = new Vec3(axis.x, 0, axis.z);
        if (axis.lengthSqr() < 1.0E-4) return;
        axis = axis.normalize();
        double shift = (bar - 0.5D) * 2D * style.ringShift * 0.5D;
        for (int i = 0; i < 2; i++) {
            Vec3 t = spots[i].add(axis.scale(shift));
            LivingEntity e = f[i].entity();
            double dx = e.getX() - t.x, dz = e.getZ() - t.z;
            if (dx * dx + dz * dz > style.resetDist * style.resetDist || Math.abs(e.getY() - t.y) > 1.5) {
                Vec3 o = spots[1 - i];
                float yaw = (float) (Mth.atan2(-(o.x - t.x), o.z - t.z) * 57.29577951308232D);
                f[i].player().teleportTo(level, t.x, t.y, t.z, yaw, 0F);
            }
        }
    }

    private void updateWatchers() {
        for (ServerPlayer sp : level.players()) {
            boolean near = sp.blockPosition().distSqr(pos) < 48 * 48;
            if (near) boss.addPlayer(sp); else boss.removePlayer(sp);
        }
    }

    // ------------------------------------------------------------------ eventos de los clientes ----
    void onNote(ServerPlayer p, int lane, int judgement) {
        if (phase != Phase.PLAY) return;
        int s = side(p.getUUID());
        if (s < 0) return;
        long now = server.getTickCount();
        if (now != rateTick) { rateTick = now; rate = 0; }
        if (++rate > 12) return;
        if (judgement != 300 && judgement != 200 && judgement != 100 && judgement != -1) return;
        int o = 1 - s;
        int throttle = Math.max(1, style.throttleMs / 50);
        if (judgement > 0) {
            combo[s]++;
            boolean fin = combo[s] % style.finisherCombo == 0 && judgement >= 200;
            double g = style.gain(judgement) + (fin ? style.finisherBonus : 0);
            bar += (s == 0 ? g : -g);
            if (fin || now - lastAttack[s] >= throttle) {
                play(s, style.attackFor(lane, judgement, fin));
                lastAttack[s] = now;
                lunge(s, o, style.lunge);
            }
            if (fin || now - lastReact[o] >= throttle) {
                String react = fin || (judgement >= 300 && (now & 3) == 0) ? style.pick(style.hitHeavy) : judgement < 200 ? style.pick(style.dodge) : style.pick(style.hitLight);
                play(o, react);
                lastReact[o] = now;
                lunge(o, s, -style.knock);
            }
            if (fin) title("¡COMBO " + combo[s] + "!", f[s].name(), 0xFFAA00);
        } else {
            combo[s] = 0;
            bar += (s == 0 ? -style.missLoss : style.missLoss);
            if (now - lastAttack[o] >= throttle) {
                play(o, style.pick(style.counter));
                lastAttack[o] = now;
                lunge(o, s, style.lunge);
            }
            if (now - lastReact[s] >= throttle) {
                play(s, style.pick(style.hitLight));
                lastReact[s] = now;
                lunge(s, o, -style.knock);
            }
        }
        bar = Mth.clamp(bar, 0F, 1F);
        if (style.ko) {
            if (bar >= 1F) end(0, "¡KO!");
            else if (bar <= 0F) end(1, "¡KO!");
        }
    }

    private void play(int side, String anim) {
        if (anim == null) return;
        f[side].play(anim);
        lastAny[side] = server.getTickCount();
    }

    /** empujon horizontal de un luchador hacia (o desde) el otro */
    private void lunge(int who, int other, double amount) {
        LivingEntity e = f[who].entity(), t = f[other].entity();
        Vec3 d = new Vec3(t.getX() - e.getX(), 0, t.getZ() - e.getZ());
        if (d.lengthSqr() < 1.0E-4) return;
        d = d.normalize().scale(amount);
        e.setDeltaMovement(d.x, e.getDeltaMovement().y, d.z);
        e.hurtMarked = true;
    }

    // ------------------------------------------------------------------ resultados ----
    void onFinish(ServerPlayer p, CubOsuResult r) {
        int s = side(p.getUUID());
        if (s < 0 || phase != Phase.PLAY) return;
        finished[s] = true;
        score[s] = r.score();
        if (deadline == Long.MAX_VALUE) deadline = server.getTickCount() + 20 * 20;
    }

    private void decide() {
        if (phase != Phase.PLAY) return;
        int w = bar > 0.5F ? 0 : bar < 0.5F ? 1 : score[0] >= score[1] ? 0 : 1;
        end(w, "Fin de la canción");
    }

    private void end(int winner, String why) {
        if (phase == Phase.END) return;
        phase = Phase.END;
        timer = 20 * 8;
        int loser = 1 - winner;
        for (int i = 0; i < 2; i++) {
            if (f[i] != null && f[i].player() != null) {
                for (String s : songs) { try { CubOsuServerApi.stop(f[i].player(), s); } catch (RuntimeException ignored) { } }
                Net.toClient(f[i].player(), "duel", "off");
            }
        }
        if (f[winner].valid()) f[winner].play(style.win);
        if (f[loser].valid()) f[loser].play(style.lose);
        for (int i = 0; i < 2; i++) {
            if (f[i] == null || f[i].player() == null) continue;
            ServerPlayer p = f[i].player();
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(i == winner ? "¡VICTORIA!" : "DERROTA")
                    .withStyle(i == winner ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(why + " · ganó " + f[winner].name())));
        }
        if (!endCmd.isBlank()) {
            String cmd = endCmd.replace("{ganador}", f[winner].name()).replace("{perdedor}", f[loser].name());
            try { server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd); } catch (RuntimeException ignored) { }
        }
        boss.setName(Component.literal("Ganó " + f[winner].name()));
        boss.setProgress(winner == 0 ? 1F : 0F);
    }

    void cancel(String msg) {
        boolean wasPlaying = phase == Phase.PLAY;
        phase = Phase.END;
        timer = 1;
        for (int i = 0; i < 2; i++) {
            if (f[i] == null || f[i].player() == null) continue;
            ServerPlayer p = f[i].player();
            if (p.hasDisconnected()) continue;
            p.displayClientMessage(Component.literal(msg), false);
            if (wasPlaying) {
                for (String s : songs) { try { CubOsuServerApi.stop(p, s); } catch (RuntimeException ignored) { } }
            }
            Net.toClient(p, "duel", "off");
        }
        boss.removeAllPlayers();
    }

    void forget(java.util.UUID id) {
        if (phase == Phase.PLAY && side(id) >= 0) {
            int other = 1 - side(id);
            if (f[other] != null && f[other].valid()) end(other, "El rival abandonó"); else cancel("El duelo se canceló.");
        } else if (phase != Phase.END) cancel("El duelo se canceló.");
    }

    void forceCancel() { if (phase != Phase.END) cancel("Un administrador canceló el duelo."); boss.removeAllPlayers(); }

    private void title(String t, String sub, int color) {
        for (int i = 0; i < 2; i++) {
            if (f[i] == null || f[i].player() == null || !f[i].valid()) continue;
            ServerPlayer p = f[i].player();
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 18, 6));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(s -> s.withColor(color))));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
        }
    }

    public Fighter fighter(int i) { return f[i]; }

    public float bar() { return bar; }
}
