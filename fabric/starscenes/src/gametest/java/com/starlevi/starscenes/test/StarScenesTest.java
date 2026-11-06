package com.starlevi.starscenes.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

/**
 * Prueba visual de StarScenes: arma un modelo de Blockbench de prueba (un muñeco con piel de jugador y
 * animaciones idle1, seeplayer1 e interact1), pone el NPC con comandos, lo toca, abre su menu y saca
 * capturas de todo.
 */
public class StarScenesTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try {
            Path cfg = FabricLoader.getInstance().getConfigDir().resolve("starscenes");
            Files.createDirectories(cfg.resolve("modelos"));
            Files.createDirectories(cfg.resolve("skins"));
            Files.writeString(cfg.resolve("modelos").resolve("prueba.bbmodel"), model(), StandardCharsets.UTF_8);
            Files.write(cfg.resolve("skins").resolve("roja.png"), skin(new Color(200, 40, 50), new Color(250, 220, 190)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> mc.options.hideGui = true);
            var server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("starscenes recargar");
            // el NPC mirando al sur, el jugador al frente
            server.runCommand("tp @p 0.5 -60 0.5 180 0");
            ctx.waitTicks(5);
            server.runCommand("execute as @p at @p run starscenes poner prueba");
            server.runCommand("tp @p 0.5 -60 4.5 180 8");
            ctx.waitTicks(60);
            ctx.takeScreenshot("s01_npc_frente");
            server.runCommand("execute as @p at @p run starscenes nombre &bProbador &f(StarScenes)");
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
            server.runCommand("execute as @p at @p run starscenes item mano");
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:golden_helmet");
            server.runCommand("execute as @p at @p run starscenes item cabeza");
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
            ctx.waitTicks(20);
            ctx.takeScreenshot("s02_nombre_items");
            // de costado y de atras
            server.runCommand("tp @p 4.5 -60 0.5 90 8");
            ctx.waitTicks(10);
            ctx.takeScreenshot("s03_costado");
            server.runCommand("tp @p 0.5 -60 -3.5 0 8");
            ctx.waitTicks(10);
            ctx.takeScreenshot("s04_espalda");
            // la skin del servidor
            server.runCommand("execute as @p at @p run starscenes skin roja");
            server.runCommand("tp @p 0.5 -60 4.5 180 8");
            ctx.waitTicks(30);
            ctx.takeScreenshot("s05_skin_roja");
            // tocarlo: su animacion de interaccion
            server.runCommand("tp @p 0.5 -60 2.5 180 20");
            ctx.waitTicks(10);
            ctx.getInput().pressKey(o -> o.keyUse);
            ctx.waitTicks(12);
            ctx.takeScreenshot("s06_interaccion");
            ctx.waitTicks(10);
            ctx.takeScreenshot("s07_interaccion_2");
            // alejarse y volver: "te vio"
            server.runCommand("tp @p 0.5 -60 14.5 180 0");
            ctx.waitTicks(40);
            server.runCommand("tp @p 0.5 -60 4.0 180 8");
            ctx.waitTicks(10);
            ctx.takeScreenshot("s08_te_vio");
            // la cabeza sigue al jugador
            server.runCommand("tp @p 3.0 -60 3.0 135 8");
            ctx.waitTicks(30);
            ctx.takeScreenshot("s09_mira_al_jugador");
            // mover y escalar con comandos
            server.runCommand("execute as @p at @p run smove arriba 0.5");
            server.runCommand("execute as @p at @p run starscenes escala 1.5");
            server.runCommand("tp @p 0.5 -60 5.5 180 0");
            ctx.waitTicks(20);
            ctx.takeScreenshot("s10_movido_escala");
            // el menu (agachado + clic derecho)
            ctx.runOnClient(mc -> mc.options.hideGui = false);
            server.runCommand("execute as @p at @p run starscenes escala 1");
            server.runCommand("tp @p 0.5 -60 2.5 180 15");
            ctx.waitTicks(10);
            ctx.getInput().holdShift();
            ctx.getInput().pressKey(o -> o.keyUse);
            ctx.getInput().releaseShift();
            ctx.waitTicks(20);
            ctx.takeScreenshot("s11_menu");
            ctx.setScreen(() -> null);
        }
    }

    // ------------------------------------------------------------------ modelo de prueba

    private static byte[] skin(Color cloth, Color skinColor) throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // cabeza (0,0): piel; cara al frente (8,8)-(16,16) con ojos
        g.setColor(skinColor);
        g.fillRect(0, 0, 32, 16);
        g.setColor(new Color(90, 60, 30));
        g.fillRect(8, 0, 8, 8);
        g.fillRect(0, 8, 8, 3);
        g.fillRect(16, 8, 8, 3);
        g.fillRect(24, 8, 8, 8);
        g.setColor(Color.WHITE);
        g.fillRect(9, 12, 2, 1);
        g.fillRect(13, 12, 2, 1);
        g.setColor(new Color(30, 60, 160));
        g.fillRect(10, 12, 1, 1);
        g.fillRect(13, 12, 1, 1);
        g.setColor(new Color(150, 60, 60));
        g.fillRect(10, 14, 4, 1);
        // torso (16,16), brazos (40,16) y (32,48), piernas (0,16) y (16,48)
        g.setColor(cloth);
        g.fillRect(16, 16, 24, 16);
        g.setColor(cloth.darker());
        g.fillRect(40, 16, 16, 16);
        g.fillRect(32, 48, 16, 16);
        g.setColor(new Color(40, 40, 90));
        g.fillRect(0, 16, 16, 16);
        g.fillRect(16, 48, 16, 16);
        // una marca en el pecho para saber cual es el frente
        g.setColor(Color.YELLOW);
        g.fillRect(22, 22, 4, 4);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    /** Cubo con UV de piel de jugador (u, v = esquina de su caja en la textura; w, h, d = tamaño). */
    private static JsonObject cube(String name, double[] from, double[] to, double[] origin, int u, int v, int w, int h, int d, JsonArray elements) {
        JsonObject c = new JsonObject();
        String id = UUID.randomUUID().toString();
        c.addProperty("name", name);
        c.addProperty("type", "cube");
        c.addProperty("uuid", id);
        c.add("from", arr(from));
        c.add("to", arr(to));
        c.add("origin", arr(origin));
        JsonObject f = new JsonObject();
        f.add("north", face(u + d, v + d, u + d + w, v + d + h));
        f.add("south", face(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h));
        f.add("east", face(u, v + d, u + d, v + d + h));
        f.add("west", face(u + d + w, v + d, u + 2 * d + w, v + d + h));
        f.add("up", face(u + d + w, v + d, u + d, v));
        f.add("down", face(u + d + 2 * w, v, u + d + w, v + d));
        c.add("faces", f);
        elements.add(c);
        return c;
    }

    private static JsonObject face(double u0, double v0, double u1, double v1) {
        JsonObject o = new JsonObject();
        o.add("uv", arr(u0, v0, u1, v1));
        o.addProperty("texture", 0);
        return o;
    }

    private static JsonObject group(String name, double[] origin, JsonArray children, java.util.Map<String, String> uuids) {
        JsonObject g = new JsonObject();
        String id = UUID.randomUUID().toString();
        uuids.put(name, id);
        g.addProperty("name", name);
        g.addProperty("uuid", id);
        g.add("origin", arr(origin));
        g.add("rotation", arr(0, 0, 0));
        g.add("children", children);
        return g;
    }

    private static JsonArray ids(JsonObject... cubes) {
        JsonArray a = new JsonArray();
        for (JsonObject c : cubes) {
            a.add(c.get("uuid").getAsString());
        }
        return a;
    }

    private static void key(JsonArray list, String channel, double t, double x, double y, double z) {
        JsonObject k = new JsonObject();
        k.addProperty("channel", channel);
        k.addProperty("time", t);
        k.addProperty("interpolation", "catmullrom");
        JsonArray dp = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("x", String.valueOf(x));
        p.addProperty("y", String.valueOf(y));
        p.addProperty("z", String.valueOf(z));
        dp.add(p);
        k.add("data_points", dp);
        list.add(k);
    }

    private static String model() throws Exception {
        JsonArray elements = new JsonArray();
        java.util.Map<String, String> uuids = new java.util.HashMap<>();
        JsonObject head = cube("Head", new double[]{-4, 24, -4}, new double[]{4, 32, 4}, new double[]{0, 24, 0}, 0, 0, 8, 8, 8, elements);
        JsonObject body = cube("Body", new double[]{-4, 12, -2}, new double[]{4, 24, 2}, new double[]{0, 24, 0}, 16, 16, 8, 12, 4, elements);
        JsonObject ra = cube("RightArm", new double[]{4, 12, -2}, new double[]{8, 24, 2}, new double[]{6, 22, 0}, 40, 16, 4, 12, 4, elements);
        JsonObject la = cube("LeftArm", new double[]{-8, 12, -2}, new double[]{-4, 24, 2}, new double[]{-6, 22, 0}, 32, 48, 4, 12, 4, elements);
        JsonObject rl = cube("RightLeg", new double[]{0, 0, -2}, new double[]{4, 12, 2}, new double[]{2, 12, 0}, 0, 16, 4, 12, 4, elements);
        JsonObject ll = cube("LeftLeg", new double[]{-4, 0, -2}, new double[]{0, 12, 2}, new double[]{-2, 12, 0}, 16, 48, 4, 12, 4, elements);
        JsonObject hit = cube("hitbox", new double[]{-4, 0, -4}, new double[]{4, 32, 4}, new double[]{0, 0, 0}, 0, 0, 8, 8, 8, elements);
        JsonArray bodyKids = ids(body);
        bodyKids.add(group("head", new double[]{0, 24, 0}, ids(head), uuids));
        bodyKids.add(group("right_arm", new double[]{5, 22, 0}, ids(ra), uuids));
        bodyKids.add(group("left_arm", new double[]{-5, 22, 0}, ids(la), uuids));
        JsonArray playerKids = new JsonArray();
        playerKids.add(group("body", new double[]{0, 12, 0}, bodyKids, uuids));
        playerKids.add(group("right_leg", new double[]{2, 12, 0}, ids(rl), uuids));
        playerKids.add(group("left_leg", new double[]{-2, 12, 0}, ids(ll), uuids));
        JsonArray outliner = new JsonArray();
        outliner.add(group("hitbox", new double[]{0, 0, 0}, ids(hit), uuids));
        outliner.add(group("player", new double[]{0, 0, 0}, playerKids, uuids));
        outliner.add(group("tag_name", new double[]{0, 36, 0}, new JsonArray(), uuids));

        JsonArray anims = new JsonArray();
        // idle1: respira y balancea los brazos
        JsonObject idle = anim("idle1", 2, "loop");
        JsonObject ia = animators(idle);
        JsonArray k = bone(ia, uuids, "body");
        key(k, "rotation", 0, 0, 0, 0);
        key(k, "rotation", 1, 3, 0, 0);
        key(k, "rotation", 2, 0, 0, 0);
        k = bone(ia, uuids, "right_arm");
        key(k, "rotation", 0, 8, 0, 4);
        key(k, "rotation", 1, -8, 0, 6);
        key(k, "rotation", 2, 8, 0, 4);
        k = bone(ia, uuids, "left_arm");
        key(k, "rotation", 0, -8, 0, -4);
        key(k, "rotation", 1, 8, 0, -6);
        key(k, "rotation", 2, -8, 0, -4);
        anims.add(idle);
        // seeplayer1: saluda con el brazo derecho
        JsonObject see = anim("seeplayer1", 1.5, "once");
        k = bone(animators(see), uuids, "right_arm");
        key(k, "rotation", 0, 0, 0, 0);
        key(k, "rotation", 0.3, 0, 0, 150);
        key(k, "rotation", 0.6, 0, 0, 120);
        key(k, "rotation", 0.9, 0, 0, 150);
        key(k, "rotation", 1.2, 0, 0, 120);
        key(k, "rotation", 1.5, 0, 0, 0);
        anims.add(see);
        // interact1: reverencia, brazo adelante
        JsonObject in = anim("interact1", 2, "once");
        JsonObject inA = animators(in);
        k = bone(inA, uuids, "body");
        key(k, "rotation", 0, 0, 0, 0);
        key(k, "rotation", 0.5, 25, 0, 0);
        key(k, "rotation", 1.5, 25, 0, 0);
        key(k, "rotation", 2, 0, 0, 0);
        k = bone(inA, uuids, "right_arm");
        key(k, "rotation", 0, 0, 0, 0);
        key(k, "rotation", 0.5, -80, 0, 0);
        key(k, "rotation", 1.5, -80, 0, 0);
        key(k, "rotation", 2, 0, 0, 0);
        anims.add(in);

        JsonObject tex = new JsonObject();
        tex.addProperty("name", "npc_skin.png");
        tex.addProperty("uv_width", 64);
        tex.addProperty("uv_height", 64);
        tex.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(skin(new Color(40, 120, 200), new Color(240, 200, 170))));
        JsonArray textures = new JsonArray();
        textures.add(tex);

        JsonObject root = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", "4.10");
        meta.addProperty("model_format", "free");
        meta.addProperty("box_uv", false);
        root.add("meta", meta);
        root.addProperty("name", "prueba");
        JsonObject res = new JsonObject();
        res.addProperty("width", 64);
        res.addProperty("height", 64);
        root.add("resolution", res);
        root.add("elements", elements);
        root.add("outliner", outliner);
        root.add("textures", textures);
        root.add("animations", anims);
        return root.toString();
    }

    private static JsonObject anim(String name, double length, String loop) {
        JsonObject a = new JsonObject();
        a.addProperty("name", name);
        a.addProperty("length", length);
        a.addProperty("loop", loop);
        a.add("animators", new JsonObject());
        return a;
    }

    private static JsonObject animators(JsonObject anim) {
        return anim.getAsJsonObject("animators");
    }

    private static JsonArray bone(JsonObject animators, java.util.Map<String, String> uuids, String name) {
        JsonObject b = new JsonObject();
        b.addProperty("name", name);
        b.addProperty("type", "bone");
        JsonArray kf = new JsonArray();
        b.add("keyframes", kf);
        animators.add(uuids.get(name), b);
        return kf;
    }
}
