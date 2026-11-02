package com.starlevi.starfest.client;

import com.starlevi.starfest.block.ShowBlockEntity;
import com.starlevi.starfest.net.Net;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;

/**
 * Fuegos artificiales del lanzador: solo visuales (no son entidades, no rompen nada). Salen solos en
 * la parte fuerte de la cancion con los golpes del bombo, o todos juntos con /starfest fuegos.
 */
public final class Fireworks {
    private static final class Rocket {
        double x, y, z, vx, vy, vz;
        int ttl;
        List<FireworkExplosion> boom;
    }

    private static final List<Rocket> ROCKETS = new ArrayList<>();
    private static final Set<ShowBlockEntity> LAUNCHERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final RandomSource RNG = RandomSource.create();
    private static final FireworkExplosion.Shape[] SHAPES = {FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.BURST,
            FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.SMALL_BALL};

    private Fireworks() {
    }

    /** Cada tick de cada lanzador (en el cliente). */
    public static void launcherTick(Level level, BlockPos pos, ShowBlockEntity be) {
        LAUNCHERS.add(be);
        Beat b = Show.beatAt(pos);
        if (!b.playing() || !b.drop()) {
            return;
        }
        long now = System.currentTimeMillis();
        int beat = b.beat();
        // en la parte fuerte: un cohete cada 4 tiempos (alternando lanzadores) y con los golpes grandes
        boolean onGrid = beat != be.lastBeat && Math.floorMod(beat + Palette.slot(pos), 4) == 0;
        boolean bigHit = b.kick() > 0.85f && now - be.lastLaunch > 1500;
        if ((onGrid || bigHit) && now - be.lastLaunch > 700) {
            be.lastLaunch = now;
            launch(level, pos, b);
        }
        be.lastBeat = beat;
    }

    public static void launch(Level level, BlockPos pos, Beat b) {
        Rocket r = new Rocket();
        r.x = pos.getX() + 0.5;
        r.y = pos.getY() + 0.8;
        r.z = pos.getZ() + 0.5;
        r.vx = RNG.nextGaussian() * 0.02;
        r.vz = RNG.nextGaussian() * 0.02;
        r.vy = 0.9 + RNG.nextDouble() * 0.35;
        r.ttl = 20 + RNG.nextInt(10);
        IntArrayList colors = new IntArrayList();
        int c0 = RNG.nextInt(4);
        colors.add(Palette.main(b, c0));
        colors.add(Palette.main(b, c0 + 1));
        IntArrayList fade = new IntArrayList();
        fade.add(0xFFFFFF);
        r.boom = List.of(new FireworkExplosion(SHAPES[RNG.nextInt(SHAPES.length)], colors, fade, true, b.drop()));
        ROCKETS.add(r);
        level.playLocalSound(r.x, r.y, r.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 2.0f, 1.0f, false);
    }

    /** /starfest fuegos: todos los lanzadores cerca disparan, uno detras de otro. */
    public static void volley(Net.Fx fx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos c = new BlockPos(fx.x(), fx.y(), fx.z());
        Beat b = Show.beatAt(c);
        Beat colors = b.playing() ? b : new Beat(true, 0, 1, 1, 1, 1, 1, 1, 1.3f, RNG.nextInt(64), 120);
        int n = 0;
        for (ShowBlockEntity be : new ArrayList<>(LAUNCHERS)) {
            if (be.isRemoved() || be.getLevel() != level || be.getBlockPos().distSqr(c) > (double) fx.radius() * fx.radius()) {
                continue;
            }
            for (int i = 0; i < 3; i++) {
                launch(level, be.getBlockPos(), colors);
            }
            n++;
        }
        if (n == 0) {
            // sin lanzadores: salen en ronda alrededor del punto
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                launch(level, c.offset((int) (Math.cos(a) * 6), 0, (int) (Math.sin(a) * 6)), colors);
            }
        }
    }

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            ROCKETS.clear();
            return;
        }
        Iterator<Rocket> it = ROCKETS.iterator();
        while (it.hasNext()) {
            Rocket r = it.next();
            r.x += r.vx;
            r.y += r.vy;
            r.z += r.vz;
            r.vy *= 0.96;
            level.addParticle(ParticleTypes.FIREWORK, r.x, r.y - 0.3, r.z, RNG.nextGaussian() * 0.05, -r.vy * 0.5, RNG.nextGaussian() * 0.05);
            if (--r.ttl <= 0) {
                level.createFireworks(r.x, r.y, r.z, 0, 0, 0, r.boom);
                it.remove();
            }
        }
    }
}
