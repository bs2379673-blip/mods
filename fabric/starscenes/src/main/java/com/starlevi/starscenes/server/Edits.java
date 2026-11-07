package com.starlevi.starscenes.server;

import com.starlevi.starscenes.entity.NpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Los cambios a un NPC, iguales desde los comandos y desde el menu. */
public final class Edits {
    private Edits() {
    }

    /** El NPC mas cercano al jugador (hasta tantos bloques), o null. */
    public static NpcEntity nearest(ServerPlayer p, double range) {
        List<NpcEntity> list = p.level().getEntitiesOfClass(NpcEntity.class, new AABB(p.blockPosition()).inflate(range));
        return list.stream().min(Comparator.comparingDouble(n -> n.distanceToSqr(p))).orElse(null);
    }

    public static void fromMenu(ServerPlayer p, int entityId, String action, String value) {
        if (!p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof NpcEntity npc) || npc.distanceToSqr(p) > 32 * 32) {
            return;
        }
        String msg = apply(p, npc, action, value);
        if (msg != null) {
            p.displayClientMessage(Component.literal(msg), true);
        }
    }

    /** Aplica un cambio y devuelve que paso (para mostrarlo). */
    public static String apply(ServerPlayer p, NpcEntity npc, String action, String value) {
        String v = value == null ? "" : value.trim();
        switch (action) {
            case "modelo": {
                String id = SceneLibrary.cleanId(v);
                if (SceneLibrary.info(id) == null) {
                    return "No existe el modelo " + v + " (mirá /starscenes lista)";
                }
                npc.setModel(id);
                return "Modelo: " + id;
            }
            case "skin": {
                String id = v.isEmpty() || v.equals("-") ? "" : SceneLibrary.cleanId(v);
                if (!id.isEmpty() && !SceneLibrary.skins().contains(id)) {
                    return "No existe la skin " + v + " (mirá /starscenes lista)";
                }
                npc.set(NpcEntity.SKIN, id);
                return id.isEmpty() ? "Skin: la del modelo" : "Skin: " + id;
            }
            case "nombre": {
                if (v.isEmpty() || v.equals("-")) {
                    npc.setCustomName(null);
                    npc.setCustomNameVisible(false);
                    return "Sin nombre";
                }
                npc.setCustomName(Component.literal(v.replace('&', '§')));
                npc.setCustomNameVisible(true);
                return "Nombre: " + v;
            }
            case "estilo": {
                int s = parseInt(v, 1);
                npc.set(NpcEntity.STYLE, Math.max(0, s));
                return "Estilo de animaciones: " + Math.max(0, s);
            }
            case "escala": {
                float s = Math.max(0.1f, Math.min(10f, parseFloat(v, 1)));
                npc.set(NpcEntity.SCALE, s);
                return "Tamaño: " + s;
            }
            case "mirar": {
                boolean b = v.equals("si") || v.equals("true") || v.equals("1");
                npc.set(NpcEntity.LOOK, b);
                return b ? "Mira a los jugadores" : "No mira a los jugadores";
            }
            case "radio": {
                float r = Math.max(0.5f, Math.min(64f, parseFloat(v, 5)));
                npc.set(NpcEntity.RADIUS, r);
                return "Reacciona a " + r + " bloques";
            }
            case "mover": {
                String[] parts = v.split(" ");
                float amount = parts.length > 1 ? parseFloat(parts[1], 0.1f) : 0.1f;
                double yaw = Math.toRadians(p.getYRot());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);
                double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
                double dx = 0, dy = 0, dz = 0;
                switch (parts[0].toLowerCase(Locale.ROOT)) {
                    case "adelante", "front" -> {
                        dx = fx;
                        dz = fz;
                    }
                    case "atras", "back" -> {
                        dx = -fx;
                        dz = -fz;
                    }
                    case "derecha", "right" -> {
                        dx = rx;
                        dz = rz;
                    }
                    case "izquierda", "left" -> {
                        dx = -rx;
                        dz = -rz;
                    }
                    case "arriba", "up" -> dy = 1;
                    case "abajo", "down" -> dy = -1;
                    default -> {
                        return "Dirección: adelante, atras, izquierda, derecha, arriba o abajo";
                    }
                }
                // en el eje que mas se parece, para que quede alineado
                if (Math.abs(dx) > Math.abs(dz)) {
                    dx = Math.signum(dx);
                    dz = 0;
                } else if (dz != 0) {
                    dz = Math.signum(dz);
                    dx = 0;
                }
                npc.snapTo(npc.getX() + dx * amount, npc.getY() + dy * amount, npc.getZ() + dz * amount, npc.getYRot(), 0);
                return String.format(Locale.ROOT, "Movido a %.2f %.2f %.2f", npc.getX(), npc.getY(), npc.getZ());
            }
            case "girar": {
                float yaw = npc.getYRot() + parseFloat(v, 15);
                npc.snapTo(npc.getX(), npc.getY(), npc.getZ(), yaw, 0);
                return "Mira hacia " + Math.round(((yaw % 360) + 360) % 360) + "°";
            }
            case "mirarme": {
                double dx = p.getX() - npc.getX(), dz = p.getZ() - npc.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                npc.snapTo(npc.getX(), npc.getY(), npc.getZ(), yaw, 0);
                return "Ahora te mira";
            }
            case "item": {
                ItemStack held = p.getMainHandItem().copyWithCount(1);
                switch (v) {
                    case "mano" -> npc.set(NpcEntity.MAIN, held);
                    case "otra_mano" -> npc.set(NpcEntity.OFF, held);
                    case "cabeza" -> npc.set(NpcEntity.HEAD, held);
                    default -> {
                        return "Lugar: mano, otra_mano o cabeza";
                    }
                }
                return held.isEmpty() ? "Sacado el ítem de " + v : "Ítem en " + v + ": " + held.getHoverName().getString();
            }
            case "dialogo": {
                String id = v.isEmpty() || v.equals("-") ? "" : SceneLibrary.cleanId(v);
                if (!id.isEmpty() && !com.starlevi.starscenes.talk.Dialogues.exists(id)) {
                    return "No existe el diálogo " + v + " (están en config/starscenes/dialogos)";
                }
                npc.setDialogue(id);
                return id.isEmpty() ? "Sin diálogo" : "Diálogo: " + id;
            }
            case "voz": {
                String vz = v.toLowerCase(Locale.ROOT);
                if (!com.starlevi.starscenes.talk.Voices.ALL.contains(vz)) {
                    return "Voces: " + String.join(", ", com.starlevi.starscenes.talk.Voices.ALL);
                }
                npc.set(NpcEntity.VOICE, vz);
                return "Voz: " + vz;
            }
            case "tono": {
                float t = Math.max(0.5f, Math.min(2f, parseFloat(v, 1)));
                npc.set(NpcEntity.VOICE_PITCH, t);
                return String.format(Locale.ROOT, "Tono de voz: %.2f", t);
            }
            case "animar": {
                com.starlevi.starscenes.api.StarScenesAPI.play(npc, v);
                return null;
            }
            case "probar": {
                if (npc.dialogue().isEmpty()) {
                    return "No tiene diálogo";
                }
                com.starlevi.starscenes.talk.Dialogues.open(p, npc, npc.dialogue());
                return null;
            }
            case "quitar": {
                npc.discard();
                return "NPC quitado";
            }
            default:
                return "No conozco " + action;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
