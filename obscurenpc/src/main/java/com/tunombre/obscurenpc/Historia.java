package com.tunombre.obscurenpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Paquete de NPCs de la historia de Obscure: contextos, dialogos y fichas listas para colocar (/onpc historia). */
public final class Historia {
    private Historia() { }

    public static JsonArray pack() {
        try (var in = Historia.class.getResourceAsStream("/defaults/historia/npcs.json")) {
            if (in == null) return new JsonArray();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (IOException | RuntimeException e) {
            return new JsonArray();
        }
    }

    public static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (JsonElement e : pack()) l.add(e.getAsJsonObject().getAsJsonObject("data").get("id").getAsString());
        return l;
    }

    private static boolean copy(String res, Path dst, boolean overwrite) {
        try {
            if (Files.exists(dst) && !overwrite) return false;
            try (var in = Historia.class.getResourceAsStream(res)) {
                if (in == null) return false;
                Files.createDirectories(dst.getParent());
                Files.write(dst, in.readAllBytes());
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** copia contextos, dialogos y el conocimiento compartido a config/obscurenpc; devuelve cuantos archivos escribio */
    public static int install(boolean overwrite) {
        Path base = State.dir();
        int n = 0;
        if (copy("/defaults/contexto/_mundo_historia.txt", base.resolve("contexto/_mundo.txt"), overwrite)) n++;
        for (String id : ids()) {
            if (copy("/defaults/contexto/" + id + ".txt", base.resolve("contexto/" + id + ".txt"), overwrite)) n++;
            if (copy("/defaults/dialogos/" + id + ".json", base.resolve("dialogos/" + id + ".json"), overwrite)) n++;
        }
        Dialogs.reload();
        return n;
    }

    /** coloca un NPC (o todos en circulo) alrededor de una posicion; devuelve cuantos creo */
    public static int spawn(ServerLevel lvl, Vec3 pos, String which) {
        JsonArray p = pack();
        List<JsonObject> sel = new ArrayList<>();
        for (JsonElement e : p) {
            JsonObject o = e.getAsJsonObject();
            String id = o.getAsJsonObject("data").get("id").getAsString();
            if (which == null || which.equals("todos") || which.equalsIgnoreCase(id)) sel.add(o);
        }
        int made = 0;
        for (int i = 0; i < sel.size(); i++) {
            JsonObject o = sel.get(i);
            NpcData d = NpcData.fromJson(o.getAsJsonObject("data").toString());
            double ang = sel.size() == 1 ? 0 : (Math.PI * 2 * i) / sel.size();
            double r = sel.size() == 1 ? 0 : 3.0 + sel.size() * 0.25;
            double x = pos.x + Math.cos(ang) * r, z = pos.z + Math.sin(ang) * r;
            Actor a = ObscureNpc.create(lvl, o.get("motor").getAsString(), d);
            if (a == null) continue;
            a.moveTo(x, pos.y, z, (float) Math.toDegrees(ang) + 90F, 0);
            a.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(a.blockPosition()), MobSpawnType.COMMAND, null, null);
            lvl.addFreshEntity(a);
            made++;
        }
        return made;
    }

    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : pack()) {
            JsonObject d = e.getAsJsonObject().getAsJsonObject("data");
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.get("id").getAsString()).append(" (").append(d.get("nombre").getAsString()).append(")");
        }
        return sb.toString();
    }
}
