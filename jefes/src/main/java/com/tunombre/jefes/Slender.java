package com.tunombre.jefes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Slenderman: rafagas de glitch para los jugadores cercanos y el minijuego de las ocho paginas. */
public final class Slender {
    private Slender() { }

    /** manda una rafaga de glitch a los jugadores a menos de {@code range} bloques del jefe */
    public static void burst(JefeEntity e, float range, int ticks, float strength, boolean flash) {
        if (!(e.level() instanceof ServerLevel lv)) return;
        for (ServerPlayer p : lv.players()) {
            if (p.isSpectator() || p.distanceToSqr(e) > range * range) continue;
            if (e.getController() != null && e.getController().equals(p.getUUID())) continue;
            Net.toPlayer(p, new Net.Burst(ticks, strength * (float) (1.0 - Math.sqrt(p.distanceToSqr(e)) / (range * 1.4)), flash));
        }
    }

    /** estado guardado en el mundo: cuantas paginas hay y cuantas se juntaron */
    public static final class Game extends SavedData {
        public int total, collected, placed;
        public boolean active;

        public static Game get(MinecraftServer s) {
            return s.overworld().getDataStorage().computeIfAbsent(Game::load, Game::new, "jefes_slender");
        }

        static Game load(CompoundTag t) {
            Game g = new Game();
            g.total = t.getInt("total"); g.collected = t.getInt("collected"); g.placed = t.getInt("placed"); g.active = t.getBoolean("active");
            return g;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putInt("total", total); t.putInt("collected", collected); t.putInt("placed", placed); t.putBoolean("active", active);
            return t;
        }

        public void sync(MinecraftServer s) {
            setDirty();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) Net.toPlayer(p, new Net.Game(collected, total, active));
        }
    }

    public static void join(ServerPlayer p) {
        if (p.getServer() == null) return;
        Game g = Game.get(p.getServer());
        Net.toPlayer(p, new Net.Game(g.collected, g.total, g.active));
    }

    /** un jugador junta una pagina: se descuenta del mundo y se avisa a todos */
    public static void collect(ServerPlayer p, ServerLevel lv, BlockPos pos) {
        MinecraftServer s = lv.getServer();
        Game g = Game.get(s);
        g.collected++;
        lv.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.4F, 0.8F);
        Net.toPlayer(p, new Net.Burst(26, 0.8F, false));
        if (g.active) {
            int left = g.total - g.collected;
            for (ServerPlayer o : s.getPlayerList().getPlayers()) {
                o.displayClientMessage(Component.literal("§f§lPagina " + g.collected + "/" + g.total + (left > 0 ? " §7— quedan " + left : "")), true);
            }
            if (g.collected == 3 || g.collected == 6) {
                for (ServerPlayer o : s.getPlayerList().getPlayers()) {
                    lv.playSound(null, o.getX(), o.getY(), o.getZ(), Jefes.SLENDER_THEME.get(), SoundSource.MUSIC, 0.9F, 1F);
                }
            }
            if (g.collected >= g.total && g.total > 0) {
                g.active = false;
                s.getPlayerList().broadcastSystemMessage(Component.literal("§a§lJuntaron todas las paginas."), false);
                for (ServerPlayer o : s.getPlayerList().getPlayers()) Net.toPlayer(o, new Net.Burst(80, 0.5F, false));
            }
        }
        g.sync(s);
    }

    /** reparte paginas por paredes que haya cerca: busca huecos de aire con una pared solida al lado */
    public static int scatter(ServerLevel lv, BlockPos centre, int radius, int count) {
        Random r = new Random();
        List<BlockPos> done = new ArrayList<>();
        Game g = Game.get(lv.getServer());
        int tries = 0;
        double minD = Math.max(8, radius * 1.3 / Math.sqrt(Math.max(1, count)));
        while (done.size() < count && tries++ < 8000) {
            int x = centre.getX() + r.nextInt(radius * 2 + 1) - radius, z = centre.getZ() + r.nextInt(radius * 2 + 1) - radius;
            if ((x - centre.getX()) * (x - centre.getX()) + (z - centre.getZ()) * (z - centre.getZ()) > radius * radius) continue;
            boolean near = false;
            for (BlockPos q : done) if (q.distToLowCornerSqr(x, q.getY(), z) < minD * minD) { near = true; break; }
            if (near) continue;
            for (int y = centre.getY() + 14; y > centre.getY() - 14; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (!lv.isLoaded(p) || !lv.getBlockState(p).isAir() || !lv.getBlockState(p.above()).isAir() || !lv.getBlockState(p.below()).isSolid()) continue;
                Direction[] ds = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
                List<Direction> ok = new ArrayList<>();
                for (Direction d : ds) {
                    BlockPos w = p.relative(d);
                    BlockState ws = lv.getBlockState(w);
                    if (ws.isFaceSturdy(lv, w, d.getOpposite()) && !ws.is(net.minecraft.tags.BlockTags.LEAVES)) ok.add(d.getOpposite());
                }
                if (ok.isEmpty()) continue;
                Direction face = ok.get(r.nextInt(ok.size()));
                BlockPos at = p.above(r.nextInt(2));
                lv.setBlock(at, Jefes.PAGINA.get().defaultBlockState().setValue(PaginaBlock.FACING, face).setValue(PaginaBlock.NUM, g.placed++ % 8), 3);
                done.add(at);
                break;
            }
        }
        g.total += done.size();
        g.sync(lv.getServer());
        return done.size();
    }

}
