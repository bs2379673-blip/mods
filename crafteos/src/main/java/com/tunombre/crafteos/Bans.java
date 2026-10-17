package com.tunombre.crafteos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Items prohibidos: es como si el item no existiera. No sale en cofres ni drops (modificador de botin global), no se
 * puede craftear, se borra de inventarios, contenedores que se abren y entidades item, y se oculta del inventario creativo.
 * Se prohibe por id (minecraft:diamond) o por mod entero (modid:*).
 */
public final class Bans {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> banned = ConcurrentHashMap.newKeySet();

    private Bans() { }

    static Path file() { return FMLPaths.CONFIGDIR.get().resolve("crafteos").resolve("prohibidos.json"); }

    public static Set<String> all() { return new TreeSet<>(banned); }
    public static int size() { return banned.size(); }
    public static boolean has(String key) { return banned.contains(key); }

    static synchronized void load() {
        banned.clear();
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            if (o != null && o.has("prohibidos")) for (JsonElement e : o.getAsJsonArray("prohibidos")) banned.add(e.getAsString());
        } catch (Exception e) { System.err.println("[crafteos] No se pudo leer " + f + ": " + e); }
    }

    static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            for (String s : new TreeSet<>(banned)) a.add(s);
            o.add("prohibidos", a);
            try (Writer w = Files.newBufferedWriter(f)) { GSON.toJson(o, w); }
        } catch (Exception e) { System.err.println("[crafteos] No se pudo guardar " + f + ": " + e); }
    }

    public static boolean set(String key, boolean ban) {
        boolean ch = ban ? banned.add(key) : banned.remove(key);
        if (ch) save();
        return ch;
    }

    public static boolean isBannedId(ResourceLocation id) {
        if (id == null || banned.isEmpty()) return false;
        return banned.contains(id.toString()) || banned.contains(id.getNamespace() + ":*");
    }

    public static boolean isBannedId(String id) {
        if (id == null || id.isEmpty() || banned.isEmpty()) return false;
        return isBannedId(ResourceLocation.tryParse(id));
    }

    public static boolean isBanned(Item item) { return !banned.isEmpty() && isBannedId(ForgeRegistries.ITEMS.getKey(item)); }

    public static boolean isBanned(ItemStack s) { return !s.isEmpty() && isBanned(s.getItem()); }

    // ------------------------------------------------------------------ limpieza ----
    private static int sweepPlayer(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isBanned(inv.getItem(i))) { inv.setItem(i, ItemStack.EMPTY); n++; }
        if (isBanned(p.containerMenu.getCarried())) { p.containerMenu.setCarried(ItemStack.EMPTY); n++; }
        return n;
    }

    /** aplica la lista actual al mundo: inventarios online, items tirados y recetas; avisa a los clientes */
    public static void changed(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (sweepPlayer(p) > 0) p.containerMenu.broadcastChanges();
        for (ServerLevel lv : s.getAllLevels()) {
            List<ItemEntity> kill = new ArrayList<>();
            for (Entity en : lv.getAllEntities()) if (en instanceof ItemEntity ie && isBanned(ie.getItem())) kill.add(ie);
            for (ItemEntity ie : kill) ie.discard();
        }
        Recipes.change(s);
        Net.sendBanned(s);
    }

    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && e.getEntity() instanceof ItemEntity ie && isBanned(ie.getItem())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || banned.isEmpty() || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        sweepPlayer(p);
    }

    @SubscribeEvent
    public static void onOpen(PlayerContainerEvent.Open e) {
        if (banned.isEmpty() || e.getEntity().level().isClientSide) return;
        for (Slot sl : e.getContainer().slots) if (sl.hasItem() && isBanned(sl.getItem())) sl.set(ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Net.sendBanned(p);
    }
}
