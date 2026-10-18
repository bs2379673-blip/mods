package com.tunombre.concierto;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Los GIF y stickers del usuario salen como aviso en pantalla con el mod tacui (/tacshow).
 * Al arrancar el servidor se copian a config/tacui/images/ (concierto_<nombre>); el guion los lanza con la orden "aviso".
 */
@Mod.EventBusSubscriber(modid = Concierto.MODID)
public final class Avisos {
    private Avisos() { }

    private static final String PREFIX = "concierto_";

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("tacui").resolve("images");
            Files.createDirectories(dir);
            String idx;
            try (InputStream in = Avisos.class.getResourceAsStream("/assets/concierto/avisos/index.txt")) {
                if (in == null) return;
                idx = new String(in.readAllBytes());
            }
            for (String n : idx.split("\\R")) {
                n = n.trim();
                if (n.isEmpty()) continue;
                try (InputStream in = Avisos.class.getResourceAsStream("/assets/concierto/avisos/" + n)) {
                    if (in == null) continue;
                    byte[] data = in.readAllBytes();
                    Path out = dir.resolve(PREFIX + n);
                    if (!Files.exists(out) || Files.size(out) != data.length) Files.write(out, data);
                }
            }
        } catch (Exception ex) {
            Concierto.LOGGER.warn("concierto: no se pudieron copiar los avisos a config/tacui/images: {}", ex.toString());
        }
    }

    /** orden "aviso" del guion: id, ext, dur, modo -> /tacshow @a concierto_<id>.<ext> <dur> <modo> none */
    public static void show(MinecraftServer srv, JsonObject o) {
        if (!ModList.get().isLoaded("tacui")) return;
        if (!o.has("id")) return;
        String id = o.get("id").getAsString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        String ext = o.has("ext") ? o.get("ext").getAsString() : "png";
        float dur = o.has("dur") ? o.get("dur").getAsFloat() : 4F;
        String modo = o.has("modo") ? o.get("modo").getAsString() : "corner";
        String cmd = String.format(Locale.ROOT, "tacshow @a %s%s.%s %.2f %s none", PREFIX, id, ext.equals("gif") ? "gif" : "png", dur, modo);
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), cmd);
    }
}
