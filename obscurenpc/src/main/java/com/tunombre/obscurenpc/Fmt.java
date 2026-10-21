package com.tunombre.obscurenpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Texto de dialogo: variables {jugador} {npc} {hora}..., etiquetas [gesto:x] [sonido:x] [pausa:n] [pone:f] y condiciones. */
public final class Fmt {
    private static final Pattern TAG = Pattern.compile("\\[(gesto|sonido|postura|pausa|pone|quita|cmd|gpone|gquita):([^\\]]*)\\]");
    private static boolean lastGesture;
    public static int lastPause;

    private Fmt() { }

    public static boolean hadGesture() { return lastGesture; }

    public static String hora(long dayTime) {
        long t = dayTime % 24000L;
        if (t < 6000) return "mañana";
        if (t < 12000) return "tarde";
        if (t < 13000) return "atardecer";
        if (t < 23000) return "noche";
        return "amanecer";
    }

    public static String process(Actor npc, String raw, ServerPlayer p) {
        lastGesture = false;
        lastPause = 0;
        if (raw == null) return "";
        String s = raw;
        Matcher m = TAG.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String k = m.group(1), v = m.group(2).trim();
            switch (k) {
                case "gesto" -> { npc.gesture(v); lastGesture = true; }
                case "sonido" -> npc.playCustom(v);
                case "postura" -> npc.setStance(Stances.byName(v));
                case "pausa" -> { try { lastPause += Integer.parseInt(v); } catch (NumberFormatException ignored) { } }
                case "pone" -> { if (p != null) npc.setFlag(p.getUUID(), v); }
                case "quita" -> { if (p != null) npc.clearFlag(p.getUUID(), v); }
                case "gpone" -> State.set(v);
                case "gquita" -> State.clear(v);
                case "cmd" -> runCommand(npc, v, p);
                default -> { }
            }
            m.appendReplacement(out, "");
        }
        m.appendTail(out);
        s = out.toString();
        boolean g = lastGesture;
        int pause = lastPause;
        s = vars(npc, s, p);
        lastGesture = g;
        lastPause = pause;
        return s.trim();
    }

    public static String vars(Actor npc, String s, ServerPlayer p) {
        if (s.indexOf('{') < 0) return s;
        long dt = npc.level().getDayTime();
        s = s.replace("{npc}", npc.data().nombre)
                .replace("{hora}", hora(dt))
                .replace("{dia}", String.valueOf(dt / 24000L + 1))
                .replace("{clima}", npc.level().isThundering() ? "tormenta" : npc.level().isRaining() ? "lluvia" : "cielo despejado")
                .replace("{bioma}", npc.level().getBiome(npc.blockPosition()).unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("este lugar"));
        if (p != null) {
            s = s.replace("{jugador}", p.getGameProfile().getName())
                    .replace("{vida}", String.valueOf((int) p.getHealth()))
                    .replace("{visitas}", String.valueOf(npc.counter(p.getUUID(), "visitas")));
        } else {
            s = s.replace("{jugador}", "forastero").replace("{vida}", "?").replace("{visitas}", "0");
        }
        return s;
    }

    public static void runCommand(Actor npc, String cmd, ServerPlayer p) {
        MinecraftServer server = npc.level().getServer();
        if (server == null) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        c = c.replace("%jugador%", p != null ? p.getGameProfile().getName() : "@p").replace("%npc%", npc.getUUID().toString());
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
                .withPosition(npc.position()).withLevel((net.minecraft.server.level.ServerLevel) npc.level()), c);
    }

    // ------------------------------------------------------------------ condiciones ----
    private static final Pattern CMP = Pattern.compile("^(\\w+)(>=|<=|==|!=|>|<)(-?\\d+)$");

    public static boolean test(Actor npc, ServerPlayer p, JsonElement si) {
        if (si == null || si.isJsonNull()) return true;
        if (si.isJsonPrimitive()) return one(npc, p, si.getAsString());
        if (si.isJsonArray()) {
            JsonArray a = si.getAsJsonArray();
            for (JsonElement e : a) if (!one(npc, p, e.getAsString())) return false;
        }
        return true;
    }

    private static boolean one(Actor npc, ServerPlayer p, String c) {
        c = c.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) return true;
        boolean neg = c.startsWith("!");
        if (neg) c = c.substring(1);
        return evalAtom(npc, p, c) != neg;
    }

    private static boolean evalAtom(Actor npc, ServerPlayer p, String c) {
        UUID id = p != null ? p.getUUID() : new UUID(0, 0);
        if (c.startsWith("g:")) return State.has(c.substring(2));
        if (c.startsWith("hora:")) {
            String h = Fmt.hora(npc.level().getDayTime());
            String w = c.substring(5);
            return switch (w) {
                case "dia" -> h.equals("mañana") || h.equals("tarde");
                case "noche" -> h.equals("noche");
                case "manana" -> h.equals("mañana");
                default -> h.equals(w);
            };
        }
        if (c.startsWith("item:") && p != null) {
            String[] sp = c.substring(5).split(":");
            String item = sp.length >= 2 ? sp[0] + ":" + sp[1] : sp[0];
            int need = sp.length >= 3 ? parse(sp[2], 1) : 1;
            var it = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(item));
            if (it == null) return false;
            int n = 0;
            for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
            return n >= need;
        }
        if (c.startsWith("tag:") && p != null) return p.getTags().contains(c.substring(4));
        if (c.equals("noche")) return Fmt.hora(npc.level().getDayTime()).equals("noche");
        if (c.equals("lluvia")) return npc.level().isRaining();
        Matcher m = CMP.matcher(c);
        if (m.matches()) {
            long v = switch (m.group(1)) {
                case "dia" -> npc.level().getDayTime() / 24000L + 1;
                case "visitas" -> npc.counter(id, "visitas");
                case "afinidad" -> npc.counter(id, "afinidad");
                default -> npc.counter(id, m.group(1));
            };
            long n = Long.parseLong(m.group(3));
            return switch (m.group(2)) {
                case ">=" -> v >= n; case "<=" -> v <= n; case ">" -> v > n; case "<" -> v < n; case "==" -> v == n; default -> v != n;
            };
        }
        return npc.hasFlag(id, c);
    }

    private static int parse(String s, int def) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; } }
}
