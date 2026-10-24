package com.tunombre.intro;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Ejecuta una escena (config/intro/<nombre>.json) paso a paso sobre un grupo de jugadores. Una escena a la vez.
 * Los pasos de pantalla (fundido, barras, camara, subtitulo, temblor, bloqueo) viajan al cliente; teletransportar, tumbar
 * y lanzar comandos pasan en el servidor.
 */
public final class Director {
    private static final class Run {
        String name;
        JsonArray steps;
        final List<UUID> players = new ArrayList<>();
        int idx;
        int wait;
    }

    private static Run run;
    private static final Random RND = new Random();

    private Director() { }

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("intro"); }

    // ------------------------------------------------------------------ comandos ----
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("intro").requires(s -> s.hasPermission(2))
                .then(Commands.literal("play").then(Commands.argument("escena", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(scenes(), b))
                        .executes(c -> play(c.getSource(), StringArgumentType.getString(c, "escena"), c.getSource().getServer().getPlayerList().getPlayers()))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .executes(c -> play(c.getSource(), StringArgumentType.getString(c, "escena"), EntityArgument.getPlayers(c, "jugadores"))))))
                .then(Commands.literal("stop").executes(c -> {
                    boolean was = run != null;
                    stop(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal(was ? "Escena detenida" : "No hay ninguna escena en marcha"), true);
                    return 1;
                }))
                .then(Commands.literal("bloquear").then(Commands.argument("jugadores", EntityArgument.players())
                        .then(Commands.literal("on").executes(c -> lock(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), true)))
                        .then(Commands.literal("off").executes(c -> lock(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), false)))))
                .then(Commands.literal("congelar").then(Commands.argument("jugadores", EntityArgument.players())
                        .executes(c -> freeze(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), "death_emote"))
                        .then(Commands.argument("emote", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(emotes(), b))
                                .executes(c -> freeze(c.getSource(), EntityArgument.getPlayers(c, "jugadores"), StringArgumentType.getString(c, "emote"))))))
                .then(Commands.literal("descongelar").then(Commands.argument("jugadores", EntityArgument.players())
                        .executes(c -> unfreeze(c.getSource(), EntityArgument.getPlayers(c, "jugadores")))))
                .then(Commands.literal("lista").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Escenas (config/intro): " + String.join(", ", scenes())), false);
                    return 1;
                }))
                .then(Commands.literal("pos").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String s = String.format(Locale.ROOT, "\"x\": %.1f, \"y\": %.1f, \"z\": %.1f, \"yaw\": %.0f, \"pitch\": %.0f", p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot());
                    c.getSource().sendSuccess(() -> Component.literal("Tu vista (para camara/tp): " + s), false);
                    return 1;
                })));
    }

    private static int lock(CommandSourceStack src, Collection<ServerPlayer> ps, boolean on) {
        for (ServerPlayer p : ps) {
            if (on) LOCKED.add(p.getUUID()); else LOCKED.remove(p.getUUID());
            Net.send(p, "{\"op\":\"bloquear\",\"on\":" + on + "}");
        }
        src.sendSuccess(() -> Component.literal((on ? "Bloqueados " : "Liberados ") + ps.size() + " jugador(es) (no rompen, ni ponen, ni se mueven)"), true);
        return ps.size();
    }

    private static boolean ef() { return ModList.get().isLoaded("epicfight"); }

    private static List<String> emotes() { return ef() ? Emote.names() : List.of(); }

    /** bloquea al jugador (no se mueve, no rompe, no interactua, no abre menus) y lo deja en un emote de Epic Fight : Dancing */
    private static int freeze(CommandSourceStack src, Collection<ServerPlayer> ps, String emote) {
        if (!ef()) { src.sendFailure(Component.literal("Epic Fight no esta cargado")); return 0; }
        var id = Emote.resolve(emote);
        if (id == null) { src.sendFailure(Component.literal("Emote desconocido '" + emote + "'")); return 0; }
        int ok = 0;
        for (ServerPlayer p : ps) {
            LOCKED.add(p.getUUID());
            Net.send(p, "{\"op\":\"bloquear\",\"on\":true}");
            if (Emote.start(p, id)) ok++;
        }
        int n = ok;
        src.sendSuccess(() -> Component.literal("Congelados " + n + "/" + ps.size() + " en " + id), true);
        return ok;
    }

    private static int unfreeze(CommandSourceStack src, Collection<ServerPlayer> ps) {
        for (ServerPlayer p : ps) {
            if (ef()) Emote.stop(p);
            LOCKED.remove(p.getUUID());
            Net.send(p, "{\"op\":\"bloquear\",\"on\":false}");
        }
        src.sendSuccess(() -> Component.literal("Liberados " + ps.size() + " jugador(es)"), true);
        return ps.size();
    }

    private static List<String> scenes() {
        List<String> l = new ArrayList<>();
        try (Stream<Path> s = Files.isDirectory(dir()) ? Files.list(dir()) : Stream.<Path>empty()) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(n -> l.add(n.substring(0, n.length() - 5)));
        } catch (IOException ignored) { }
        return l;
    }

    private static int play(CommandSourceStack src, String name, Collection<ServerPlayer> players) {
        MinecraftServer server = src.getServer();
        ensureExample();
        Path f = dir().resolve(name + ".json");
        if (!Files.isRegularFile(f)) {
            src.sendFailure(Component.literal("No existe la escena '" + name + "' (mira /intro lista)"));
            return 0;
        }
        JsonArray steps;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            steps = o.getAsJsonArray("pasos");
        } catch (IOException | RuntimeException ex) {
            src.sendFailure(Component.literal("Error leyendo la escena: " + ex.getMessage()));
            return 0;
        }
        if (run != null) stop(server);
        Run r = new Run();
        r.name = name;
        r.steps = steps;
        for (ServerPlayer p : players) r.players.add(p.getUUID());
        run = r;
        src.sendSuccess(() -> Component.literal("Escena '" + name + "' para " + r.players.size() + " jugador(es), " + steps.size() + " pasos"), true);
        return r.players.size();
    }

    // ------------------------------------------------------------------ ejecucion ----
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (ef()) Emote.tick(e.getServer());
        if (run == null) return;
        Run r = run;
        MinecraftServer server = e.getServer();
        if (r.wait > 0) { r.wait--; return; }
        int guard = 0;
        while (run == r && r.wait <= 0 && guard++ < 200) {
            if (r.idx >= r.steps.size()) { stop(server); return; }
            JsonObject s = r.steps.get(r.idx++).getAsJsonObject();
            try {
                step(server, r, s);
            } catch (RuntimeException ex) {
                Intro_LOG("Paso " + r.idx + " de '" + r.name + "' fallo: " + ex);
            }
        }
    }

    private static void Intro_LOG(String m) { com.mojang.logging.LogUtils.getLogger().warn("[intro] {}", m); }

    private static int num(JsonObject s, String k, int def) { return s.has(k) ? s.get(k).getAsInt() : def; }

    private static double dbl(JsonObject s, String k, double def) { return s.has(k) ? s.get(k).getAsDouble() : def; }

    private static boolean flag(JsonObject s, String k, boolean def) { return s.has(k) ? s.get(k).getAsBoolean() : def; }

    private static List<ServerPlayer> online(MinecraftServer server, Run r) {
        List<ServerPlayer> l = new ArrayList<>();
        for (UUID id : r.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) l.add(p);
        }
        return l;
    }

    private static void toClients(MinecraftServer server, Run r, JsonObject s) {
        String json = s.toString();
        for (ServerPlayer p : online(server, r)) Net.send(p, json);
    }

    private static void step(MinecraftServer server, Run r, JsonObject s) {
        String op = s.get("op").getAsString().toLowerCase(Locale.ROOT);
        switch (op) {
            case "esperar" -> r.wait = num(s, "ticks", 20);
            case "fundido" -> {
                toClients(server, r, s);
                if (flag(s, "espera", true)) r.wait = num(s, "ticks", 20);
            }
            case "barras", "bloquear", "camara_fin", "temblor" -> toClients(server, r, s);
            case "camara" -> {
                toClients(server, r, s);
                if (flag(s, "espera", true)) {
                    int total = 0;
                    if (s.has("puntos")) for (JsonElement p : s.getAsJsonArray("puntos")) total += p.getAsJsonObject().has("ticks") ? p.getAsJsonObject().get("ticks").getAsInt() : 0;
                    r.wait = total;
                }
            }
            case "subtitulo" -> {
                toClients(server, r, s);
                if (s.has("sonido")) sound(server, r, s.get("sonido").getAsString(), (float) dbl(s, "volumen", 1), (float) dbl(s, "tono", 1));
                if (flag(s, "espera", true)) r.wait = num(s, "ticks", 60);
            }
            case "tp" -> {
                double radio = dbl(s, "radio", 0);
                for (ServerPlayer p : online(server, r)) {
                    double ox = radio > 0 ? (RND.nextDouble() * 2 - 1) * radio : 0, oz = radio > 0 ? (RND.nextDouble() * 2 - 1) * radio : 0;
                    p.teleportTo(p.serverLevel(), dbl(s, "x", p.getX()) + ox, dbl(s, "y", p.getY()), dbl(s, "z", p.getZ()) + oz,
                            (float) dbl(s, "yaw", p.getYRot()), (float) dbl(s, "pitch", p.getXRot()));
                }
            }
            case "sonido" -> sound(server, r, s.get("id").getAsString(), (float) dbl(s, "volumen", 1), (float) dbl(s, "tono", 1));
            case "bloqueo_servidor" -> {
                boolean on = flag(s, "on", true);
                for (UUID id : r.players) { if (on) LOCKED.add(id); else LOCKED.remove(id); }
            }
            case "congelar" -> {
                String em = s.has("emote") ? s.get("emote").getAsString() : "death_emote";
                var id = ef() ? Emote.resolve(em) : null;
                for (ServerPlayer p : online(server, r)) {
                    LOCKED.add(p.getUUID());
                    Net.send(p, "{\"op\":\"bloquear\",\"on\":true}");
                    if (id != null) Emote.start(p, id);
                    else p.setForcedPose(Pose.SWIMMING);   // sin Epic Fight: queda tumbado de la forma vanilla
                }
            }
            case "descongelar" -> {
                for (ServerPlayer p : online(server, r)) {
                    if (ef()) Emote.stop(p);
                    p.setForcedPose(null);
                    LOCKED.remove(p.getUUID());
                    Net.send(p, "{\"op\":\"bloquear\",\"on\":false}");
                }
            }
            case "tumbar" -> {
                boolean on = flag(s, "on", true);
                for (ServerPlayer p : online(server, r)) p.setForcedPose(on ? Pose.SWIMMING : null);
            }
            case "cmd" -> {
                String cmd = s.get("cmd").getAsString();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                if (flag(s, "por_jugador", false)) {
                    for (ServerPlayer p : online(server, r)) run(server, cmd.replace("%jugador%", p.getGameProfile().getName()));
                } else {
                    run(server, cmd);
                }
            }
            default -> Intro_LOG("Paso desconocido '" + op + "' en '" + r.name + "'");
        }
    }

    private static void sound(MinecraftServer server, Run r, String id, float vol, float pitch) {
        var ev = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(new net.minecraft.resources.ResourceLocation(id));
        if (ev == null) return;
        for (ServerPlayer p : online(server, r)) p.playNotifySound(ev, net.minecraft.sounds.SoundSource.MASTER, vol, pitch);
    }

    /** jugadores a los que el servidor no deja romper, poner ni interactuar (la carcel) */
    public static final java.util.Set<UUID> LOCKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean locked(net.minecraft.world.entity.Entity e) { return e instanceof ServerPlayer p && LOCKED.contains(p.getUUID()); }

    @SubscribeEvent
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent e) { if (locked(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent
    public static void onPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e) { if (locked(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent e) { if (e.isCancelable() && locked(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent e) { if (locked(e.getEntity())) e.setCanceled(true); }

    private static void run(MinecraftServer server, String cmd) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), cmd);
    }

    /** termina la escena: todo vuelve a la normalidad para los jugadores */
    static void stop(MinecraftServer server) {
        Run r = run;
        run = null;
        if (r == null) return;
        for (ServerPlayer p : online(server, r)) clean(p);
    }

    private static void clean(ServerPlayer p) {
        LOCKED.remove(p.getUUID());
        p.setForcedPose(null);
        if (ef()) Emote.stop(p);
        Net.send(p, "{\"op\":\"reset\"}");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && LOCKED.contains(p.getUUID())) Net.send(p, "{\"op\":\"bloquear\",\"on\":true}");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) p.setForcedPose(null);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) { run = null; }

    // ------------------------------------------------------------------ escena de ejemplo ----
    private static void ensureExample() {
        try {
            Files.createDirectories(dir());
            Path f = dir().resolve("inicio.json");
            if (!Files.exists(f)) {
                try (var in = Director.class.getResourceAsStream("/inicio.json")) {
                    if (in != null) Files.write(f, in.readAllBytes());
                }
            }
        } catch (IOException ignored) { }
    }
}
