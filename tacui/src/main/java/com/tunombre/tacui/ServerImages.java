package com.tunombre.tacui;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * Lado servidor del sistema de imagenes: /tacshow (solo operadores, nivel 2).
 * Las imagenes (PNG o GIF) se ponen en config/tacui/images/ del servidor; se envian al cliente la primera vez.
 */
@Mod.EventBusSubscriber(modid = TacUI.ID)
public final class ServerImages {
    private ServerImages() {}

    private static final int CHUNK = 30000, MAX_BYTES = 8 * 1024 * 1024;
    private static final Map<UUID, Set<String>> SENT = new ConcurrentHashMap<>();
    private static final String[] MODES = {"center", "full", "corner"};

    static Path dir() {
        Path d = FMLPaths.CONFIGDIR.get().resolve("tacui").resolve("images");
        try {
            Files.createDirectories(d);
            Path readme = d.resolve("LEEME.txt");
            if (!Files.exists(readme)) Files.writeString(readme,
                    "Pon aqui imagenes PNG o GIF (max 8 MB) y usalas con /tacshow <jugadores> <archivo> [segundos] [modo] [sonido] [texto]\n" +
                    "Modos: full (pantalla completa, para sustos), center (aviso en el centro), corner (alerta en la esquina)\n" +
                    "Sonido: cualquier id de sonido (p. ej. minecraft:entity.ghast.scream) o none. En el texto se pueden usar colores con &c, &l...\n");
        } catch (Exception ignored) { }
        return d;
    }

    static List<String> files() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.map(p -> p.getFileName().toString()).filter(n -> {
                String l = n.toLowerCase(Locale.ROOT);
                return l.endsWith(".png") || l.endsWith(".gif");
            }).sorted().forEach(out::add);
        } catch (Exception ignored) { }
        return out;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SENT.remove(e.getEntity().getUUID()); }

    private static int alertColor(String c) {
        return switch (c.toLowerCase(Locale.ROOT)) {
            case "rojo", "red" -> 0xFF2A2A;
            case "violeta", "morado", "purple" -> 0xB05CFF;
            case "verde", "green" -> 0x3CFF6A;
            case "azul", "blue" -> 0x3C8CFF;
            case "ambar", "amarillo", "naranja", "amber" -> 0xFFA020;
            case "blanco", "white" -> 0xFFFFFF;
            default -> {
                try { yield Integer.parseInt(c.startsWith("#") ? c.substring(1) : c, 16) & 0xFFFFFF; } catch (NumberFormatException ex) { yield 0xFF2A2A; }
            }
        };
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        // /tacalert <jugadores> <segundos> <color> <TITULO|subtitulo>: alerta glitch de texto, sin imagen
        e.getDispatcher().register(Commands.literal("tacalert").requires(s -> s.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.5f, 120f))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("rojo", "violeta", "verde", "azul", "ambar", "blanco"), b))
                                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                                            int rgb = alertColor(StringArgumentType.getString(c, "color"));
                                            TacNet.Alert al = new TacNet.Alert(StringArgumentType.getString(c, "text"), FloatArgumentType.getFloat(c, "seconds"), rgb);
                                            int n = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                                                if (!TacNet.CH.isRemotePresent(p.connection.connection)) continue;
                                                TacNet.CH.send(PacketDistributor.PLAYER.with(() -> p), al);
                                                n++;
                                            }
                                            return n;
                                        }))))));
        e.getDispatcher().register(Commands.literal("tacshow").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> {
                    List<String> f = files();
                    c.getSource().sendSuccess(() -> Component.literal(f.isEmpty() ? "No hay imagenes en config/tacui/images/" : "Imagenes: " + String.join(", ", f)), false);
                    return f.size();
                }))
                .then(Commands.literal("clear").then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
                    for (ServerPlayer p : ps) if (TacNet.CH.isRemotePresent(p.connection.connection))
                        TacNet.CH.send(PacketDistributor.PLAYER.with(() -> p), new TacNet.ClearImage((byte) 0));
                    return ps.size();
                })))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("image", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(files(), b))
                                .executes(c -> run(c, 4f, "center", "", ""))
                                .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.3f, 120f))
                                        .executes(c -> run(c, FloatArgumentType.getFloat(c, "seconds"), "center", "", ""))
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MODES, b))
                                                .executes(c -> run(c, FloatArgumentType.getFloat(c, "seconds"), StringArgumentType.getString(c, "mode"), "", ""))
                                                .then(Commands.argument("sound", ResourceLocationArgument.id())
                                                        .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                                                        .executes(c -> run(c, FloatArgumentType.getFloat(c, "seconds"), StringArgumentType.getString(c, "mode"),
                                                                sound(c), ""))
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(c -> run(c, FloatArgumentType.getFloat(c, "seconds"), StringArgumentType.getString(c, "mode"),
                                                                        sound(c), StringArgumentType.getString(c, "text"))))))))));
    }

    private static String sound(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "sound");
        return id.getPath().equals("none") ? "" : id.toString();
    }

    private static String mode(String m) {
        String l = m.toLowerCase(Locale.ROOT);
        if (l.startsWith("p_")) return l;
        return switch (l) {
            case "full", "screamer", "susto", "completa" -> "full";
            case "corner", "aviso", "alerta", "esquina" -> "corner";
            default -> "center";
        };
    }

    private static int run(CommandContext<CommandSourceStack> c, float secs, String mode, String sound, String text) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        String name = StringArgumentType.getString(c, "image");
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            src.sendFailure(Component.literal("Nombre de archivo no valido"));
            return 0;
        }
        Path file = dir().resolve(name);
        byte[] data;
        try {
            if (!Files.isRegularFile(file)) { src.sendFailure(Component.literal("No existe config/tacui/images/" + name + " (usa /tacshow list)")); return 0; }
            if (Files.size(file) > MAX_BYTES) { src.sendFailure(Component.literal("La imagen pesa mas de 8 MB")); return 0; }
            data = Files.readAllBytes(file);
        } catch (Exception ex) {
            src.sendFailure(Component.literal("No se pudo leer la imagen: " + ex.getMessage()));
            return 0;
        }
        CRC32 crc = new CRC32();
        crc.update(data);
        String key = name.toLowerCase(Locale.ROOT) + "#" + Long.toHexString(crc.getValue());
        String m = mode(mode);
        int total = (data.length + CHUNK - 1) / CHUNK, ok = 0, missing = 0;
        for (ServerPlayer p : targets) {
            if (!TacNet.CH.isRemotePresent(p.connection.connection)) { missing++; continue; }
            PacketDistributor.PacketTarget to = PacketDistributor.PLAYER.with(() -> p);
            Set<String> sent = SENT.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
            if (!sent.contains(key)) {
                for (int i = 0; i < total; i++) {
                    int from = i * CHUNK, len = Math.min(CHUNK, data.length - from);
                    byte[] part = new byte[len];
                    System.arraycopy(data, from, part, 0, len);
                    TacNet.CH.send(to, new TacNet.ImageData(key, i, total, part));
                }
                sent.add(key);
            }
            TacNet.CH.send(to, new TacNet.ShowImage(key, secs, m, sound, 1.0f, text));
            ok++;
        }
        int fok = ok, fmiss = missing;
        src.sendSuccess(() -> Component.literal("Imagen " + name + " (" + m + ", " + secs + " s) enviada a " + fok + " jugador(es)"
                + (fmiss > 0 ? "; " + fmiss + " sin tacui instalado" : "")), true);
        return ok;
    }
}
