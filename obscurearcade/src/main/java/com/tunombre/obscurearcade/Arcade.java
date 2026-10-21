package com.tunombre.obscurearcade;

import com.cotii.cubosu.api.CubOsuResult;
import com.cotii.cubosu.api.CubOsuServerApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Logica de la maquina recreativa: cobra, lanza la cancion, guarda el top y da el premio. */
public final class Arcade {
    private record Play(ResourceKey<Level> dim, BlockPos pos, String song) { }

    private static final Map<UUID, Play> PLAYING = new HashMap<>();
    private static final List<String> RANKS = List.of("D", "C", "B", "A", "S", "SS");

    private Arcade() { }

    public static boolean isPlaying(UUID id) { return PLAYING.containsKey(id); }

    public static void forget(UUID id) { PLAYING.remove(id); }

    public static void clear() { PLAYING.clear(); }

    private static void say(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s), true); }

    private static Item costItem(ArcadeEntity be) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(be.costItem.isBlank() ? "obscurearcade:arcade_token" : be.costItem));
        return it;
    }

    private static int count(ServerPlayer p, Item it) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        return n;
    }

    private static void take(ServerPlayer p, Item it, int n) {
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) break;
            if (s.is(it)) { int t = Math.min(n, s.getCount()); s.shrink(t); n -= t; }
        }
    }

    public static void use(ServerPlayer p, ServerLevel level, ArcadeEntity be) {
        if (p.isShiftKeyDown()) { board(p, be); return; }
        if (be.song.isBlank()) { say(p, "Esta máquina no tiene canción (configúrala con la Llave Arcade)"); return; }
        if (!CubOsuServerApi.hasMod(p)) { say(p, "Necesitas tener CubOsu! instalado para jugar"); return; }
        if (PLAYING.containsKey(p.getUUID()) || Duels.inDuel(p.getUUID())) { say(p, "Ya estás jugando"); return; }
        Item cost = costItem(be);
        if (be.costCount > 0 && cost != null) {
            if (count(p, cost) < be.costCount) { say(p, "Necesitas " + be.costCount + " × " + cost.getDescription().getString()); return; }
            take(p, cost, be.costCount);
        }
        if (!CubOsuServerApi.play(p, be.song)) {
            if (be.costCount > 0 && cost != null) p.getInventory().add(new ItemStack(cost, be.costCount));
            say(p, "No se pudo iniciar la canción '" + be.song + "' (¿está guardada en el servidor?)");
            return;
        }
        PLAYING.put(p.getUUID(), new Play(level.dimension(), be.getBlockPos(), be.song));
    }

    private static void board(ServerPlayer p, ArcadeEntity be) {
        p.sendSystemMessage(Component.literal("§6§lTOP · " + (be.song.isBlank() ? "(sin canción)" : be.song)));
        if (be.top.isEmpty()) p.sendSystemMessage(Component.literal("§7Nadie ha jugado todavía"));
        int i = 1;
        for (CompoundTag t : be.top)
            p.sendSystemMessage(Component.literal("§e" + i++ + ". §f" + t.getString("n") + " §7· " + t.getInt("s") + " · " + String.format("%.1f", t.getFloat("a")) + "% · " + t.getString("r")));
    }

    public static void onFinish(ServerPlayer p, CubOsuResult r) {
        Play pl = PLAYING.remove(p.getUUID());
        if (pl == null) return;
        ServerLevel level = p.server.getLevel(pl.dim());
        if (level == null || !(level.getBlockEntity(pl.pos()) instanceof ArcadeEntity be)) return;
        if (r.score() > 0) be.record(p.getGameProfile().getName(), r.score(), r.accuracy(), r.rank());
        int place = 1;
        for (CompoundTag t : be.top) { if (t.getString("n").equals(p.getGameProfile().getName())) break; place++; }
        p.sendSystemMessage(Component.literal("§6" + pl.song() + " §7· puntaje §f" + r.score() + " §7· rango §f" + r.rank()
                + (place <= 5 ? " §7· puesto §e#" + place : "")));
        boolean ok = r.success() && (be.rewardRank.isBlank() || RANKS.indexOf(r.rank()) >= RANKS.indexOf(be.rewardRank));
        if (ok && !be.rewardCmd.isBlank()) {
            String cmd = be.rewardCmd.replace("{jugador}", p.getGameProfile().getName()).replace("{rango}", r.rank()).replace("{puntaje}", String.valueOf(r.score()));
            try { p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack().withSuppressedOutput(), cmd); } catch (RuntimeException ignored) { }
        }
    }
}
