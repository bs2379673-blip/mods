package com.starlevi.starscenes.talk;

import com.starlevi.starscenes.StarScenes;
import com.starlevi.starscenes.api.StarScenesAPI;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las acciones ("tipo:valor") y condiciones que usan los dialogos, las misiones y las tiendas. Otros
 * mods agregan las suyas con {@link StarScenesAPI#registerAction} y {@link StarScenesAPI#registerCondition}.
 */
public final class Actions {
    private static final Map<String, StarScenesAPI.Action> ACTIONS = new HashMap<>();
    private static final Map<String, StarScenesAPI.Condition> CONDITIONS = new HashMap<>();

    private Actions() {
    }

    public static void register(String type, StarScenesAPI.Action a) {
        ACTIONS.put(type.toLowerCase(Locale.ROOT), a);
    }

    public static void registerCondition(String type, StarScenesAPI.Condition c) {
        CONDITIONS.put(type.toLowerCase(Locale.ROOT), c);
    }

    /** Reemplaza %jugador% y %npc%, y los colores con &amp;. */
    public static String fill(String s, ServerPlayer p, Entity speaker) {
        String r = s.replace("%jugador%", p.getName().getString()).replace("%player%", p.getName().getString());
        String npc = speaker == null ? "" : speaker.getDisplayName().getString();
        r = r.replace("%npc%", npc);
        return r.replace('&', '§');
    }

    public static void runAll(ServerPlayer p, Entity speaker, List<String> list) {
        for (String a : list) {
            run(p, speaker, a);
        }
    }

    public static void run(ServerPlayer p, Entity speaker, String action) {
        String a = action.trim();
        if (a.isEmpty()) {
            return;
        }
        int c = a.indexOf(':');
        String type = (c < 0 ? a : a.substring(0, c)).trim().toLowerCase(Locale.ROOT);
        String value = c < 0 ? "" : a.substring(c + 1).trim();
        StarScenesAPI.Action act = ACTIONS.get(type);
        if (act == null) {
            StarScenes.LOGGER.warn("StarScenes: no conozco la accion '{}'", action);
            return;
        }
        try {
            // en los comandos los % se completan ahi mismo
            act.run(p, speaker, type.equals("comando") ? value : fill(value, p, speaker));
        } catch (Exception e) {
            StarScenes.LOGGER.warn("StarScenes: fallo la accion '{}': {}", action, e.toString());
        }
    }

    public static boolean testAll(ServerPlayer p, Entity speaker, List<String> list) {
        for (String c : list) {
            if (!test(p, speaker, c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean test(ServerPlayer p, Entity speaker, String cond) {
        String s = cond.trim();
        boolean not = s.startsWith("!");
        if (not) {
            s = s.substring(1).trim();
        }
        int c = s.indexOf(':');
        String type = (c < 0 ? s : s.substring(0, c)).trim().toLowerCase(Locale.ROOT);
        String value = c < 0 ? "" : s.substring(c + 1).trim();
        StarScenesAPI.Condition t = CONDITIONS.get(type);
        if (t == null) {
            StarScenes.LOGGER.warn("StarScenes: no conozco la condicion '{}'", cond);
            return false;
        }
        boolean r;
        try {
            r = t.test(p, speaker, value);
        } catch (Exception e) {
            StarScenes.LOGGER.warn("StarScenes: fallo la condicion '{}': {}", cond, e.toString());
            r = false;
        }
        return not != r;
    }

    /** "amistad>=5", "oro<3", "nivel=2": compara un numero del jugador. */
    private static boolean compare(int have, String expr) {
        String[] ops = {">=", "<=", "!=", ">", "<", "="};
        for (String op : ops) {
            int i = expr.indexOf(op);
            if (i >= 0) {
                int want;
                try {
                    want = Integer.parseInt(expr.substring(i + op.length()).trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                return switch (op) {
                    case ">=" -> have >= want;
                    case "<=" -> have <= want;
                    case "!=" -> have != want;
                    case ">" -> have > want;
                    case "<" -> have < want;
                    default -> have == want;
                };
            }
        }
        return have != 0;
    }

    private static String varName(String expr) {
        return expr.split(">=|<=|!=|>|<|=")[0].trim();
    }

    private static String[] two(String v) {
        int i = v.lastIndexOf(' ');
        return i < 0 ? new String[]{v, "1"} : new String[]{v.substring(0, i).trim(), v.substring(i + 1).trim()};
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static {
        // dialogo
        register("ir", (p, s, v) -> Dialogues.go(p, v));
        register("cerrar", (p, s, v) -> Dialogues.close(p));
        register("dialogo", (p, s, v) -> Dialogues.open(p, s, v));
        // mundo
        register("comando", (p, s, v) -> {
            var server = p.level().getServer();
            String cmd = v.replace("%jugador%", p.getName().getString()).replace("%player%", p.getName().getString())
                    .replace("%npc%", s == null ? "" : s.getStringUUID());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(p.level()).withPosition(p.position())
                    .withEntity(p).withSuppressedOutput(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        });
        register("dar", (p, s, v) -> {
            ItemSpec it = ItemSpec.parse(v);
            if (it != null) {
                it.give(p);
            }
        });
        register("quitar", (p, s, v) -> {
            ItemSpec it = ItemSpec.parse(v);
            if (it != null) {
                it.take(p);
            }
        });
        register("xp", (p, s, v) -> p.giveExperiencePoints(parseInt(v, 0)));
        register("niveles", (p, s, v) -> p.giveExperienceLevels(parseInt(v, 0)));
        register("mensaje", (p, s, v) -> p.sendSystemMessage(Component.literal(v)));
        register("aviso", (p, s, v) -> p.displayClientMessage(Component.literal(v), true));
        register("animar", (p, s, v) -> {
            if (s != null) {
                StarScenesAPI.play(s, v);
            }
        });
        register("sonido", (p, s, v) -> {
            String[] parts = v.split(" ");
            Identifier id = Identifier.tryParse(parts[0]);
            if (id == null) {
                return;
            }
            float vol = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f, pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            Entity at = s != null ? s : p;
            p.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvent.createVariableRangeEvent(id)), SoundSource.NEUTRAL, at.getX(), at.getY(),
                    at.getZ(), vol, pitch, p.getRandom().nextLong()));
        });
        // datos del jugador
        register("marca", (p, s, v) -> PlayerStore.setFlag(p, v, true));
        register("desmarca", (p, s, v) -> PlayerStore.setFlag(p, v, false));
        register("sumar", (p, s, v) -> {
            String[] t = two(v);
            PlayerStore.setVar(p, t[0], PlayerStore.of(p).vars.getOrDefault(t[0], 0) + parseInt(t[1], 1));
        });
        register("poner", (p, s, v) -> {
            String[] t = two(v);
            PlayerStore.setVar(p, t[0], parseInt(t[1], 0));
        });
        // misiones y tiendas
        register("mision", (p, s, v) -> Quests.start(p, v));
        register("entregar", (p, s, v) -> Quests.deliver(p, s, v));
        register("abandonar", (p, s, v) -> Quests.abandon(p, v));
        register("tienda", (p, s, v) -> Shops.open(p, s, v));

        registerCondition("tiene", (p, s, v) -> {
            ItemSpec it = ItemSpec.parse(v);
            return it != null && it.enough(p);
        });
        registerCondition("marca", (p, s, v) -> PlayerStore.of(p).flags.contains(v));
        registerCondition("var", (p, s, v) -> compare(PlayerStore.of(p).vars.getOrDefault(varName(v), 0), v));
        registerCondition("mision_nueva", (p, s, v) -> Quests.state(p, v).equals("nueva"));
        registerCondition("mision_activa", (p, s, v) -> {
            String st = Quests.state(p, v);
            return st.equals("activa") || st.equals("lista");
        });
        registerCondition("mision_lista", (p, s, v) -> Quests.state(p, v).equals("lista"));
        registerCondition("mision_hecha", (p, s, v) -> PlayerStore.of(p).done.containsKey(v));
        registerCondition("op", (p, s, v) -> p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        registerCondition("dia", (p, s, v) -> p.level().isBrightOutside());
        registerCondition("noche", (p, s, v) -> !p.level().isBrightOutside());
        registerCondition("azar", (p, s, v) -> p.getRandom().nextDouble() < Double.parseDouble(v.replace(',', '.')));
        registerCondition("nivel", (p, s, v) -> compare(p.experienceLevel, "n" + (v.matches("^[<>=!].*") ? v : ">=" + v)));
    }
}
