package com.tunombre.obscurearcade.client;

import com.cotii.cubosu.client.media.OsuImporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** /fnf lista | /fnf convertir <cancion>: lee config/obscurearcade/fnf/<cancion>/ y deja un .osz en la carpeta de importacion de CubOsu!. */
public final class FnfCommand {
    private FnfCommand() { }

    static Path root() { return FMLPaths.CONFIGDIR.get().resolve("obscurearcade").resolve("fnf"); }

    static List<String> songs() {
        List<String> l = new ArrayList<>();
        try {
            Files.createDirectories(root());
            try (Stream<Path> s = Files.list(root())) {
                s.filter(Files::isDirectory).forEach(p -> l.add(p.getFileName().toString()));
            }
        } catch (IOException ignored) { }
        return l;
    }

    private static void say(String m) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(Component.literal(m), false); });
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("fnf")
                .executes(c -> { help(); return 1; })
                .then(Commands.literal("lista").executes(c -> {
                    List<String> l = songs();
                    say("Carpeta: " + root() + "\nCanciones: " + (l.isEmpty() ? "(ninguna, crea una carpeta por canción con el chart .json, Inst.ogg y Voices.ogg)" : String.join(", ", l)));
                    return 1;
                }))
                .then(Commands.literal("convertir").then(Commands.argument("cancion", StringArgumentType.greedyString())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(songs(), b))
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "cancion").trim();
                            Path dir = root().resolve(n).normalize();
                            if (!dir.startsWith(root()) || !Files.isDirectory(dir)) { say("No existe la carpeta " + dir); return 0; }
                            say("Convirtiendo «" + n + "»...");
                            Thread t = new Thread(() -> {
                                try {
                                    String r = FnfConverter.convert(dir, OsuImporter.importFolder());
                                    say("Listo: " + r + ". Abre CubOsu! para importarlo.");
                                } catch (IOException | RuntimeException e) {
                                    say("Error al convertir: " + e.getMessage());
                                }
                            }, "fnf-convert");
                            t.setDaemon(true);
                            t.start();
                            return 1;
                        }))));
    }

    private static void help() {
        say("/fnf lista — canciones disponibles\n/fnf convertir <canción> — crea el .osz en la carpeta de importación de CubOsu!\nCarpeta: " + root());
    }
}
