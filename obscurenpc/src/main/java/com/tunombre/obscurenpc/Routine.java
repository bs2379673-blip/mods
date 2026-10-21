package com.tunombre.obscurenpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Rutina / objetivos de un NPC: una lista de pasos (config/obscurenpc/rutinas/<id>.json) que el NPC ejecuta solo:
 * ir a un punto, esperar, decir algo, gestos, posturas, comandos, mirar, esperar a un jugador o a una bandera...
 * Sirve tambien como "actor" para escenas: se lanza con /onpc rutina.
 */
public final class Routine {
    private String name = "";
    private JsonArray steps = new JsonArray();
    private boolean repeat;
    private int idx;
    private int wait;
    private boolean running;
    private int timeout;
    private Vec3 look;
    private boolean moving;
    private boolean lookPlayer;
    private int moveGrace;

    public boolean active() { return running; }

    public boolean moving() { return moving; }

    public Vec3 lookPos() { return look; }

    public void setLook(Vec3 v) { look = v; lookPlayer = false; }

    private static Path dir() { return State.dir().resolve("rutinas"); }

    public static java.util.List<String> fileIds() {
        java.util.List<String> l = new java.util.ArrayList<>();
        try (var s = Files.isDirectory(dir()) ? Files.list(dir()) : java.util.stream.Stream.<Path>empty()) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(n -> l.add(n.substring(0, n.length() - 5)));
        } catch (IOException ignored) { }
        java.util.Collections.sort(l);
        return l;
    }

    public boolean start(String id) {
        try {
            Path f = dir().resolve(id.toLowerCase(Locale.ROOT) + ".json");
            if (!Files.isRegularFile(f)) return false;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            steps = o.getAsJsonArray("pasos");
            repeat = o.has("repetir") && o.get("repetir").getAsBoolean();
            name = id;
            idx = 0;
            wait = 0;
            timeout = 0;
            look = null;
            running = true;
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public void stop() { running = false; look = null; moving = false; }

    /** algo (un golpe) corta lo que estaba haciendo y la rutina se retoma mas tarde */
    public void interrupt() { if (running) { wait = Math.max(wait, 40); moving = false; } }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putInt("idx", idx);
        t.putBoolean("run", running);
        return t;
    }

    public void load(CompoundTag t) {
        if (t.getBoolean("run") && !t.getString("name").isEmpty() && start(t.getString("name"))) idx = Math.min(t.getInt("idx"), steps.size());
    }

    // ------------------------------------------------------------------ ejecucion ----
    public void tick(Actor a) {
        if (moveGrace > 0 && --moveGrace == 0) { moving = false; a.setAggressive(false); }
        if (!running) return;
        if (lookPlayer) {
            Player p = a.level().getNearestPlayer(a, 12);
            if (p != null) look = p.getEyePosition();
        }
        if (wait > 0) { wait--; return; }
        int guard = 0;
        while (running && wait <= 0 && guard++ < 50) {
            if (idx >= steps.size()) {
                if (repeat && steps.size() > 0) { idx = 0; wait = 20; }
                else { running = false; moving = false; look = null; }
                return;
            }
            JsonObject s = steps.get(idx).getAsJsonObject();
            String op = s.get("op").getAsString().toLowerCase(Locale.ROOT);
            if (!step(a, s, op)) return;      // paso en curso: se vuelve a evaluar el proximo tick
            idx++;
            timeout = 0;
        }
    }

    private static double num(JsonObject s, String k, double d) { return s.has(k) ? s.get(k).getAsDouble() : d; }

    /** @return true cuando el paso termino */
    private boolean step(Actor a, JsonObject s, String op) {
        switch (op) {
            case "ir" -> {
                double x = num(s, "x", a.getX()), y = num(s, "y", a.getY()), z = num(s, "z", a.getZ());
                double vel = num(s, "vel", 1.0);
                boolean wantWait = !s.has("espera_llegar") || s.get("espera_llegar").getAsBoolean();
                if (timeout == 0) { moving = true; a.goTo(x, y, z, vel); }
                timeout++;
                double dist = Math.sqrt(a.distanceToSqr(x, a.getY(), z));
                if (!wantWait) { moveGrace = 20 * 20; return true; }
                if (dist > 1.6 && a.getNavigation().isDone() && timeout % 40 == 0) a.goTo(x, y, z, vel);
                if (dist < 1.6 || timeout > 20 * 40) {
                    a.getNavigation().stop();
                    a.setAggressive(false);
                    moving = false;
                    return true;
                }
                return false;
            }
            case "esperar" -> { wait = (int) num(s, "ticks", 20); return true; }
            case "decir" -> {
                Player p = a.level().getNearestPlayer(a, 16);
                int t = a.speak(s.get("texto").getAsString(), p instanceof ServerPlayer sp ? sp : null, !s.has("chat") || s.get("chat").getAsBoolean());
                wait = s.has("ticks") ? (int) num(s, "ticks", t) : t + Fmt.lastPause;
                return true;
            }
            case "gesto" -> { a.gesture(s.get("id").getAsString()); if (s.has("espera")) wait = (int) num(s, "espera", 0); return true; }
            case "postura" -> { a.setStance(Stances.byName(s.get("id").getAsString())); return true; }
            case "mirar" -> {
                if (s.has("jugador") && s.get("jugador").getAsBoolean()) lookPlayer = true;
                else { lookPlayer = false; look = new Vec3(num(s, "x", a.getX()), num(s, "y", a.getEyeY()), num(s, "z", a.getZ())); }
                return true;
            }
            case "mirar_fin" -> { look = null; lookPlayer = false; return true; }
            case "cmd" -> { Fmt.runCommand(a, s.get("cmd").getAsString(), null); return true; }
            case "tp" -> { a.teleportTo(num(s, "x", a.getX()), num(s, "y", a.getY()), num(s, "z", a.getZ())); return true; }
            case "dialogo" -> {
                Player p = a.level().getNearestPlayer(a, num(s, "radio", 8));
                if (p instanceof ServerPlayer sp) Dialogs.start(a, sp, s.has("id") ? s.get("id").getAsString() : "");
                return true;
            }
            case "esperar_jugador" -> {
                Player p = a.level().getNearestPlayer(a, num(s, "radio", 6));
                return p != null && !p.isSpectator();
            }
            case "esperar_bandera" -> { return State.has(s.get("bandera").getAsString().replace("g:", "")); }
            case "bandera" -> {
                if (s.has("pone")) State.set(s.get("pone").getAsString());
                if (s.has("quita")) State.clear(s.get("quita").getAsString());
                return true;
            }
            case "equipar" -> { equip(a, s); return true; }
            case "atacar" -> {
                Player p = a.level().getNearestPlayer(a, num(s, "radio", 24));
                if (p != null && !p.isCreative() && !p.isSpectator()) a.setTarget(p);
                return true;
            }
            case "saltar_a" -> { idx = (int) num(s, "paso", 0) - 1; return true; }
            case "fin" -> { running = false; moving = false; return true; }
            default -> { return true; }
        }
    }

    private static void equip(Actor a, JsonObject s) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(s.get("item").getAsString()));
        if (item == null) return;
        ItemStack st = new ItemStack(item);
        if (s.has("nbt")) {
            try { st.setTag(TagParser.parseTag(s.get("nbt").getAsString())); } catch (Exception ignored) { }
        }
        String slot = s.has("slot") ? s.get("slot").getAsString() : "mano";
        EquipmentSlot es = switch (slot) {
            case "mano2" -> EquipmentSlot.OFFHAND;
            case "cabeza" -> EquipmentSlot.HEAD;
            case "pecho" -> EquipmentSlot.CHEST;
            case "piernas" -> EquipmentSlot.LEGS;
            case "pies" -> EquipmentSlot.FEET;
            default -> EquipmentSlot.MAINHAND;
        };
        a.setItemSlot(es, st);
    }
}
