package com.tunombre.spawncontrol;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Aviso al liberar un mob: si esta tacui instalado ejecuta /tacshow @a <imagen> ... ; si no, o si la regla solo tiene texto,
 * muestra un titulo en pantalla a todos los jugadores.
 */
public final class Announce {
    private Announce() { }

    public static boolean tacuiPresent(MinecraftServer s) {
        return s.getCommands().getDispatcher().getRoot().getChild("tacshow") != null;
    }

    static Component nameOf(String key) {
        if (key.equals("*")) return Component.literal("todos los mobs");
        if (key.endsWith(":*")) return Component.literal("mobs de " + key.substring(0, key.length() - 2));
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id)) return ForgeRegistries.ENTITY_TYPES.getValue(id).getDescription();
        return Component.literal(key);
    }

    /** devuelve una descripcion de lo que hizo (para el comando de prueba) */
    public static String fire(MinecraftServer s, String key, Rule r) {
        boolean shown = false;
        String how = "";
        if (!r.aImage.isEmpty() && tacuiPresent(s)) {
            String sound = r.aSound.isEmpty() ? "none" : r.aSound;
            StringBuilder cmd = new StringBuilder("tacshow @a ").append(r.aImage).append(' ').append(r.aSecs).append(' ').append(r.aMode).append(' ').append(sound);
            if (!r.aText.isEmpty()) cmd.append(' ').append(r.aText);
            try {
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), cmd.toString());
                shown = true;
                how = "imagen " + r.aImage + " con tacui";
            } catch (Exception e) {
                SpawnControl.LOG.error("Fallo el aviso con tacui", e);
            }
        }
        if (!shown && (!r.aText.isEmpty() || !r.aImage.isEmpty())) {
            Component title = r.aText.isEmpty() ? Component.literal("Se libera: ").append(nameOf(key)) : Component.literal(r.aText);
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, (int) (r.aSecs * 20), 20));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
            }
            how = r.aImage.isEmpty() ? "titulo en pantalla" : "titulo en pantalla (tacui no esta instalado)";
        }
        return how;
    }
}
