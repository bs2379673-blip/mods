package com.tunombre.concierto;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** efectos para el publico en los momentos cumbre: volar (como en los conciertos de Fortnite), brillo y lluvia de confeti */
public final class Players {
    private static final Map<UUID, Integer> FLY = new HashMap<>();
    private static int confettiUntil = -1;
    private static Vec3 confettiAt = Vec3.ZERO;
    private static float confettiRadius = 60F;
    private static final Vector3f[] COLORS = {new Vector3f(0.22F, 0.77F, 0.73F), new Vector3f(1F, 0.4F, 0.67F), new Vector3f(1F, 0.82F, 0.23F), new Vector3f(1F, 1F, 1F)};

    private Players() { }

    private static boolean near(ServerPlayer p, Vec3 c, float r) {
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        return dx * dx + dz * dz <= r * r;
    }

    /** modo: volar, brillo, confeti, nada */
    public static int apply(MinecraftServer srv, String mode, float secs, Vec3 center, float radius) {
        int ticks = Math.max(20, (int) (secs * 20F));
        int n = 0;
        switch (mode) {
            case "volar" -> {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (p.isCreative() || p.isSpectator() || !near(p, center, radius)) continue;
                    p.getAbilities().mayfly = true;
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.setDeltaMovement(p.getDeltaMovement().add(0, 0.9, 0));
                    p.hurtMarked = true;
                    FLY.put(p.getUUID(), srv.getTickCount() + ticks);
                    n++;
                }
            }
            case "brillo" -> {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (p.isSpectator() || !near(p, center, radius)) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
                    n++;
                }
            }
            case "confeti" -> {
                confettiUntil = srv.getTickCount() + ticks;
                confettiAt = center;
                confettiRadius = radius;
                n = 1;
            }
            case "nada" -> cancel(srv);
            default -> { }
        }
        return n;
    }

    public static void cancel(MinecraftServer srv) {
        confettiUntil = -1;
        for (Iterator<UUID> it = FLY.keySet().iterator(); it.hasNext(); ) {
            ServerPlayer p = srv.getPlayerList().getPlayer(it.next());
            if (p != null) land(p);
            it.remove();
        }
    }

    private static void land(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        p.fallDistance = 0F;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0, false, false));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && FLY.remove(p.getUUID()) != null) land(p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = e.getServer();
        int now = srv.getTickCount();
        if (!FLY.isEmpty()) {
            for (Iterator<Map.Entry<UUID, Integer>> it = FLY.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
                if (p == null) continue;
                p.fallDistance = 0F;
                if (now >= en.getValue()) { land(p); it.remove(); }
            }
        }
        if (confettiUntil >= 0) {
            if (now >= confettiUntil) { confettiUntil = -1; return; }
            if (now % 3 != 0) return;
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                if (p.isSpectator() || !near(p, confettiAt, confettiRadius) || !(p.level() instanceof ServerLevel lv)) continue;
                for (int i = 0; i < 4; i++) {
                    Vector3f c = COLORS[lv.random.nextInt(COLORS.length)];
                    lv.sendParticles(new DustParticleOptions(c, 1.5F), p.getX() + (lv.random.nextDouble() - 0.5) * 14, p.getY() + 5 + lv.random.nextDouble() * 6, p.getZ() + (lv.random.nextDouble() - 0.5) * 14, 1, 0.2, 0.1, 0.2, 0);
                }
                lv.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 8, p.getZ(), 3, 7, 1.5, 7, 0.02);
            }
        }
    }
}
